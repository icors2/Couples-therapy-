import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const { relationship_id } = await req.json();
    const admin = serviceClient();

    const { data: profile } = await admin.from("profiles").select("*").eq("id", user.id).single();
    if (!profile) return jsonResponse({ ok: false, message: "Profile not found" }, 404);
    if (!profile.age_attested_at) {
      return jsonResponse({ ok: false, message: "Please confirm your date of birth first" }, 400);
    }

    const { data: relationship } = await admin
      .from("relationships")
      .select("*")
      .eq("id", relationship_id)
      .single();
    if (!relationship?.partner2_id) {
      return jsonResponse({ ok: false, message: "Partner not paired yet" }, 400);
    }
    if (relationship.partner1_id !== user.id && relationship.partner2_id !== user.id) {
      return jsonResponse({ ok: false, message: "Not a member of this relationship" }, 403);
    }

    const { data: p1 } = await admin.from("profiles").select("*").eq("id", relationship.partner1_id).single();
    const { data: p2 } = await admin.from("profiles").select("*").eq("id", relationship.partner2_id).single();

    if (relationship.relationship_type === "couples") {
      if (p1?.is_minor || p2?.is_minor) {
        return jsonResponse({
          ok: false,
          message: "Couples sessions require both people to be 18+",
        }, 403);
      }
    }

    const hasMinor = Boolean(p1?.is_minor || p2?.is_minor);
    if (hasMinor) {
      const { data: consent } = await admin
        .from("parental_consents")
        .select("id")
        .eq("relationship_id", relationship_id)
        .maybeSingle();
      if (!consent) {
        return jsonResponse({
          ok: false,
          message: "Parent/guardian consent is required before starting a session",
        }, 403);
      }
    }

    if (profile.is_minor) {
      const { data: anyConsent } = await admin
        .from("parental_consents")
        .select("id")
        .eq("relationship_id", relationship_id)
        .eq("minor_id", user.id)
        .maybeSingle();
      if (!anyConsent) {
        return jsonResponse({
          ok: false,
          message: "Waiting for parent/guardian consent",
        }, 403);
      }
    }

    await admin
      .from("profiles")
      .update({ active_relationship_id: relationship_id })
      .eq("id", user.id);

    const { data: existing } = await admin
      .from("sessions")
      .select("*")
      .eq("relationship_id", relationship_id)
      .in("status", ["pending", "active"])
      .order("started_at", { ascending: false })
      .limit(1)
      .maybeSingle();
    if (existing) {
      return jsonResponse({
        ok: true,
        session_id: existing.id,
        message: "Existing session reused",
      });
    }

    const isPartner1 = relationship.partner1_id === user.id;
    const { data: session, error } = await admin
      .from("sessions")
      .insert({
        relationship_id,
        started_by: user.id,
        status: "pending",
        partner_a_joined: isPartner1,
        partner_b_joined: !isPartner1,
        last_user_message_at: new Date().toISOString(),
      })
      .select("*")
      .single();
    if (error || !session) {
      return jsonResponse({ ok: false, message: error?.message ?? "Could not start session" }, 500);
    }

    const partnerId = isPartner1 ? relationship.partner2_id : relationship.partner1_id;
    await admin.from("messages").insert({
      session_id: session.id,
      sender: "system",
      content: "Session started. Waiting for the other person to join.",
    });

    if (partnerId) {
      await admin.from("notifications").insert({
        user_id: partnerId,
        type: "session_invite",
        title: "Therapy session invite",
        body: `${profile.display_name ?? "Someone"} started a therapy session.`,
        payload: { session_id: session.id, relationship_id },
      });
    }

    return jsonResponse({
      ok: true,
      session_id: session.id,
      message: "Session started",
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 500);
  }
});
