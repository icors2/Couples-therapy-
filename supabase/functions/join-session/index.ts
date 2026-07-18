import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

async function maybeOpenFirstSession(
  req: Request,
  sessionId: string,
  relationshipId: string,
  bothJoined: boolean,
): Promise<void> {
  if (!bothJoined) return;

  const admin = serviceClient();
  const { count: endedCount } = await admin
    .from("sessions")
    .select("*", { count: "exact", head: true })
    .eq("relationship_id", relationshipId)
    .eq("status", "ended");
  if ((endedCount ?? 0) !== 0) return;

  const { data: aiMsgs } = await admin
    .from("messages")
    .select("id")
    .eq("session_id", sessionId)
    .eq("sender", "ai")
    .limit(1);
  if ((aiMsgs ?? []).length > 0) return;

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return;

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  if (!supabaseUrl) return;

  try {
    await fetch(`${supabaseUrl}/functions/v1/ai-respond`, {
      method: "POST",
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ session_id: sessionId, force: true }),
    });
  } catch {
    // Opening message is best-effort; partners can still chat.
  }
}

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
    const partnerAJoined = isPartner1 ? true : session.partner_a_joined;
    const partnerBJoined = !isPartner1 ? true : session.partner_b_joined;

    const { data: updated, error } = await admin
      .from("sessions")
      .update({
        status: "active",
        partner_a_joined: partnerAJoined,
        partner_b_joined: partnerBJoined,
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

    await maybeOpenFirstSession(
      req,
      session_id,
      session.relationship_id,
      Boolean(partnerAJoined && partnerBJoined),
    );

    return jsonResponse({ ok: true, session_id: updated.id });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
