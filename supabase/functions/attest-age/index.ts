import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

function ageYears(dob: Date, now = new Date()): number {
  let age = now.getUTCFullYear() - dob.getUTCFullYear();
  const m = now.getUTCMonth() - dob.getUTCMonth();
  if (m < 0 || (m === 0 && now.getUTCDate() < dob.getUTCDate())) age--;
  return age;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const { date_of_birth } = await req.json();
    const dobStr = String(date_of_birth ?? "").trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dobStr)) {
      return jsonResponse({ ok: false, message: "date_of_birth must be YYYY-MM-DD" }, 400);
    }
    const dob = new Date(`${dobStr}T00:00:00.000Z`);
    if (Number.isNaN(dob.getTime())) {
      return jsonResponse({ ok: false, message: "Invalid date_of_birth" }, 400);
    }
    const now = new Date();
    if (dob > now) {
      return jsonResponse({ ok: false, message: "Date of birth cannot be in the future" }, 400);
    }
    const age = ageYears(dob, now);
    if (age > 120) {
      return jsonResponse({ ok: false, message: "Invalid date of birth" }, 400);
    }
    const isMinor = age < 18;
    const admin = serviceClient();
    const { data, error } = await admin
      .from("profiles")
      .update({
        date_of_birth: dobStr,
        age_attested_at: now.toISOString(),
        is_minor: isMinor,
      })
      .eq("id", user.id)
      .select("*")
      .single();
    if (error) return jsonResponse({ ok: false, message: error.message }, 500);

    return jsonResponse({
      ok: true,
      message: isMinor ? "Age recorded (minor account)" : "Age recorded",
      data: { is_minor: isMinor, date_of_birth: dobStr },
      profile: data,
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
