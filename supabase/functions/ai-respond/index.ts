import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import {
  chatCompletion,
  shouldAiRespond,
  therapistSystemPrompt,
} from "../_shared/openai.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

const RECENT_RAW_LIMIT = 24;
const SUMMARY_TRIGGER = 30;
const SUMMARY_EVERY_N = 8;
const DEBOUNCE_MS = 3000;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const body = await req.json();
    const session_id = body.session_id as string;
    const force = Boolean(body.force);
    const trigger_message_id = body.trigger_message_id as string | undefined;
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

    const { data: p1 } = await admin.from("profiles").select("is_minor").eq("id", relationship.partner1_id).single();
    const { data: p2 } = relationship.partner2_id
      ? await admin.from("profiles").select("is_minor").eq("id", relationship.partner2_id).single()
      : { data: null };
    const promptCtx = {
      relationshipType: relationship.relationship_type as string,
      partner1Role: relationship.partner1_role as string,
      partner2Role: relationship.partner2_role as string,
      includesMinor: Boolean(p1?.is_minor || p2?.is_minor),
    };

    if (session.status === "pending") {
      await admin.from("sessions").update({ status: "active" }).eq("id", session_id);
    }

    const { data: messages } = await admin
      .from("messages")
      .select("*")
      .eq("session_id", session_id)
      .order("created_at", { ascending: true })
      .limit(120);

    const transcript = messages ?? [];
    const last = transcript[transcript.length - 1];
    if (!last) return jsonResponse({ ok: true, message: "No messages yet" });

    // Idempotency: if this trigger already produced an AI reply, skip OpenAI.
    if (trigger_message_id) {
      const trigger = transcript.find((m) => m.id === trigger_message_id);
      if (trigger?.created_at) {
        const alreadyReplied = transcript.some(
          (m) =>
            m.sender === "ai" &&
            m.created_at &&
            m.created_at > trigger.created_at,
        );
        if (alreadyReplied) {
          return jsonResponse({ ok: true, message: "Already responded to trigger" });
        }
      }
    }

    // Short per-session debounce to avoid double replies from rapid invokes.
    const lastAi = [...transcript].reverse().find((m) => m.sender === "ai");
    if (lastAi?.created_at) {
      const ageMs = Date.now() - new Date(lastAi.created_at).getTime();
      if (ageMs >= 0 && ageMs < DEBOUNCE_MS) {
        return jsonResponse({ ok: true, message: "Debounced — recent AI reply" });
      }
    }

    let streak = 0;
    for (let i = transcript.length - 1; i >= 0; i--) {
      const s = transcript[i].sender;
      if (s === "partner_a" || s === "partner_b") streak++;
      else break;
    }

    if (!force && !shouldAiRespond(streak, last.sender, last.content ?? "")) {
      return jsonResponse({ ok: true, message: "AI waiting for more context" });
    }

    const { data: memoryRow } = await admin
      .from("ai_memory")
      .select("*")
      .eq("relationship_id", session.relationship_id)
      .order("version", { ascending: false })
      .limit(1)
      .maybeSingle();

    const { data: relationshipSessions } = await admin
      .from("sessions")
      .select("id")
      .eq("relationship_id", session.relationship_id);
    const sessionIds = (relationshipSessions ?? []).map((s) => s.id);
    const { data: pinnedRows } = sessionIds.length
      ? await admin
        .from("messages")
        .select("content, sender, created_at")
        .in("session_id", sessionIds)
        .eq("pinned", true)
        .order("created_at", { ascending: true })
        .limit(50)
      : { data: [] as { content: string; sender: string; created_at: string }[] };

    const pinnedFacts = (pinnedRows ?? []).map((m) => {
      const who = m.sender === "partner_a"
        ? "Partner A"
        : m.sender === "partner_b"
        ? "Partner B"
        : m.sender === "ai"
        ? "Therapist"
        : "System";
      return `${who}: ${m.content}`;
    });

    const { count } = await admin
      .from("sessions")
      .select("*", { count: "exact", head: true })
      .eq("relationship_id", session.relationship_id)
      .eq("status", "ended");

    const nonSystem = transcript.filter((m) => m.sender !== "system");
    let workingSummary: string | null = session.working_summary ?? null;

    // Refresh working summary when the session grows past the raw window.
    if (
      nonSystem.length > SUMMARY_TRIGGER &&
      nonSystem.length % SUMMARY_EVERY_N === 0
    ) {
      const older = nonSystem.slice(0, -RECENT_RAW_LIMIT);
      if (older.length > 0) {
        const olderText = older
          .map((m) => {
            const who = m.sender === "ai"
              ? "Therapist"
              : m.sender === "partner_a"
              ? "Partner A"
              : m.sender === "partner_b"
              ? "Partner B"
              : "System";
            return `${who}: ${m.content}`;
          })
          .join("\n");
        try {
          const summaryCompletion = await chatCompletion(
            [
              {
                role: "system",
                content:
                  "Summarize earlier couples therapy turns into a compact working summary " +
                  "(max ~250 words). Preserve concrete facts, commitments, and open questions. " +
                  "No markdown.",
              },
              {
                role: "user",
                content:
                  (workingSummary
                    ? `Prior working summary:\n${workingSummary}\n\n`
                    : "") +
                  `Older turns to fold in:\n${olderText}`,
              },
            ],
            { temperature: 0.2 },
          );
          workingSummary = summaryCompletion.content.trim();
          await admin
            .from("sessions")
            .update({ working_summary: workingSummary })
            .eq("id", session_id);
        } catch {
          // Keep prior summary if refresh fails; still answer with recent turns.
        }
      }
    }

    let system = therapistSystemPrompt(
      memoryRow?.memory_json ?? null,
      (count ?? 0) === 0,
      workingSummary,
      promptCtx,
    );
    if (pinnedFacts.length > 0) {
      system +=
        `\n\nPinned messages (explicitly marked by partners — MUST remember and use when relevant):\n` +
        pinnedFacts.map((f) => `- ${f}`).join("\n");
    }

    const chatMessages = [
      { role: "system" as const, content: system },
      ...nonSystem
        .slice(-RECENT_RAW_LIMIT)
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
