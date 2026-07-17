# Agent Handoff — AI Couples Therapy

Use this file when continuing work in a **local Cursor IDE** (or any new agent session). Read `user_setup.md` next for credential setup details.

---

## Repo / branch

| Item | Value |
|------|--------|
| GitHub | `https://github.com/icors2/Couples-therapy-.git` |
| Working branch | `main` (multi-relationship work may be local until committed) |
| Package name | `com.aicouples.therapy` |
| App module | Android (Kotlin + Jetpack Compose + Hilt + Supabase) |
| Supabase ref | `yxherebpbkvlkqboqwtg` |

### Multi-relationship + parent–child (2026-07-17)

- Users can have **multiple** dyads (`couples` or `parent_child`); Home picks who to start with.
- **Age gate** (`attest-age`) required after sign-in; minors need **parental consent** (`consent-parental`) before sessions.
- New migration: `supabase/migrations/20260717160000_multi_relationship_family.sql`
- New Edge Functions: `attest-age`, `consent-parental`; updated `pair-partner`, `unpair-partner`, `start-session`, AI prompts.
- Install latest `app-debug.apk`; existing users will see the age gate once, then re-add connections if needed.

Clone (Windows):

```bash
git clone -b cursor/ai-couples-therapy-app-3493 https://github.com/icors2/Couples-therapy-.git
cd Couples-therapy-
```

Open that folder in Android Studio **and** Cursor (user may run Android Studio alone when free RAM is needed for the emulator).

---

## What already exists (built in cloud agent + local fixes)

Full greenfield implementation from `Initial plan.md`:

- Android app screens: Auth (Google), Pairing, Home, Therapy chat, History, Settings
- Supabase SQL migration + RLS + realtime publication: `supabase/migrations/20260717000000_init.sql`
- Edge Functions under `supabase/functions/` (`pair-partner`, `start-session`, `join-session`, `decline-session`, `ai-respond`, `generate-memory`, `end-session`, `session-timeout`)
- CI: `.github/workflows/android-build.yml`
- Setup docs: `user_setup.md`, `README.md`, this handoff
- Local `assembleDebug` succeeds (with Android Studio JBR as `JAVA_HOME` if needed)

### Intentional plan corrections (do not “fix” these back)

1. AI must **not** claim to be a licensed therapist.
2. **OpenAI key is server-side only** (Edge Functions). Never put it in the Android app. Architecture already uses `ai-respond` / `generate-memory` — no BYOK client path.
3. Full chat transcripts stay in `messages`; structured memory rolls in `ai_memory` / `ai_archives`.
4. 10-minute inactivity end is **server-side** (`session-timeout`), not client-only.
5. No `com.aicouples.therapy://auth-callback` deep link is required — auth is native Google ID token → Supabase.

### Local fixes already shipped (do not re-do)

1. **Google Sign-In Activity context** — Credential Manager was using `@ApplicationContext`, which cannot show the account picker (`Failed to launch the selector UI`). Fixed in `AuthRepository` / `AuthScreen` / `AuthViewModel` to pass Activity context. Pushed in `8dc911f`.
2. **Windows `sdk.dir`** — backslashes in `local.properties` caused `Invalid file path`. Use forward slashes (`C:/Users/.../Android/Sdk`). Documented in `user_setup.md` + `local.properties.example`.
3. **`user_setup.md` cron section** — SQL Editor path for `pg_cron` + `pg_net` (Dashboard Cron UI may be hard to find).

---

## Where the human is right now (2026-07-17)

Setup is mid-flight on a Windows machine. User is **closing Cursor** to free RAM so **Android Studio can run a phone emulator**.

### Done / mostly done

- [x] Branch `cursor/ai-couples-therapy-app-3493` checked out locally
- [x] `local.properties` filled (Supabase URL, anon key, Google Web Client ID) — **do not commit**
- [x] Debug APK builds (`assembleDebug` OK)
- [x] App installed and launched on a physical device
- [x] Google account picker UI works after Activity-context fix
- [x] OpenAI intended as Edge Function secrets only (server-side)

### Blocked / verify next (auth)

Last on-device error after picking a Google account:

```text
Provider (issuer "https://accounts.google.com") is not enabled
```

That means Supabase Auth **Google provider** was not enabled (or missing Web Client ID + Secret).

**User action (Supabase Dashboard):**

