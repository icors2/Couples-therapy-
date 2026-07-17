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
2. Add your Google **Web Client ID** and **Client Secret** (from the Web client you create below).
3. Copy the callback URL shown on that Supabase page (usually  
   `https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback`) — you will paste it into Google Cloud as an **Authorized redirect URI**.

### Confirm Realtime publication

After the migration runs, confirm these tables are on the `supabase_realtime` publication:

1. Supabase → **Database** → **Publications** (sometimes labeled **Replication**).
2. Open **`supabase_realtime`**.
3. Ensure **`messages`**, **`sessions`**, and **`notifications`** are listed/enabled.

(This app does **not** use a `com.aicouples.therapy://auth-callback` deep link for sign-in. Auth is native Google ID token → Supabase.)

---

## 2. Google Cloud OAuth (step-by-step)

You need **two** OAuth clients in the same Google Cloud project:

| Client type | Purpose |
|-------------|---------|
| **Web application** | Supabase Google provider + Android `GOOGLE_WEB_CLIENT_ID` |
| **Android** | Lets Google Sign-In work on your device (package name + SHA-1) |

### 2a. OAuth consent screen

1. Open [Google Cloud Console](https://console.cloud.google.com/) → select/create a project.
2. **APIs & Services** → **OAuth consent screen**.
3. Choose **External** (unless you only use Workspace accounts) → create.
4. Fill **App name**, **User support email**, and **Developer contact**.
5. Save. You can leave scopes at defaults for now.
6. If the app is in **Testing**, add your Google accounts under **Test users**.

“Authorized domains” are added automatically from redirect URIs (e.g. `supabase.co`). You usually do **not** need to type them yourself.

### 2b. Generate the SHA-1 fingerprint (for the Android client)

Google asks for SHA-1 so it can verify your app’s signing certificate. For **local debug builds**, use the Android debug keystore.

**macOS / Linux** (run in a terminal):

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

**Windows** (Command Prompt / PowerShell):

```bash
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
```

In the output, find the line:

```text
SHA1: AB:CD:EF:...
```

Copy that entire SHA-1 value (colons included).

Notes:

- If `~/.android/debug.keystore` does not exist yet, build/run the app once in Android Studio (or `./gradlew assembleDebug`) — Android Studio creates the debug keystore automatically.
- `keytool` ships with the JDK. If the command is missing, install a JDK or use Android Studio’s bundled JDK.
- **Release / Play Store builds use a different SHA-1.** When you publish, add another Android OAuth client (or another SHA-1 on the same client) from Play App Signing / your release keystore.

Optional (if you use Android Studio’s Gradle signing report):

```bash
./gradlew signingReport
```

Look under `Variant: debug` → `SHA1`.

### 2c. Create the Web application OAuth client

Redirect URI fields appear **only** when Application type is **Web application**.

1. **APIs & Services** → **Credentials** → **+ Create credentials** → **OAuth client ID**.
2. **Application type** → **Web application** (not Android, iOS, or Desktop).
3. Name it e.g. `Supabase Web`.
4. Under **Authorized redirect URIs** → **Add URI**:

```text
https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback
```

5. Leave **Authorized JavaScript origins** empty for this Android setup.
6. **Create** → copy **Client ID** and **Client secret**.

Then:

- Paste both into Supabase → **Authentication → Providers → Google**.
- Put the **Client ID only** into Android `local.properties` as `GOOGLE_WEB_CLIENT_ID`.

### 2d. Create the Android OAuth client

1. **Credentials** → **+ Create credentials** → **OAuth client ID**.
2. **Application type** → **Android**.
3. Name it e.g. `AI Couples Therapy Debug`.
4. **Package name:** `com.aicouples.therapy`
5. **SHA-1 certificate fingerprint:** paste the SHA-1 from section 2b.
6. **Create**.

You will **not** see Authorized redirect URIs on the Android client — that is normal.

### 2e. What goes where (checklist)

| Value | Where |
|-------|--------|
| Web Client ID | Supabase Google provider **and** `local.properties` → `GOOGLE_WEB_CLIENT_ID` |
| Web Client Secret | Supabase Google provider only (never in the Android app) |
| Android client (package + SHA-1) | Google Cloud only (enables device Sign-In) |
| Supabase callback URL | Google **Web** client → Authorized redirect URIs |

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
