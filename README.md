# AI Couples Therapy

Android app where two partners message each other with an AI couples-therapy facilitator in the conversation. Built with Kotlin, Jetpack Compose, Hilt, and Supabase (Auth, Postgres, Realtime, Edge Functions + OpenAI).

## Features

- Google Sign-In via Supabase Auth
- Partner pairing with 6-character codes
- Messaging UI (Partner A / Partner B / AI) with typing indicator and session timer
- Session invite → join / decline flow
- Server-side AI responses and structured therapeutic memory handoffs
- Rolling memory compression + archives (full transcripts retained)
- History, settings, in-app notifications

## Project layout

```
app/                  Android application
supabase/migrations   Postgres schema + RLS
supabase/functions    Edge Functions (pairing, sessions, AI, timeout)
.github/workflows     CI Android build
user_setup.md         API keys & cloud setup you must complete
Initial plan.md       Original product spec
```

## Quick start

1. Follow **[user_setup.md](./user_setup.md)** (Supabase, Google OAuth, OpenAI secrets).
2. Copy `local.properties.example` → `local.properties` and fill values.
3. `./gradlew assembleDebug`

## Architecture notes

- **MVVM + repository pattern** on Android
- **AI keys stay on the server** (Edge Functions)
- **Transcripts ≠ memory**: `messages` are permanent; `ai_memory` is versioned structured JSON
- **10-minute inactivity** enforced by `session-timeout` + `expire_inactive_sessions()`

## License

Private / unlicensed unless otherwise stated by the repository owner.
