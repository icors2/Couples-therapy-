import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const { relationship_id } = await req.json();
    if (!relationship_id) {
      return jsonResponse({ ok: false, message: "relationship_id is required" }, 400);
    }

    const admin = serviceClient();
    const { data: relationship } = await admin
      .from("relationships")
      .select("partner1_id, partner2_id")
      .eq("id", relationship_id)
      .single();
    if (!relationship) return jsonResponse({ ok: false, message: "Not found" }, 404);
    if (relationship.partner1_id !== user.id && relationship.partner2_id !== user.id) {
      return jsonResponse({ ok: false, message: "Forbidden" }, 403);
    }

    const { count: endedCount } = await admin
      .from("sessions")
      .select("*", { count: "exact", head: true })
      .eq("relationship_id", relationship_id)
      .eq("status", "ended");

    const { data: intakes } = await admin
      .from("relationship_intakes")
      .select("user_id")
      .eq("relationship_id", relationship_id);

    const userIds = new Set((intakes ?? []).map((r) => r.user_id as string));
    const meDone = userIds.has(user.id);
    const partnerDone = [...userIds].some((id) => id !== user.id);

    return jsonResponse({
      ok: true,
      relationship_id,
      me_done: meDone,
      partner_done: partnerDone,
      required: (endedCount ?? 0) === 0,
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
