import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

export const CONSENT_VERSION = "v1";
export const CONSENT_TEXT =
  "I confirm I am the parent or legal guardian of this minor. " +
  "I consent to their use of this AI communication facilitator. " +
  "I understand it is not licensed therapy or emergency care.";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const { relationship_id, accept } = await req.json();
    if (!relationship_id || !accept) {
      return jsonResponse({ ok: false, message: "Must accept consent for a relationship" }, 400);
    }

    const admin = serviceClient();
    const { data: relationship } = await admin
      .from("relationships")
      .select("*")
      .eq("id", relationship_id)
      .single();
    if (!relationship) return jsonResponse({ ok: false, message: "Relationship not found" }, 404);
    if (relationship.relationship_type !== "parent_child") {
      return jsonResponse({ ok: false, message: "Consent only applies to parent–child connections" }, 400);
    }

    const isP1 = relationship.partner1_id === user.id;
    const isP2 = relationship.partner2_id === user.id;
    if (!isP1 && !isP2) return jsonResponse({ ok: false, message: "Forbidden" }, 403);

    const myRole = isP1 ? relationship.partner1_role : relationship.partner2_role;
    if (myRole !== "parent") {
      return jsonResponse({ ok: false, message: "Only the parent role can grant consent" }, 403);
    }

    const minorId = isP1 ? relationship.partner2_id : relationship.partner1_id;
    if (!minorId) return jsonResponse({ ok: false, message: "Child member missing" }, 400);

    const { data: minor } = await admin.from("profiles").select("*").eq("id", minorId).single();
    if (!minor?.is_minor) {
      return jsonResponse({
        ok: true,
        message: "No parental consent required (child is not a minor)",
        relationship_id,
      });
    }

    const { data: existing } = await admin
      .from("parental_consents")
      .select("*")
      .eq("relationship_id", relationship_id)
      .maybeSingle();
    if (existing) {
      return jsonResponse({ ok: true, message: "Consent already recorded", data: existing });
    }

    const { data: consent, error } = await admin
      .from("parental_consents")
      .insert({
        relationship_id,
        guardian_id: user.id,
        minor_id: minorId,
        consent_version: CONSENT_VERSION,
      })
      .select("*")
      .single();
    if (error) return jsonResponse({ ok: false, message: error.message }, 500);

    const { data: me } = await admin.from("profiles").select("display_name").eq("id", user.id).single();
    await admin.from("notifications").insert({
      user_id: minorId,
      type: "parental_consent_granted",
      title: "Parent consent granted",
      body: `${me?.display_name ?? "Your parent/guardian"} approved your use of therapy sessions.`,
      payload: { relationship_id },
    });

    return jsonResponse({
      ok: true,
      message: "Parental consent recorded",
      relationship_id,
      data: consent,
      consent_text: CONSENT_TEXT,
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
