// Optional Edge Function for memory handoff generation with service-role writes.
// Secrets: OPENAI_API_KEY, SUPABASE_SERVICE_ROLE_KEY, SUPABASE_URL

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const { relationship_id, messages, system_prompt } = await req.json();
  if (!relationship_id || !messages) {
    return new Response(JSON.stringify({ error: "relationship_id and messages required" }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  }

  const completionRes = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${OPENAI_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: "gpt-4o-mini",
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: system_prompt ?? "Return therapeutic memory JSON only." },
        { role: "user", content: JSON.stringify(messages) },
      ],
    }),
  });

  if (!completionRes.ok) {
    return new Response(await completionRes.text(), { status: completionRes.status });
  }

  const completion = await completionRes.json();
  const content = completion.choices?.[0]?.message?.content ?? "{}";
  const memory = JSON.parse(content);

  const supabase = createClient(SUPABASE_URL, SERVICE_ROLE);
  await supabase.from("ai_memory").update({ is_current: false }).eq("relationship_id", relationship_id).eq("is_current", true);

  const { data: latest } = await supabase
    .from("ai_memory")
    .select("version")
    .eq("relationship_id", relationship_id)
    .order("version", { ascending: false })
    .limit(1)
    .maybeSingle();

  const version = (latest?.version ?? 0) + 1;
  const { data, error } = await supabase.from("ai_memory").insert({
    relationship_id,
    version,
    memory_json: memory,
    sessions_included: memory.sessions_included ?? 0,
    is_current: true,
  }).select().single();

  if (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }

  return new Response(JSON.stringify(data), {
    headers: { "Content-Type": "application/json" },
  });
});
