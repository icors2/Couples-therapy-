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

    await admin
      .from("sessions")
      .update({
        status: "declined",
        ended_at: new Date().toISOString(),
        ended_by: user.id,
      })
      .eq("id", session_id);

    await admin.from("messages").insert({
      session_id,
      sender: "system",
      content: "The session invite was declined.",
    });

    if (session.started_by !== user.id) {
      await admin.from("notifications").insert({
        user_id: session.started_by,
        type: "session_ended",
        title: "Session declined",
        body: "Your partner declined the therapy session.",
        payload: { session_id },
      });
    }

    return jsonResponse({ ok: true, session_id });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
