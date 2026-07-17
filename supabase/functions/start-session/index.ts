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
    if (!profile?.relationship_id || profile.relationship_id !== relationship_id) {
      return jsonResponse({ ok: false, message: "Not a member of this relationship" }, 403);
    }

    const { data: relationship } = await admin
      .from("relationships")
      .select("*")
      .eq("id", relationship_id)
      .single();
    if (!relationship?.partner2_id) {
      return jsonResponse({ ok: false, message: "Partner not paired yet" }, 400);
    }

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
      content: `${profile.display_name ?? "Your partner"} started a therapy session.`,
    });

    await admin.from("notifications").insert({
      user_id: partnerId,
      type: "session_invite",
      title: "Therapy session invite",
      body: `${profile.display_name ?? "Your partner"} wants to begin a therapy session.`,
      payload: { session_id: session.id },
    });

    return jsonResponse({ ok: true, session_id: session.id });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
