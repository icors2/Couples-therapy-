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

    if (["ended", "expired", "declined"].includes(session.status)) {
      return jsonResponse({ ok: true, session_id, message: "Already closed" });
    }

    const endedAt = new Date();
    const startedAt = new Date(session.started_at);
    const durationSeconds = Math.max(0, Math.floor((endedAt.getTime() - startedAt.getTime()) / 1000));

    await admin
      .from("sessions")
      .update({
        status: "ended",
        ended_at: endedAt.toISOString(),
        ended_by: user.id,
        duration_seconds: durationSeconds,
      })
      .eq("id", session_id);

    await admin.from("messages").insert({
      session_id,
      sender: "system",
      content: "Session ended. Generating therapeutic memory handoff…",
    });

    // Generate memory via internal function invoke
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const memoryRes = await fetch(`${supabaseUrl}/functions/v1/generate-memory`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${serviceKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ session_id }),
    });
    const memoryJson = await memoryRes.json().catch(() => ({}));

    const partnerIds = [relationship.partner1_id, relationship.partner2_id].filter(Boolean);
    await admin.from("notifications").insert(
      partnerIds.map((uid) => ({
        user_id: uid,
        type: "session_ended",
        title: "Session ended",
        body: "Your therapy session has ended. Memory handoff saved.",
        payload: { session_id },
      })),
    );

    await admin.from("messages").insert({
      session_id,
      sender: "system",
      content: memoryRes.ok
        ? "Therapeutic memory updated. This session is now locked."
        : `Session locked. Memory update warning: ${memoryJson.message ?? "unknown error"}`,
    });

    return jsonResponse({
      ok: true,
      session_id,
      message: "Session ended",
      data: memoryJson,
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 500);
  }
});
