import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

type Answers = Record<string, unknown>;

function trimStr(v: unknown, max = 2000): string {
  if (typeof v !== "string") return "";
  return v.trim().slice(0, max);
}

function requireText(answers: Answers, key: string): string | null {
  const t = trimStr(answers[key]);
  return t.length >= 2 ? t : null;
}

function validateAnswers(
  answers: Answers,
  relationshipType: string,
  myRole: string,
): { ok: true; cleaned: Answers } | { ok: false; message: string } {
  const goals = requireText(answers, "goals");
  const mainConcerns = requireText(answers, "main_concerns");
  const wantFromSessions = requireText(answers, "want_from_sessions");
  if (!goals || !mainConcerns || !wantFromSessions) {
    return {
      ok: false,
      message: "Please fill in goals, main concerns, and what you want from sessions",
    };
  }

  const safetyConcern = Boolean(answers.safety_concern);
  const cleaned: Answers = {
    goals,
    main_concerns: mainConcerns,
    want_from_sessions: wantFromSessions,
    strengths: trimStr(answers.strengths),
    communication_wish: trimStr(answers.communication_wish),
    anything_else: trimStr(answers.anything_else),
    safety_concern: safetyConcern,
    safety_note: safetyConcern ? trimStr(answers.safety_note) : "",
  };

  if (relationshipType === "parent_child") {
    const roleNotes = requireText(answers, "role_notes");
    if (!roleNotes) {
      const hint = myRole === "parent"
        ? "Please share your concerns about your child or your relationship"
        : "Please share what feels hard or what you want help with";
      return { ok: false, message: hint };
    }
    cleaned.role_notes = roleNotes;
  }

  return { ok: true, cleaned };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const body = await req.json();
    const relationship_id = body.relationship_id as string | undefined;
    const answers = (body.answers ?? {}) as Answers;
    if (!relationship_id) {
      return jsonResponse({ ok: false, message: "relationship_id is required" }, 400);
    }

    const admin = serviceClient();
    const { data: relationship } = await admin
      .from("relationships")
      .select("*")
      .eq("id", relationship_id)
      .single();
    if (!relationship?.partner2_id) {
      return jsonResponse({ ok: false, message: "Partner not paired yet" }, 400);
    }
    if (relationship.partner1_id !== user.id && relationship.partner2_id !== user.id) {
      return jsonResponse({ ok: false, message: "Forbidden" }, 403);
    }

    const myRole = relationship.partner1_id === user.id
      ? relationship.partner1_role
      : relationship.partner2_role;

    const validated = validateAnswers(
      answers,
      relationship.relationship_type as string,
      myRole as string,
    );
    if (!validated.ok) {
      return jsonResponse({ ok: false, message: validated.message }, 400);
    }

    const { data: existing } = await admin
      .from("relationship_intakes")
      .select("id, completed_at")
      .eq("relationship_id", relationship_id)
      .eq("user_id", user.id)
      .maybeSingle();
    if (existing) {
      return jsonResponse({
        ok: true,
        message: "Intake already completed",
        relationship_id,
        data: existing,
      });
    }

    const { data: row, error } = await admin
      .from("relationship_intakes")
      .insert({
        relationship_id,
        user_id: user.id,
        answers: validated.cleaned,
      })
      .select("id, relationship_id, user_id, completed_at")
      .single();
    if (error) return jsonResponse({ ok: false, message: error.message }, 500);

    const otherId = relationship.partner1_id === user.id
      ? relationship.partner2_id
      : relationship.partner1_id;
    const { data: me } = await admin.from("profiles").select("display_name").eq("id", user.id).single();
    if (otherId) {
      await admin.from("notifications").insert({
        user_id: otherId,
        type: "intake_completed",
        title: "Intake completed",
        body: `${me?.display_name ?? "Your partner"} finished their private intake.`,
        payload: { relationship_id },
      });
    }

    return jsonResponse({
      ok: true,
      message: "Intake saved",
      relationship_id,
      data: row,
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
