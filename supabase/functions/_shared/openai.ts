export type ChatMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

const THERAPIST_SYSTEM = `You are an AI couples therapy facilitator embedded in a messaging app with two partners.
You are NOT a licensed clinician and must never claim to be one or replace professional care.
Never choose sides. Encourage healthy communication, reflect emotions, ask thoughtful questions,
and keep discussions productive. Do not dominate — respond when helpful after partners share.
If someone appears in crisis or immediate danger, urge emergency services or a crisis hotline.
Keep replies concise (usually 2–5 short paragraphs or fewer). Use a warm, calm tone.`;

export function therapistSystemPrompt(memoryJson: unknown, isFirstSession: boolean): string {
  const memoryBlock = memoryJson
    ? `\nTherapeutic memory (structured JSON):\n${JSON.stringify(memoryJson, null, 2)}`
    : "\nNo prior therapeutic memory yet.";
  const continuity = isFirstSession
    ? "This may be an early session. Establish safety and rapport."
    : "You are continuing therapy with this couple. Maintain continuity. Do not repeat prior interventions unless appropriate.";
  return `${THERAPIST_SYSTEM}\n\n${continuity}${memoryBlock}`;
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
  "sessions_included": 0
}
Be concise, specific, and clinically careful. Do not invent facts not supported by the transcript.`;
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

/** Heuristic: AI should not reply to every single message. */
export function shouldAiRespond(recentUserMessages: number, lastSender: string): boolean {
  if (lastSender === "ai" || lastSender === "system") return false;
  // Reply after both partners have spoken recently, or after 2+ consecutive partner messages
  return recentUserMessages >= 2;
}
