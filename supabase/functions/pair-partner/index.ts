import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

type RelType = "couples" | "parent_child";
type MemberRole = "partner" | "parent" | "child";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const body = await req.json();
    const code = String(body.partner_code ?? "").trim().toUpperCase();
    const relationshipType = (body.relationship_type ?? "couples") as RelType;
    const myRole = (body.my_role ?? (relationshipType === "couples" ? "partner" : null)) as MemberRole | null;

    if (!/^[A-Z2-9]{6}$/.test(code)) {
      return jsonResponse({ ok: false, message: "Invalid pair code" }, 400);
    }
    if (relationshipType !== "couples" && relationshipType !== "parent_child") {
      return jsonResponse({ ok: false, message: "Invalid relationship_type" }, 400);
    }

    const admin = serviceClient();
    const { data: me, error: meErr } = await admin
      .from("profiles")
      .select("*")
      .eq("id", user.id)
      .single();
    if (meErr || !me) return jsonResponse({ ok: false, message: "Profile not found" }, 404);
    if (!me.age_attested_at || !me.date_of_birth) {
      return jsonResponse({ ok: false, message: "Please confirm your date of birth first" }, 400);
    }
    if (me.pair_code === code) {
      return jsonResponse({ ok: false, message: "You cannot pair with your own code" }, 400);
    }

    const { data: partner, error: partnerErr } = await admin
      .from("profiles")
      .select("*")
      .eq("pair_code", code)
      .maybeSingle();
    if (partnerErr || !partner) {
      return jsonResponse({ ok: false, message: "Partner code not found" }, 404);
    }
    if (!partner.age_attested_at) {
      return jsonResponse({
        ok: false,
        message: "That person has not finished age verification yet",
      }, 400);
    }

    // Reject if these two already share a relationship
    const { data: existingPairs } = await admin
      .from("relationships")
      .select("id")
      .or(
        `and(partner1_id.eq.${me.id},partner2_id.eq.${partner.id}),and(partner1_id.eq.${partner.id},partner2_id.eq.${me.id})`,
      )
      .limit(1);
    if (existingPairs && existingPairs.length > 0) {
      return jsonResponse({ ok: false, message: "You are already connected with this person" }, 400);
    }

    let partner1Role: MemberRole = "partner";
    let partner2Role: MemberRole = "partner";

    if (relationshipType === "couples") {
      if (me.is_minor || partner.is_minor) {
        return jsonResponse({
          ok: false,
          message: "Couples connections require both people to be 18+",
        }, 400);
      }
      partner1Role = "partner";
      partner2Role = "partner";
    } else {
      if (myRole !== "parent" && myRole !== "child") {
        return jsonResponse({
          ok: false,
          message: "For parent–child, set my_role to parent or child",
        }, 400);
      }
      const theirRole: MemberRole = myRole === "parent" ? "child" : "parent";
      // Inviter (code owner) is partner1; joiner is partner2
      partner1Role = theirRole;
      partner2Role = myRole;

      if (partner1Role === partner2Role) {
        return jsonResponse({ ok: false, message: "Roles must be parent and child" }, 400);
      }

      // Parent must be adult
      const parentIsJoiner = myRole === "parent";
      const parentProfile = parentIsJoiner ? me : partner;
      const childProfile = parentIsJoiner ? partner : me;
      if (parentProfile.is_minor) {
        return jsonResponse({ ok: false, message: "The parent role must be 18+" }, 400);
      }
      void childProfile;
    }

    const { data: relationship, error: relErr } = await admin
      .from("relationships")
      .insert({
        partner1_id: partner.id,
        partner2_id: me.id,
        relationship_type: relationshipType,
        partner1_role: partner1Role,
        partner2_role: partner2Role,
      })
      .select("*")
      .single();
    if (relErr || !relationship) {
      return jsonResponse({ ok: false, message: relErr?.message ?? "Could not create relationship" }, 500);
    }

    // Set active relationship for both if unset
    for (const uid of [me.id, partner.id]) {
      const { data: prof } = await admin.from("profiles").select("active_relationship_id").eq("id", uid).single();
      if (!prof?.active_relationship_id) {
        await admin.from("profiles").update({ active_relationship_id: relationship.id }).eq("id", uid);
      }
    }

    const typeLabel = relationshipType === "couples" ? "couples" : "parent–child";
    await admin.from("notifications").insert([
      {
        user_id: me.id,
        type: "partner_paired",
        title: "You're connected",
        body: `You are now connected with ${partner.display_name ?? "them"} (${typeLabel}).`,
        payload: { relationship_id: relationship.id, relationship_type: relationshipType },
      },
      {
        user_id: partner.id,
        type: "partner_paired",
        title: "You're connected",
        body: `${me.display_name ?? "Someone"} connected with your pair code (${typeLabel}).`,
        payload: { relationship_id: relationship.id, relationship_type: relationshipType },
      },
    ]);

    const needsConsent = relationshipType === "parent_child" &&
      (me.is_minor || partner.is_minor);

    return jsonResponse({
      ok: true,
      relationship_id: relationship.id,
      message: needsConsent
        ? "Connected. Parent must grant consent before sessions."
        : "Paired successfully",
      needs_parental_consent: needsConsent,
      data: relationship,
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
