# User setup — AI Couples Therapy

This guide covers everything the cloud agent cannot configure for you: cloud projects, OAuth credentials, and API keys.

## What is already in the repo

- Android app (Kotlin, Jetpack Compose, Hilt, MVVM)
- Supabase SQL migrations under `supabase/migrations/`
- Optional Edge Functions under `supabase/functions/`
- GitHub Action `.github/workflows/android-build.yml` that runs unit tests and builds a debug APK

## 1. Create a Supabase project

1. Go to [https://supabase.com](https://supabase.com) and create a project.
2. Open **Project Settings → API** and copy:
   - Project URL → `SUPABASE_URL`
   - `anon` `public` key → `SUPABASE_ANON_KEY`
3. Apply migrations (pick one):

```bash
# Option A: Supabase CLI
npm i -g supabase
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase db push

# Option B: SQL Editor
# Paste and run, in order:
#   supabase/migrations/20260717000001_init.sql
#   supabase/migrations/20260717000002_rls.sql
#   supabase/migrations/20260717000003_session_rpc.sql
```

4. In **Authentication → URL Configuration**, add redirect URL:

```
com.aicouples.therapy://auth-callback
```

5. Enable **Realtime** for `messages`, `therapy_sessions`, and `notifications` (the migration adds them to the publication; confirm in Database → Replication).

## 2. Google Sign-In (required)

Only Google auth is supported.

### Google Cloud Console

1. Create (or reuse) a Google Cloud project.
2. Configure **OAuth consent screen**.
3. Create credentials:
   - **Web application** OAuth client (required by Supabase + Credential Manager)
   - **Android** OAuth client with:
     - Package name: `com.aicouples.therapy` (debug builds use `com.aicouples.therapy.debug`)
     - SHA-1 of your debug keystore:

```bash
keytool -list -v -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android -keypass android
```

4. Copy the **Web client ID** → `GOOGLE_WEB_CLIENT_ID`

### Supabase Auth provider

1. In Supabase: **Authentication → Providers → Google**
2. Enable Google
3. Paste the Google **Web** client ID and client secret
4. Save

## 3. OpenAI API key

The therapist calls OpenAI Chat Completions (`gpt-4o-mini` by default).

**Recommended for day-to-day use**

1. Create a key at [https://platform.openai.com/api-keys](https://platform.openai.com/api-keys)
2. In the app: **Settings → OpenAI API key → Save**

**Optional for local builds**

Add to `local.properties`:

```properties
OPENAI_API_KEY=sk-...
```

**Optional production hardening**

Deploy Edge Functions so the key never ships in the APK:

```bash
supabase secrets set OPENAI_API_KEY=sk-...
supabase functions deploy ai-respond
supabase functions deploy ai-memory
```

The Android client currently calls OpenAI directly with the user/build key (BYOK). Point it at the Edge Function later if you want server-side keys only.

## 4. Local Android configuration

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=your_anon_key
GOOGLE_WEB_CLIENT_ID=xxxx.apps.googleusercontent.com
OPENAI_API_KEY=              # optional if you paste the key in Settings
```

Then:

```bash
./gradlew assembleDebug
# Install on a device/emulator
./gradlew installDebug
```

## 5. Pairing two accounts

1. Sign in on device A → note the 6-character pair code on the pairing screen  
2. Sign in on device B (different Google account) → enter A’s code  
3. Both land on Home → **Start Therapy**

Use two emulators, or one emulator + one physical device.

## 6. GitHub Actions secrets (optional)

The default workflow builds with empty config placeholders and does **not** need secrets.

If you later add a signed release job, add repository secrets such as:

| Secret | Purpose |
|--------|---------|
| `SUPABASE_URL` | Injected into `local.properties` for CI flavors |
| `SUPABASE_ANON_KEY` | Same |
| `GOOGLE_WEB_CLIENT_ID` | Same |
| `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` | Release signing |

## 7. Checklist

- [ ] Supabase project created and migrations applied  
- [ ] Google provider enabled in Supabase  
- [ ] Google Web + Android OAuth clients created (include `.debug` package SHA-1)  
- [ ] Redirect URL `com.aicouples.therapy://auth-callback` added  
- [ ] `local.properties` filled in  
- [ ] OpenAI key set in Settings or `local.properties`  
- [ ] Two Google accounts paired successfully  

## Plan notes (reviewed before build)

A few gaps in `Initial plan.md` were resolved in the implementation:

1. **AI timing** — the AI does not answer every message; it responds when both partners have spoken, when the therapist is addressed, or after repeated messages from one partner (`PromptOrchestrator.shouldAiRespond`).
2. **API key safety** — BYOK via Settings for development; Edge Function stubs included for production.
3. **Push notifications** — in-app notification rows + Realtime are implemented; FCM device push still needs a Firebase project if you want OS-level alerts.
4. **Transcripts vs memory** — full messages stay in `messages`; only structured JSON handoffs go into `ai_memory` / `ai_archives`.
5. **Offline / streaming** — deferred; chat uses Realtime + non-streaming completions for reliability.
