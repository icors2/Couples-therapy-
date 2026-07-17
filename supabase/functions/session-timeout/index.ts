import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { serviceClient } from "../_shared/supabase.ts";

/**
 * Scheduled job: end sessions idle for 10+ minutes, cancel pending invites after 30m,
 * then generate memory handoffs for newly expired active sessions.
 * Protect with a shared CRON_SECRET header when verify_jwt is false.
 */
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const cronSecret = Deno.env.get("CRON_SECRET");
    if (cronSecret) {
      const provided = req.headers.get("x-cron-secret");
      if (provided !== cronSecret) {
        return jsonResponse({ ok: false, message: "Unauthorized" }, 401);
      }
    }

    const admin = serviceClient();
    const { data: expiredIds, error } = await admin.rpc("expire_inactive_sessions", {
      timeout_minutes: 10,
      pending_timeout_minutes: 30,
    });
    if (error) return jsonResponse({ ok: false, message: error.message }, 500);

    // Find recently expired sessions without a fresh memory version after end
    const since = new Date(Date.now() - 15 * 60 * 1000).toISOString();
    const { data: sessions } = await admin
      .from("sessions")
      .select("id")
      .eq("status", "expired")
      .gte("ended_at", since);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const results = [];
    for (const s of sessions ?? []) {
      const res = await fetch(`${supabaseUrl}/functions/v1/generate-memory`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${serviceKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ session_id: s.id }),
      });
      results.push({ session_id: s.id, ok: res.ok });
    }

    return jsonResponse({
      ok: true,
      expired: expiredIds,
      memory_jobs: results,
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 500);
  }
});
