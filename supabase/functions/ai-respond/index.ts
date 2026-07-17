import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import {
  chatCompletion,
  shouldAiRespond,
  therapistSystemPrompt,
} from "../_shared/openai.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const { session_id, force } = await req.json();
    const admin = serviceClient();

    const { data: session } = await admin.from("sessions").select("*").eq("id", session_id).single();
    if (!session) return jsonResponse({ ok: false, message: "Session not found" }, 404);
    if (!["pending", "active"].includes(session.status)) {
      return jsonResponse({ ok: false, message: "Session is not active" }, 400);
    }

    const { data: relationship } = await admin
      .from("relationships")
      .select("*")
      .eq("id", session.relationship_id)
      .single();
    if (!relationship || (relationship.partner1_id !== user.id && relationship.partner2_id !== user.id)) {
      return jsonResponse({ ok: false, message: "Forbidden" }, 403);
    }

    // Activate on first message if still pending
    if (session.status === "pending") {
      await admin.from("sessions").update({ status: "active" }).eq("id", session_id);
    }

    const { data: messages } = await admin
      .from("messages")
      .select("*")
      .eq("session_id", session_id)
      .order("created_at", { ascending: true })
      .limit(80);

    const transcript = messages ?? [];
    const last = transcript[transcript.length - 1];
    if (!last) return jsonResponse({ ok: true, message: "No messages yet" });

    let streak = 0;
    for (let i = transcript.length - 1; i >= 0; i--) {
      const s = transcript[i].sender;
      if (s === "partner_a" || s === "partner_b") streak++;
      else break;
    }

    if (!force && !shouldAiRespond(streak, last.sender)) {
      return jsonResponse({ ok: true, message: "AI waiting for more context" });
    }

    const { data: memoryRow } = await admin
      .from("ai_memory")
      .select("*")
      .eq("relationship_id", session.relationship_id)
      .order("version", { ascending: false })
      .limit(1)
      .maybeSingle();

    const { count } = await admin
      .from("sessions")
      .select("*", { count: "exact", head: true })
      .eq("relationship_id", session.relationship_id)
      .eq("status", "ended");

    const system = therapistSystemPrompt(memoryRow?.memory_json ?? null, (count ?? 0) === 0);

    const chatMessages = [
      { role: "system" as const, content: system },
      ...transcript
        .filter((m) => m.sender !== "system")
        .slice(-30)
        .map((m) => ({
          role: (m.sender === "ai" ? "assistant" : "user") as "assistant" | "user",
          content: m.sender === "ai"
            ? m.content
            : `[${m.sender === "partner_a" ? "Partner A" : "Partner B"}]: ${m.content}`,
        })),
    ];

    const completion = await chatCompletion(chatMessages, { temperature: 0.65 });

    const { data: aiMessage, error } = await admin
      .from("messages")
      .insert({
        session_id,
        sender: "ai",
        content: completion.content.trim(),
        token_count: completion.tokens ?? null,
        model: completion.model,
      })
      .select("*")
      .single();

    if (error) return jsonResponse({ ok: false, message: error.message }, 500);

    return jsonResponse({ ok: true, session_id, data: aiMessage });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 500);
  }
});