1. **Authentication → Providers → Google** → enable
2. Paste **Web** Client ID + Client Secret (same Web Client ID as `GOOGLE_WEB_CLIENT_ID` in `local.properties`)
3. Ensure Google Cloud **Web** OAuth client has redirect:  
   `https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback`

After that, Sign-In should complete without a rebuild (if Client ID already matches).

### Live DB aligned to app (2026-07-17)

Project was originally on a divergent schema (`therapy_sessions`, `user_settings`, `read_at`, etc.). Aligned via:

- `supabase/migrations/20260717100000_notifications_is_read.sql`
- `supabase/migrations/20260717103000_align_live_schema_to_app.sql` (applied with `supabase db query --linked`)
- Migration history repaired so `20260717000000` / `20260717100000` / `20260717103000` are marked **applied**

**Preserved:** 2 profiles, 1 relationship, auth users.  
**Recreated to match app:** `sessions`, `messages`, `settings`, `ai_memory`, `ai_archives`, RLS, realtime (`messages`/`sessions`/`notifications`), `expire_inactive_sessions`, `handle_new_user` → `settings`.  
**Removed:** `therapy_sessions`, `user_settings`, `device_tokens`, old SQL RPCs.

**Verified:** all 8 Edge Functions are ACTIVE (`pair-partner`, `start-session`, `join-session`, `decline-session`, `ai-respond`, `generate-memory`, `end-session`, `session-timeout`).

### Still incomplete (backend / E2E)

- [x] Live schema aligned with app
- [x] Realtime publication includes `messages`, `sessions`, `notifications`
- [x] Edge Functions deployed (all 8 ACTIVE)
- [ ] Edge Function secrets: confirm `OPENAI_API_KEY` (+ optional `OPENAI_MODEL`, `CRON_SECRET`)
- [ ] `session-timeout` cron scheduled (SQL Editor path in `user_setup.md` §3)
- [x] Google Sign-In works
- [x] Pairing works (two accounts; pairing preserved after schema align)
- [x] Retest Home / Join Therapy / live chat (polling + realtime fixes)
- [x] AI memory prompts + `key_facts` + direct-question reply heuristic deployed (`ai-respond`, `generate-memory`)
- [x] Explicit message pin (long-press → Pin for AI); `messages.pinned`; pinned facts injected into AI prompts
- [ ] Retest pin + memory recall after reinstalling debug APK

### Emulator note

Full couples smoke test needs **two Google accounts** (cannot pair with own code). Lightest path: phone + emulator, or two emulators. Progressive checks (sign-in alone, then `session-timeout` POST with `x-cron-secret`) are documented in conversation; can be added to `user_setup.md` if useful.

### SHA-1 (Windows, if Android OAuth client still missing)

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

Prioritize finishing setup / runtime auth, not rewriting the app unless asked.

1. Confirm Google provider enabled in Supabase → Sign-In succeeds.
2. Confirm migration + Edge Function secrets + deploys.
3. Help run emulator / second account for pairing smoke test.
4. Debug AI response path via Edge Function logs if chat fails after pairing.

Full instructions: **`user_setup.md`**.

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

## Useful commands (local Windows)

```powershell
# JAVA_HOME if gradle complains (Android Studio JBR)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Build + unit tests
.\gradlew testDebugUnitTest assembleDebug

# Debug signing / SHA-1
.\gradlew signingReport

# Install debug APK
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
| Credential Manager + Application context | Must use **Activity** context (fixed) |
| Windows `sdk.dir` with `\` | Use forward slashes or escaped path |
| `Provider … accounts.google.com is not enabled` | Enable Google under Supabase Auth → Providers |
| Can’t find Dashboard “Cron” / pg_net menu | Use SQL Editor (`user_setup.md` Option B) |

---

## PR status

Branch pushed to origin. Draft PR may still need user create/approve in GitHub UI. Base: `main` ← `cursor/ai-couples-therapy-app-3493`.

---

## How to continue if asked to “finish the app”

1. Do not rebuild from scratch — extend what exists.
2. Prefer fixing setup/runtime issues (auth, pairing, realtime, AI functions).
3. Keep secrets out of git; update `user_setup.md` if setup steps change.
4. After code changes: commit on `cursor/ai-couples-therapy-app-3493` and push.
5. Refresh this handoff when setup milestones complete.
