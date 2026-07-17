# Agent Handoff — AI Couples Therapy

Use this file when continuing work in a **local Cursor IDE** (or any new agent session). Read `user_setup.md` next for credential setup details.

---

## Repo / branch

| Item | Value |
|------|--------|
| GitHub | `https://github.com/icors2/Couples-therapy-.git` |
| Working branch | `cursor/ai-couples-therapy-app-3493` (**ahead of `main`**) |
| Base branch | `main` |
| Package name | `com.aicouples.therapy` |
| App module | Android (Kotlin + Jetpack Compose + Hilt + Supabase) |

Clone (Windows):

```bash
git clone -b cursor/ai-couples-therapy-app-3493 https://github.com/icors2/Couples-therapy-.git
cd Couples-therapy-
```

Open that folder in Android Studio **and** Cursor.

---

## What already exists (built in cloud agent)

Full greenfield implementation from `Initial plan.md`:

- Android app screens: Auth (Google), Pairing, Home, Therapy chat, History, Settings
- Supabase SQL migration + RLS + realtime publication: `supabase/migrations/20260717000000_init.sql`
- Edge Functions under `supabase/functions/` (`pair-partner`, `start-session`, `join-session`, `decline-session`, `ai-respond`, `generate-memory`, `end-session`, `session-timeout`)
- CI: `.github/workflows/android-build.yml`
- Setup docs: `user_setup.md`, `README.md`
- Unit tests pass; `assembleDebug` succeeded in the cloud environment

### Intentional plan corrections (do not “fix” these back)

1. AI must **not** claim to be a licensed therapist.
2. **OpenAI key is server-side only** (Edge Functions). Never put it in the Android app.
3. Full chat transcripts stay in `messages`; structured memory rolls in `ai_memory` / `ai_archives`.
4. 10-minute inactivity end is **server-side** (`session-timeout`), not client-only.
5. No `com.aicouples.therapy://auth-callback` deep link is required — auth is native Google ID token → Supabase.

---

## Where the human is right now (setup in progress)

As of handoff, the user is configuring **Google Cloud OAuth** so they can finish Sign-In:

1. Creating Google Cloud **Web** + **Android** OAuth clients
2. About to generate **SHA-1 on their Windows machine** via Android Studio / `.\gradlew signingReport`
3. Supabase project / secrets / function deploy may still be incomplete — verify with them

**Important:** SHA-1 must be generated **locally** (their debug keystore), not in a cloud agent VM. Cloud keystores will not match APKs they install from their laptop.

### SHA-1 (Windows, project root)

```bash
.\gradlew signingReport
```

Or:

```bash
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Use the **debug** SHA-1 + package `com.aicouples.therapy` on the Google **Android** OAuth client.

---

## What the local agent should help with next

Prioritize finishing setup, not rewriting the app unless asked.

### Checklist

- [ ] Confirm branch `cursor/ai-couples-therapy-app-3493` is checked out locally
- [ ] Android Studio syncs Gradle successfully
- [ ] User has Google **Web** OAuth client with redirect:
  `https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback`
- [ ] User has Google **Android** OAuth client (package + local SHA-1)
- [ ] Supabase Google provider has Web Client ID + Secret
- [ ] `local.properties` created from `local.properties.example` with:
  - `sdk.dir=...`
  - `SUPABASE_URL=...`
  - `SUPABASE_ANON_KEY=...`
  - `GOOGLE_WEB_CLIENT_ID=...` (**Web** client ID, not Android)
- [ ] Migration applied (`supabase db push` or SQL editor)
- [ ] Realtime publication includes `messages`, `sessions`, `notifications`
- [ ] Edge Function secrets: `OPENAI_API_KEY` (+ optional `OPENAI_MODEL`, `CRON_SECRET`)
- [ ] Edge Functions deployed
- [ ] App runs on device/emulator; Google Sign-In works
- [ ] Pair two accounts → Start Therapy → chat → End Session → `ai_memory` row appears

Full instructions live in **`user_setup.md`** (Google section was expanded with Web vs Android, redirect URIs, SHA-1, Realtime).

---

## Key paths

```
app/src/main/java/com/aicouples/therapy/   # Android source
supabase/migrations/                      # DB schema + RLS
supabase/functions/                       # AI + session Edge Functions
user_setup.md                           # Human credential setup
.github/workflows/android-build.yml       # CI build
local.properties.example                  # Template (do not commit secrets)
```

Do **not** commit `local.properties`, API keys, or service role keys.

---

## Useful commands (local)

```bash
# Build + unit tests
.\gradlew testDebugUnitTest assembleDebug

# Debug signing / SHA-1
.\gradlew signingReport

# Install debug APK (device connected)
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## Common pitfalls already discovered

| Pitfall | Reality |
|---------|---------|
| Looking for redirect URIs on Android OAuth client | Redirect URIs only exist on **Web application** clients |
| Putting OpenAI key in Android | Keep in Supabase Edge Function secrets only |
| Generating SHA-1 in cloud agent | Must be local debug keystore SHA-1 |
| Expecting `therapy_sessions` table | Table is named **`sessions`** |
| Needing `com.aicouples.therapy://auth-callback` | Not used by current auth flow |

---

## PR status

Branch was pushed; a draft PR was prepared but may require the user to create/approve it in GitHub UI (auto-create was blocked by user settings). Base: `main` ← `cursor/ai-couples-therapy-app-3493`.

---

## How to continue if asked to “finish the app”

1. Do not rebuild from scratch — extend what exists.
2. Prefer fixing setup/runtime issues (auth, pairing, realtime, AI functions).
3. Keep secrets out of git; update `user_setup.md` if setup steps change.
4. After code changes: commit on `cursor/ai-couples-therapy-app-3493` and push.
