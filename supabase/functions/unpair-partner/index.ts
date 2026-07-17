import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const body = await req.json().catch(() => ({}));
    const relationshipId = body.relationship_id as string | undefined;
    const admin = serviceClient();

    const { data: me, error: meErr } = await admin
      .from("profiles")
      .select("*")
      .eq("id", user.id)
      .single();
    if (meErr || !me) return jsonResponse({ ok: false, message: "Profile not found" }, 404);

    let relationship;
    if (relationshipId) {
      const { data, error } = await admin
        .from("relationships")
        .select("*")
        .eq("id", relationshipId)
        .single();
      if (error || !data) return jsonResponse({ ok: false, message: "Relationship not found" }, 404);
      relationship = data;
    } else if (me.active_relationship_id) {
      const { data, error } = await admin
        .from("relationships")
        .select("*")
        .eq("id", me.active_relationship_id)
        .single();
      if (error || !data) return jsonResponse({ ok: false, message: "Relationship not found" }, 404);
      relationship = data;
    } else {
      return jsonResponse({ ok: false, message: "relationship_id is required" }, 400);
    }

    if (relationship.partner1_id !== user.id && relationship.partner2_id !== user.id) {
      return jsonResponse({ ok: false, message: "Forbidden" }, 403);
    }

    const partnerId = relationship.partner1_id === user.id
      ? relationship.partner2_id
      : relationship.partner1_id;

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
        title: "Connection ended",
        body: `${me.display_name ?? "Someone"} removed this connection. Shared sessions and AI memory were removed.`,
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

    // Clear active pointer if it pointed here; pick another membership if any
    for (const uid of [user.id, partnerId].filter(Boolean) as string[]) {
      const { data: remaining } = await admin
        .from("relationships")
        .select("id")
        .or(`partner1_id.eq.${uid},partner2_id.eq.${uid}`)
        .limit(1)
        .maybeSingle();
      await admin
        .from("profiles")
        .update({ active_relationship_id: remaining?.id ?? null })
        .eq("id", uid)
        .eq("active_relationship_id", relationship.id);
      // Also fix if active was this id (eq filter may miss if already null via FK)
      const { data: prof } = await admin.from("profiles").select("active_relationship_id").eq("id", uid).single();
      if (prof?.active_relationship_id === relationship.id || !prof?.active_relationship_id) {
        await admin
          .from("profiles")
          .update({ active_relationship_id: remaining?.id ?? null })
          .eq("id", uid);
      }
    }

    return jsonResponse({
      ok: true,
      message: "Unpaired successfully",
      relationship_id: relationship.id,
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
