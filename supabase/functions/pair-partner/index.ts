import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { corsHeaders, jsonResponse } from "../_shared/cors.ts";
import { requireUser, serviceClient } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  try {
    const { user } = await requireUser(req);
    const { partner_code } = await req.json();
    const code = String(partner_code ?? "").trim().toUpperCase();
    if (!/^[A-Z2-9]{6}$/.test(code)) {
      return jsonResponse({ ok: false, message: "Invalid pair code" }, 400);
    }

    const admin = serviceClient();
    const { data: me, error: meErr } = await admin
      .from("profiles")
      .select("*")
      .eq("id", user.id)
      .single();
    if (meErr || !me) return jsonResponse({ ok: false, message: "Profile not found" }, 404);
    if (me.relationship_id) {
      return jsonResponse({ ok: false, message: "You are already paired" }, 400);
    }
    if (me.pair_code === code) {
      return jsonResponse({ ok: false, message: "You cannot pair with your own code" }, 400);
    }

    const { data: partner, error: partnerErr } = await admin
      .from("profiles")
      .select("*")
      .eq("pair_code", code)
      .maybeSingle();
    if (partnerErr || !partner) {
      return jsonResponse({ ok: false, message: "Partner code not found" }, 404);
    }
    if (partner.relationship_id) {
      return jsonResponse({ ok: false, message: "That partner is already paired" }, 400);
    }

    const { data: relationship, error: relErr } = await admin
      .from("relationships")
      .insert({ partner1_id: partner.id, partner2_id: me.id })
      .select("*")
      .single();
    if (relErr || !relationship) {
      return jsonResponse({ ok: false, message: relErr?.message ?? "Could not create relationship" }, 500);
    }

    const { error: updateErr } = await admin
      .from("profiles")
      .update({ relationship_id: relationship.id })
      .in("id", [me.id, partner.id]);
    if (updateErr) {
      return jsonResponse({ ok: false, message: updateErr.message }, 500);
    }

    await admin.from("notifications").insert([
      {
        user_id: me.id,
        type: "partner_paired",
        title: "You're paired",
        body: `You are now connected with ${partner.display_name ?? "your partner"}.`,
        payload: { relationship_id: relationship.id },
      },
      {
        user_id: partner.id,
        type: "partner_paired",
        title: "You're paired",
        body: `${me.display_name ?? "Your partner"} connected with your pair code.`,
        payload: { relationship_id: relationship.id },
      },
    ]);

    return jsonResponse({
      ok: true,
      relationship_id: relationship.id,
      message: "Paired successfully",
    });
  } catch (e) {
    return jsonResponse({ ok: false, message: (e as Error).message }, 401);
  }
});
