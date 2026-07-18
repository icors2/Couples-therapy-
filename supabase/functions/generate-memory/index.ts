import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { chatCompletion, memoryHandoffSystemPrompt } from "../_shared/openai.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

const ROLLING_WINDOW = 5;
const TRANSCRIPT_CAP = 120;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    // Allow service role calls (from end-session) or user JWT
    const authHeader = req.headers.get("Authorization") ?? "";
    const isService = authHeader.includes(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "___");
    if (!isService) {
      await requireUser(req);
    }

    const { session_id } = await req.json();
    const admin = serviceClient();

    const { data: session } = await admin.from("sessions").select("*").eq("id", session_id).single();
    if (!session) return jsonResponse({ ok: false, message: "Session not found" }, 404);

    const { data: relForType } = await admin
      .from("relationships")
      .select("relationship_type")
      .eq("id", session.relationship_id)
      .maybeSingle();
    const relationshipType = relForType?.relationship_type as string | undefined;

    // Idempotency: one memory document per source session.
    const { data: existing } = await admin
      .from("ai_memory")
      .select("*")
      .eq("source_session_id", session_id)
      .maybeSingle();
    if (existing) {
      return jsonResponse({
        ok: true,
        session_id,
        message: "Memory already generated for this session",
        data: existing,
      });
    }

    const { data: messages } = await admin
      .from("messages")
      .select("sender, content, created_at, pinned")
      .eq("session_id", session_id)
      .order("created_at", { ascending: true });

    const { data: prior } = await admin
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
    const { data: allPinned } = sessionIds.length
      ? await admin
        .from("messages")
        .select("sender, content")
        .in("session_id", sessionIds)
        .eq("pinned", true)
        .order("created_at", { ascending: true })
        .limit(50)
      : { data: [] as { sender: string; content: string }[] };

    const allMessages = messages ?? [];
    const capped = allMessages.length > TRANSCRIPT_CAP
      ? allMessages.slice(-TRANSCRIPT_CAP)
      : allMessages;

    const transcript = capped
      .map((m) => `${m.pinned ? "[PINNED] " : ""}${m.sender}: ${m.content}`)
      .join("\n");

    const pinnedBlock = (allPinned ?? [])
      .map((m) => `- ${m.sender}: ${m.content}`)
      .join("\n");

    const completion = await chatCompletion(
      [
        { role: "system", content: memoryHandoffSystemPrompt(relationshipType) },
        {
          role: "user",
          content:
            `Prior memory JSON:\n${JSON.stringify(prior?.memory_json ?? {}, null, 2)}\n\n` +
            `Explicitly pinned messages across sessions (MUST appear in key_facts with full wording):\n` +
            `${pinnedBlock || "(none)"}\n\n` +
            (allMessages.length > TRANSCRIPT_CAP
              ? `(Transcript capped to last ${TRANSCRIPT_CAP} of ${allMessages.length} messages.)\n`
              : "") +
            `Session transcript:\n${transcript}\n\n` +
            `Produce the updated therapeutic memory JSON. ` +
            `IMPORTANT: copy any concrete lists/facts partners stated (colors, numbers, names, preferences) ` +
            `into key_facts / partner_a_notes / partner_b_notes with the actual values — do not only note that they "will list" them. ` +
            `Every pinned message above must be reflected in key_facts.`,
        },
      ],
      { temperature: 0.2, json: true, purpose: "memory" },
    );

    let memoryJson: Record<string, unknown>;
    try {
      memoryJson = JSON.parse(completion.content);
    } catch {
      return jsonResponse({ ok: false, message: "Model returned invalid JSON" }, 500);
    }

    const nextSessionsIncluded = (prior?.sessions_included ?? 0) + 1;
    memoryJson.sessions_included = nextSessionsIncluded;
    const nextVersion = (prior?.version ?? 0) + 1;

    const { data: saved, error } = await admin
      .from("ai_memory")
      .insert({
        relationship_id: session.relationship_id,
        version: nextVersion,
        memory_json: memoryJson,
        sessions_included: nextSessionsIncluded,
        source_session_id: session_id,
      })
      .select("*")
      .single();
    if (error) {
      // Race: another caller inserted first for this session.
      if (error.code === "23505") {
        const { data: raced } = await admin
          .from("ai_memory")
          .select("*")
          .eq("source_session_id", session_id)
          .maybeSingle();
        return jsonResponse({
          ok: true,
          session_id,
          message: "Memory already generated for this session",
          data: raced,
        });
      }
      return jsonResponse({ ok: false, message: error.message }, 500);
    }

    // Compress every 5 sessions into an archive and reset rolling counter in a new doc.
    if (nextSessionsIncluded > 0 && nextSessionsIncluded % ROLLING_WINDOW === 0) {
      const { data: archives } = await admin
        .from("ai_archives")
        .select("archive_number")
        .eq("relationship_id", session.relationship_id)
        .order("archive_number", { ascending: false })
        .limit(1);
      const archiveNumber = (archives?.[0]?.archive_number ?? 0) + 1;

      await admin.from("ai_archives").insert({
        relationship_id: session.relationship_id,
        archive_number: archiveNumber,
        summary: memoryJson,
      });

      const compressed = await chatCompletion(
        [
          { role: "system", content: memoryHandoffSystemPrompt() },
          {
            role: "user",
            content: `Compress this rolling memory of ${ROLLING_WINDOW} sessions into a durable long-term memory seed. Keep structure identical. sessions_included should be ${ROLLING_WINDOW}.\n\n${JSON.stringify(memoryJson)}`,
          },
        ],
        { temperature: 0.2, json: true, purpose: "memory" },
      );
      let compressedJson: Record<string, unknown> = memoryJson;
      try {
        compressedJson = JSON.parse(compressed.content);
        compressedJson.sessions_included = 0;
      } catch {
        compressedJson = { ...memoryJson, sessions_included: 0 };
      }

      await admin.from("ai_memory").insert({
        relationship_id: session.relationship_id,
        version: nextVersion + 1,
        memory_json: compressedJson,
        sessions_included: 0,
      });
    }

    return jsonResponse({ ok: true, session_id, data: saved });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 500);
  }
});
