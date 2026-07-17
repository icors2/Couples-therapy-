export type ChatMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

const THERAPIST_SYSTEM = `You are an AI couples therapy facilitator embedded in a messaging app with two partners.
You are NOT a licensed clinician and must never claim to be one or replace professional care.
Never choose sides. Encourage healthy communication, reflect emotions, ask thoughtful questions,
and keep discussions productive. Do not dominate — respond when helpful after partners share.
If someone appears in crisis or immediate danger, urge emergency services or a crisis hotline.
Keep replies concise (usually 2–5 short paragraphs or fewer). Use a warm, calm tone.

You receive a structured therapeutic memory JSON from prior sessions. Treat it as durable continuity.
When a partner asks you to recall details they previously shared (colors, numbers, commitments, names,
preferences, homework, etc.), answer from that memory / key_facts when present.
Do NOT say you lack access to prior sessions if the memory JSON contains the answer.
If memory truly does not contain the requested detail, say you do not have that detail saved yet
and invite them to share it again so it can be remembered.`;

/** Trim memory JSON to the fields most useful for live facilitation. */
export function trimMemoryForPrompt(memoryJson: unknown): unknown {
  if (!memoryJson || typeof memoryJson !== "object") return memoryJson;
  const m = memoryJson as Record<string, unknown>;
  return {
    key_facts: m.key_facts ?? [],
    partner_a_notes: m.partner_a_notes ?? [],
    partner_b_notes: m.partner_b_notes ?? [],
    unresolved_issues: m.unresolved_issues ?? [],
    next_session_focus: m.next_session_focus ?? "",
    agreed_commitments: m.agreed_commitments ?? [],
    goals: m.goals ?? [],
    relationship_summary: m.relationship_summary ?? "",
  };
}

export function therapistSystemPrompt(
  memoryJson: unknown,
  isFirstSession: boolean,
  workingSummary?: string | null,
): string {
  const trimmed = trimMemoryForPrompt(memoryJson);
  const memoryBlock = memoryJson
    ? `\nTherapeutic memory from prior sessions (key fields):\n${JSON.stringify(trimmed, null, 2)}`
    : "\nNo prior therapeutic memory yet.";
  const summaryBlock = workingSummary?.trim()
    ? `\n\nWorking summary of earlier turns in this session:\n${workingSummary.trim()}`
    : "";
  const continuity = isFirstSession
    ? "This may be an early session. Establish safety and rapport."
    : "You are continuing therapy with this couple. Maintain continuity using the memory JSON. Prefer recalling saved facts over asking them to repeat.";
  return `${THERAPIST_SYSTEM}\n\n${continuity}${memoryBlock}${summaryBlock}`;
}

export function memoryHandoffSystemPrompt(): string {
  return `You update structured therapeutic memory for a couples therapy app.
Return ONLY valid JSON matching this shape (no markdown):
{
  "relationship_summary": "string",
  "major_conflicts": ["string"],
  "communication_patterns": ["string"],
  "wins": ["string"],
  "goals": ["string"],
  "follow_up_topics": ["string"],
  "emotional_progress": "string",
  "next_session_focus": "string",
  "agreed_commitments": ["string"],
  "unresolved_issues": ["string"],
  "strengths": ["string"],
  "homework": ["string"],
  "key_facts": ["string"],
  "partner_a_notes": ["string"],
  "partner_b_notes": ["string"],
  "sessions_included": 0
}

Rules:
- Preserve concrete durable facts partners explicitly shared (favorite colors, number lists, names,
  preferences, dates, commitments). Put them in key_facts and/or partner_*_notes with the actual values.
- Example key_facts entry: "Partner A favorite colors (in order): green, blue, red, pink, purple, orange"
- Example: "Partner B memory-test numbers: 10, 64, 11, 888, 44456, 2215"
- Merge with prior memory: keep old key_facts unless clearly corrected or withdrawn.
- Do not invent facts not supported by the transcript or prior memory.
- Be concise and clinically careful.`;
}

export async function chatCompletion(
  messages: ChatMessage[],
  options: { temperature?: number; json?: boolean } = {},
): Promise<{ content: string; model: string; tokens?: number }> {
  const apiKey = Deno.env.get("OPENAI_API_KEY");
  if (!apiKey) throw new Error("OPENAI_API_KEY is not configured");

  const model = Deno.env.get("OPENAI_MODEL") ?? "gpt-4o-mini";
  const body: Record<string, unknown> = {
    model,
    messages,
    temperature: options.temperature ?? 0.6,
  };
  if (options.json) {
    body.response_format = { type: "json_object" };
  }

  const res = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`OpenAI error ${res.status}: ${text}`);
  }

  const data = await res.json();
  const content = data.choices?.[0]?.message?.content ?? "";
  return {
    content,
    model,
    tokens: data.usage?.total_tokens,
  };
}

/** True when a partner message looks directed at the facilitator / asks for recall. */
export function isDirectAiAddress(content: string): boolean {
  const t = content.trim().toLowerCase();
  if (!t) return false;
  if (/\b(therapist|facilitator|hey ai|ok ai)\b/.test(t)) return true;
  if (
    /\b(do you remember|can you (tell|remember|recall)|could you (tell|remember|recall)|tell me my|what (are|were|was) my|remind me)\b/
      .test(t)
  ) {
    return true;
  }
  // Question clearly about AI memory / prior sessions
  if (t.includes("?") && /\b(you|remember|memory|session|favorite|colours|colors|numbers)\b/.test(t)) {
    return true;
  }
  return false;
}

/**
 * Decide whether the AI should reply now.
 * - Always reply to direct questions / recall asks
 * - Otherwise wait until both partners have spoken recently (streak >= 2)
 * - After streak >= 2, reply on every 2nd partner message (not every message)
 */
export function shouldAiRespond(
  recentUserMessages: number,
  lastSender: string,
  lastContent = "",
): boolean {
  if (lastSender === "ai" || lastSender === "system") return false;
  if (isDirectAiAddress(lastContent)) return true;
  if (recentUserMessages < 2) return false;
  // Every 2nd partner message once both have spoken (streak 2, 4, 6…).
  return recentUserMessages % 2 === 0;
}
