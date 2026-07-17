export type ChatMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

export type RelationshipPromptContext = {
  relationshipType?: "couples" | "parent_child" | string;
  partner1Role?: string;
  partner2Role?: string;
  includesMinor?: boolean;
};

const COUPLES_SYSTEM = `You are an AI couples therapy facilitator embedded in a messaging app with two partners.
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

const FAMILY_SYSTEM = `You are an AI family communication facilitator for a parent and child in a messaging app.
You are NOT a licensed clinician and must never claim to be one or replace professional care.
Do NOT treat them as equal romantic partners. Acknowledge power dynamics: the parent holds more authority;
support the child's age-appropriate autonomy and voice without undermining safety.
Never triangulate or take sides. Encourage respectful listening, clear boundaries, and repair after conflict.
Keep replies concise (usually 2–5 short paragraphs or fewer). Use a warm, calm tone.
If someone appears in crisis or immediate danger, urge emergency services or a crisis hotline,
and for a minor also urge involving a trusted adult / parent / guardian.

You receive structured therapeutic memory JSON from prior sessions. Treat it as durable continuity for THIS family dyad only.`;

const MINOR_SAFETY = `
Extra safety (a minor is present):
- No romantic, sexual, or adult-dating framing.
- Use language appropriate for a minor; avoid graphic content.
- Prefer inviting a trusted adult when topics involve safety, abuse, self-harm, or emergencies.
- Never ask a minor to keep secrets from a parent/guardian about safety concerns.`;

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

function roleLabels(ctx?: RelationshipPromptContext): string {
  if (!ctx || ctx.relationshipType !== "parent_child") {
    return "Label speakers as Partner A and Partner B.";
  }
  const a = ctx.partner1Role === "parent" ? "Parent" : ctx.partner1Role === "child" ? "Child" : "Person A";
  const b = ctx.partner2Role === "parent" ? "Parent" : ctx.partner2Role === "child" ? "Child" : "Person B";
  return `This is a parent–child dyad. Prefer labels "${a}" (partner_a) and "${b}" (partner_b) in your understanding.`;
}

export function therapistSystemPrompt(
  memoryJson: unknown,
  isFirstSession: boolean,
  workingSummary?: string | null,
  ctx?: RelationshipPromptContext,
): string {
  const base = ctx?.relationshipType === "parent_child" ? FAMILY_SYSTEM : COUPLES_SYSTEM;
  const trimmed = trimMemoryForPrompt(memoryJson);
  const memoryBlock = memoryJson
    ? `\nTherapeutic memory from prior sessions (key fields):\n${JSON.stringify(trimmed, null, 2)}`
    : "\nNo prior therapeutic memory yet.";
  const summaryBlock = workingSummary?.trim()
    ? `\n\nWorking summary of earlier turns in this session:\n${workingSummary.trim()}`
    : "";
  const continuity = isFirstSession
    ? "This may be an early session. Establish safety and rapport."
    : ctx?.relationshipType === "parent_child"
    ? "You are continuing with this parent–child pair. Maintain continuity using the memory JSON."
    : "You are continuing therapy with this couple. Maintain continuity using the memory JSON. Prefer recalling saved facts over asking them to repeat.";
  const minor = ctx?.includesMinor ? `\n${MINOR_SAFETY}` : "";
  return `${base}\n\n${roleLabels(ctx)}\n\n${continuity}${memoryBlock}${summaryBlock}${minor}`;
}

export function memoryHandoffSystemPrompt(relationshipType?: string): string {
  const who = relationshipType === "parent_child"
    ? "a parent–child family facilitation app"
    : "a couples therapy app";
  return `You update structured therapeutic memory for ${who}.
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
- Preserve concrete durable facts members explicitly shared.
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
  if (t.includes("?") && /\b(you|remember|memory|session|favorite|colours|colors|numbers)\b/.test(t)) {
    return true;
  }
  return false;
}

export function shouldAiRespond(
  recentUserMessages: number,
  lastSender: string,
  lastContent = "",
): boolean {
  if (lastSender === "ai" || lastSender === "system") return false;
  if (isDirectAiAddress(lastContent)) return true;
  if (recentUserMessages < 2) return false;
  return recentUserMessages % 2 === 0;
}
