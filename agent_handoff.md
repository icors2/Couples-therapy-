# Agent Handoff — Family Therapy

Use this file when continuing work in a **local Cursor IDE** (or any new agent session). Read `user_setup.md` next for credential setup details.

Display name: **Family Therapy**. Package: `com.aicouples.therapy`.

---

## Repo / branch

| Item | Value |
|------|--------|
| GitHub | `https://github.com/icors2/Couples-therapy-.git` |
| Working branch | **`main`** (push here; do not require the old feature branch) |
| Package name | `com.aicouples.therapy` |
| App module | Android (Kotlin + Jetpack Compose + Hilt + Supabase) |
| Supabase project | Couples-therapy — ref `yxherebpbkvlkqboqwtg` |

Clone:

```bash
git clone https://github.com/icors2/Couples-therapy-.git
cd Couples-therapy-
git checkout main
git pull
```

Open that folder in Android Studio **and** Cursor as needed.

---

## Product features (current)

- Google Sign-In → **age gate** (`attest-age`) → Home with **multiple connections** (`couples` / `parent_child`)
- Pair / unpair; parental **consent** for minors before sessions
- **Private intake** per user per relationship — both must finish before first Start Therapy; answers own-row RLS; AI uses both on first session only
- Therapy chat + pin for AI; TTS on latest AI message (Volume); session invite / join / end
- Idle: active ~10m / pending ~30m via `expire_inactive_sessions` + `session-timeout`
- OpenAI: `OPENAI_CHAT_MODEL` (live replies) + `OPENAI_MEMORY_MODEL` (memory / summaries)

### Key migrations

| File | Purpose |
|------|---------|
| `20260717000000_init.sql` | Core schema |
| `20260717100000_notifications_is_read.sql` | `is_read` |
| `20260717103000_align_live_schema_to_app.sql` | Live DB cutover |
| `20260717120000_messages_pinned.sql` | Pin |
| `20260717140000_unpair_sessions_ai.sql` | Unpair / idle / AI helpers |
| `20260717160000_multi_relationship_family.sql` | Multi-dyad + age + consent |
| `20260717170000_relationship_intakes.sql` | Private intakes + `intake_completed` + `get_intake_status` |

### Edge Functions (deploy all)

`pair-partner`, `unpair-partner`, `attest-age`, `consent-parental`, `submit-intake`, `intake-status`, `start-session`, `join-session`, `decline-session`, `ai-respond`, `generate-memory`, `end-session`, `session-timeout`

Shared AI: `supabase/functions/_shared/openai.ts` (intake agenda, chat vs memory models, `shouldAiRespond`).

### Android intake paths

```
app/.../intake/IntakeScreen.kt
app/.../intake/IntakeViewModel.kt
app/.../data/repository/IntakeRepository.kt
HomeScreen / HomeViewModel — gate Start Therapy + Complete intake CTA
Routes.INTAKE = intake/{relationshipId}
```

---

## Intentional plan corrections (do not “fix” these back)

1. AI must **not** claim to be a licensed therapist.
2. **OpenAI key is server-side only** (Edge Functions). Never put it in the Android app.
3. Full chat transcripts stay in `messages`; structured memory rolls in `ai_memory` / `ai_archives`.
4. Inactivity end is **server-side** (`session-timeout`), not client-only.
5. No `com.aicouples.therapy://auth-callback` deep link — auth is native Google ID token → Supabase.
6. Intake answers are **private** (own-row RLS); partners never read each other’s forms. AI loads both with service role.

### Local fixes already shipped (do not re-do)

1. **Google Sign-In Activity context** — Credential Manager needs Activity (`AuthRepository` / Auth UI).
2. **Windows `sdk.dir`** — use forward slashes in `local.properties`.
3. **Cron** — SQL Editor path for `pg_cron` + `pg_net` in `user_setup.md`.

---

## Where things stand (2026-07-17 evening)

### Done

- [x] Auth Google provider enabled; Sign-In works on device
- [x] Pairing + multi-relationship family + age gate + parental consent
- [x] Unpair, idle timeout helper, AI cost guards, TTS, pin-for-AI
- [x] Split OpenAI chat vs memory models; secrets + functions deployed
- [x] Private intake schema + Edge Functions + Android UI; deployed to `yxherebpbkvlkqboqwtg`
- [x] Test history wiped for Brittanie/Chris connection so intake can be re-tested (accounts stay paired)
- [x] Work lives on **`main`** (latest includes intake commit)

### Still optional / verify

- [ ] Confirm Edge secrets: `OPENAI_API_KEY`, `OPENAI_CHAT_MODEL`, `OPENAI_MEMORY_MODEL`, `CRON_SECRET`
- [ ] Confirm `session-timeout` cron scheduled (`user_setup.md` §3 Option B)
- [ ] E2E: both complete intake → Start → Join → guided AI open → end → memory → second session skips intake

### Reset intake for an existing pair (without unpairing)

See SQL in `user_setup.md` §3 “Reset test data…”. Deletes sessions (messages cascade), `ai_memory`, intakes, related notifications for one `relationship_id`.

---

## What the local agent should help with next

1. Finish E2E intake + first-session smoke if not done.
2. Cron / secrets hygiene if missing.
3. Product polish only when asked (edit intake later, FCM, etc.).
4. After code changes: commit and **push `main`** (or PR into `main` if using a feature branch).

Full setup: **`user_setup.md`**.

---

## Key paths

```
app/src/main/java/com/aicouples/therapy/   # Android source
supabase/migrations/                      # DB schema + RLS
supabase/functions/                       # AI + session + intake Edge Functions
user_setup.md                           # Human credential setup
agent_handoff.md                          # This file
.github/workflows/android-build.yml       # CI build
local.properties.example                  # Template (do not commit secrets)
```

Do **not** commit `local.properties`, API keys, or service role keys. Do not commit `.vscode/` unless asked.

---

## Useful commands (local Windows)

```powershell
# JAVA_HOME if gradle complains (Android Studio JBR)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Build
.\gradlew assembleDebug

# Debug signing / SHA-1
.\gradlew signingReport

# Install debug APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Deploy Edge Functions (linked project)
supabase functions deploy
```

---

## Common pitfalls

| Pitfall | Reality |
|---------|---------|
| Looking for redirect URIs on Android OAuth client | Redirect URIs only on **Web** clients |
| Putting OpenAI key in Android | Keep in Supabase Edge Function secrets only |
| Expecting `therapy_sessions` | Table is **`sessions`** |
| Credential Manager + Application context | Must use **Activity** context |
| Windows `sdk.dir` with `\` | Use forward slashes |
| Intake not showing after prior therapy | Gate is “zero ended sessions”; reset SQL in `user_setup.md` |
| Partner can read intake via API | No — RLS own-row; status via `intake-status` only |
| Old feature branch as default | Prefer **`main`** |

---

## Git / PR status

Primary branch: **`main`** on `origin`. Feature branch `cursor/ai-couples-therapy-app-3493` is historical; new work should land on `main` unless the user asks for a PR workflow.

---

## How to continue if asked to “finish the app”

1. Do not rebuild from scratch — extend what exists.
2. Prefer runtime/setup and E2E over speculative refactors.
3. Keep secrets out of git; update `user_setup.md` / this handoff when behavior changes.
4. Commit and push **`main`** after meaningful changes.
