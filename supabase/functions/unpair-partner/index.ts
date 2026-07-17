import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const admin = serviceClient();

    const { data: me, error: meErr } = await admin
      .from("profiles")
      .select("*")
      .eq("id", user.id)
      .single();
    if (meErr || !me) return jsonResponse({ ok: false, message: "Profile not found" }, 404);
    if (!me.relationship_id) {
      return jsonResponse({ ok: false, message: "You are not paired" }, 400);
    }

    const { data: relationship, error: relErr } = await admin
      .from("relationships")
      .select("*")
      .eq("id", me.relationship_id)
      .single();
    if (relErr || !relationship) {
      return jsonResponse({ ok: false, message: "Relationship not found" }, 404);
    }

    const partnerId = relationship.partner1_id === user.id
      ? relationship.partner2_id
      : relationship.partner1_id;

    // Close open sessions before cascade delete (no memory handoff — relationship data is removed).
    const now = new Date().toISOString();
    await admin
      .from("sessions")
      .update({ status: "ended", ended_at: now, ended_by: user.id })
      .eq("relationship_id", relationship.id)
      .eq("status", "active");
    await admin
      .from("sessions")
      .update({ status: "declined", ended_at: now, ended_by: user.id })
      .eq("relationship_id", relationship.id)
      .eq("status", "pending");

    if (partnerId) {
      await admin.from("notifications").insert({
        user_id: partnerId,
        type: "partner_unpaired",
        title: "Partnership ended",
        body: `${me.display_name ?? "Your partner"} unpaired. Shared sessions and AI memory were removed.`,
        payload: { relationship_id: relationship.id },
      });
    }

    const { error: deleteErr } = await admin
      .from("relationships")
      .delete()
      .eq("id", relationship.id);
    if (deleteErr) {
      return jsonResponse({ ok: false, message: deleteErr.message }, 500);
    }

    // Regenerate pair codes so old codes cannot be reused after unpair.
    for (const uid of [user.id, partnerId].filter(Boolean) as string[]) {
      const { data: codeRow, error: codeErr } = await admin.rpc("generate_pair_code");
      if (codeErr || !codeRow) {
        return jsonResponse({
          ok: false,
          message: codeErr?.message ?? "Could not regenerate pair code",
        }, 500);
      }
      const { error: profileErr } = await admin
        .from("profiles")
        .update({ pair_code: codeRow as string })
        .eq("id", uid);
      if (profileErr) {
        return jsonResponse({ ok: false, message: profileErr.message }, 500);
      }
    }

    return jsonResponse({
      ok: true,
      message: "Unpaired successfully",
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
