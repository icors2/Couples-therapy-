import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const { session_id } = await req.json();
    const admin = serviceClient();

    const { data: session } = await admin.from("sessions").select("*").eq("id", session_id).single();
    if (!session) return jsonResponse({ ok: false, message: "Session not found" }, 404);

    const { data: relationship } = await admin
      .from("relationships")
      .select("*")
      .eq("id", session.relationship_id)
      .single();
    if (!relationship || (relationship.partner1_id !== user.id && relationship.partner2_id !== user.id)) {
      return jsonResponse({ ok: false, message: "Forbidden" }, 403);
    }

    const isPartner1 = relationship.partner1_id === user.id;
    const { data: updated, error } = await admin
      .from("sessions")
      .update({
        status: "active",
        partner_a_joined: isPartner1 ? true : session.partner_a_joined,
        partner_b_joined: !isPartner1 ? true : session.partner_b_joined,
      })
      .eq("id", session_id)
      .select("*")
      .single();
    if (error) return jsonResponse({ ok: false, message: error.message }, 500);

    const { data: profile } = await admin.from("profiles").select("display_name").eq("id", user.id).single();
    await admin.from("messages").insert({
      session_id,
      sender: "system",
      content: `${profile?.display_name ?? "Partner"} joined the session.`,
    });

    const otherId = isPartner1 ? relationship.partner2_id : relationship.partner1_id;
    if (otherId) {
      await admin.from("notifications").insert({
        user_id: otherId,
        type: "partner_joined",
        title: "Partner joined",
        body: `${profile?.display_name ?? "Your partner"} joined the therapy session.`,
        payload: { session_id },
      });
    }

    return jsonResponse({ ok: true, session_id: updated.id });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
