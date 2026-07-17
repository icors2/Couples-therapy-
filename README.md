# AI Couples Therapy

Android app where two partners and an AI couples therapist share one moderated conversation, with long-term structured therapeutic memory.

## Stack

- Kotlin · Jetpack Compose · Material 3 · Hilt · Navigation
- Supabase Auth (Google) · Postgres · Realtime
- OpenAI Chat Completions (BYOK)

## Quick start

1. Follow **[user_setup.md](user_setup.md)** for Supabase, Google OAuth, and OpenAI keys.
2. Copy `local.properties.example` → `local.properties` and fill values.
3. Build:

```bash
./gradlew assembleDebug
```

## Project layout

```
app/src/main/java/com/aicouples/therapy/
  ai/           Prompt orchestration, OpenAI client, memory engine
  data/         Models, repositories, DataStore
  ui/           Auth, pairing, home, therapy chat, history, settings
supabase/
  migrations/   Schema, RLS, session RPCs
  functions/    Optional Edge Function proxies
.github/workflows/android-build.yml
```

## Product flow

1. Google Sign-In → profile + pair code  
2. Partner enters code → relationship + seed memory  
3. Start Therapy → invite → join → chat (3 bubble colors)  
4. End session (or 10 min inactivity) → JSON handoff → versioned memory  
5. Every 5 sessions → compress + archive  

See `Initial plan.md` for the full product spec.
