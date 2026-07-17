# User Setup — AI Couples Therapy

This guide covers everything the codebase cannot configure for you: cloud projects, OAuth, API keys, and device install.

## What was corrected from the original plan

Before setup, note these intentional deviations from `Initial plan.md`:

1. **The AI must not claim to be a licensed therapist.** Prompts and UI copy describe an AI facilitator / guided support tool, with a clear disclaimer.
2. **OpenAI keys never ship in the Android app.** All AI calls go through Supabase Edge Functions. Only the Supabase URL + anon/publishable key and Google Web Client ID belong in `local.properties`.
3. **Full transcripts stay forever** in `messages`. Compression only rolls structured `ai_memory` / `ai_archives` documents (token control without deleting history).
4. **Session inactivity timeout is server-side** via `session-timeout` + SQL helper `expire_inactive_sessions` (schedule with cron). Client timers alone are not enough.
5. **In-app notifications table is first-class.** True mobile push (FCM) is optional follow-up — wire Firebase later if you need lock-screen alerts.

---

## 1. Create a Supabase project

1. Open [Supabase Dashboard](https://supabase.com/dashboard) → **New project**.
2. Note:
   - **Project URL** → `SUPABASE_URL`
   - **anon / publishable key** → `SUPABASE_ANON_KEY`
   - **service_role key** → used only as a server secret (never in the app)

### Apply database schema

From this repo (with [Supabase CLI](https://supabase.com/docs/guides/cli) installed):

```bash
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase db push
```

Or paste/run `supabase/migrations/20260717000000_init.sql` in the SQL editor.

### Enable Google Auth

1. Supabase → **Authentication → Providers → Google** → enable.
2. Add your Google **Web Client ID** and **Client Secret** (from Google Cloud Console).
3. Add redirect URL from the Supabase Google provider screen to Google Cloud **Authorized redirect URIs**.

---

## 2. Google Cloud OAuth

1. Create/select a Google Cloud project.
2. Configure **OAuth consent screen**.
3. Create credentials:
   - **Web application** client (required by Supabase + Credential Manager `serverClientId`)
   - **Android** client with your package name `com.aicouples.therapy` and SHA-1:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

4. Put the **Web** client ID into Android `local.properties` as `GOOGLE_WEB_CLIENT_ID`.

---

## 3. OpenAI (server secrets)

In Supabase → **Edge Functions → Secrets** (or CLI):

```bash
supabase secrets set OPENAI_API_KEY=sk-...
supabase secrets set OPENAI_MODEL=gpt-4o-mini
supabase secrets set CRON_SECRET=$(openssl rand -hex 32)
```

`SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` are normally injected automatically for Edge Functions.

### Deploy functions

```bash
supabase functions deploy pair-partner
supabase functions deploy start-session
supabase functions deploy join-session
supabase functions deploy decline-session
supabase functions deploy ai-respond
supabase functions deploy generate-memory
supabase functions deploy end-session
supabase functions deploy session-timeout
```

### Schedule inactivity timeout

Use Supabase **Cron** (pg_cron + `net.http_post`) or an external cron hitting:

`POST https://YOUR_PROJECT_REF.supabase.co/functions/v1/session-timeout`

Header: `x-cron-secret: <CRON_SECRET>`

Suggested cadence: every 5 minutes.

---

## 4. Android local config

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=your_anon_or_publishable_key
GOOGLE_WEB_CLIENT_ID=xxxx.apps.googleusercontent.com
```

Build:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. GitHub Actions secrets (optional but recommended)

For `.github/workflows/android-build.yml`, add repository secrets:

| Secret | Purpose |
|--------|---------|
| `SUPABASE_URL` | Injected into CI `local.properties` |
| `SUPABASE_ANON_KEY` | Injected into CI `local.properties` |
| `GOOGLE_WEB_CLIENT_ID` | Injected into CI `local.properties` |

The workflow still builds if these are empty (placeholder BuildConfig values). Runtime sign-in will fail until real values are present on a device/emulator build.

---

## 6. Quick smoke test

1. Install the debug APK on two devices/emulators (or two Google accounts via multiple users).
2. Sign in with Google on both.
3. Share pair codes and connect.
4. Partner A taps **Start Therapy** → Partner B **Join**.
5. Exchange a few messages → AI should respond after enough partner context.
6. **End Session** → confirm a new `ai_memory` row appears in Supabase.

---

## 7. Optional later

- Firebase Cloud Messaging for true push notifications
- Anthropic / Gemini providers behind the same Edge Function interface
- Room offline cache for message history
- Play App Signing SHA-1 for release Google Sign-In
