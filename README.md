# CoachApp

A coaching platform in the style of Kahunas. Coaches build personalised **workout**, **meal** and **supplement** plans for each client and review their **weekly check-ins**. Clients follow the plans, log workouts, meals and supplements, and submit check-ins with progress photos.

- **Client app**: Compose Multiplatform on Android, iOS and Web (wasmJs). The web build doubles as the coach dashboard.
- **Backend**: Supabase (Postgres + Row Level Security, Auth, Storage).
- **iOS**: a native SwiftUI shell (tabs, navigation bars) hosting the shared Kotlin screens.

See **[docs/PLAN.md](docs/PLAN.md)** for architecture, status, known gaps and the roadmap.

## Project layout

```
composeApp/        Shared KMP module (UI, ViewModels, repositories)
  src/commonMain/kotlin/ch/coachapp/
    core/          Supabase client, error handling, dates, theme, shared UI
    data/          @Serializable models and Supabase repositories
    domain/        Plan drafts (builder state) and macro calculations
    feature/auth   Sign in / sign up / join a coach
    feature/coach  Clients, invites, plan builders, library, check-in review
    feature/client Today, workout session, plans, check-in form, history
    navigation/    Role-based root (coach vs. client graphs)
    di/            Koin modules
androidApp/        Android application (AGP 9 requires a separate app module)
iosApp/            Xcode project embedding the ComposeApp framework
supabase/
  migrations/      Schema, RLS, functions (save/assign plans, invites), storage
  seed.sql         Demo accounts and data
  tests/           pgTAP RLS tests
```

## Getting started

Requirements: JDK 17+ (Android Studio's bundled JBR works), Docker and Node (for `npx supabase`).

```bash
# 1. Backend
npx supabase start          # applies migrations + seed
npx supabase test db        # RLS tests

# 2. Apps (the defaults point at the local stack)
./gradlew :androidApp:installDebug                       # Android emulator (uses 10.0.2.2)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun        # Web, http://localhost:8080
open iosApp/iosApp.xcodeproj                             # iOS: run from Xcode
```

### Running on a physical iPhone

The phone reaches the local Supabase stack through your Mac's Bonjour name (`<LocalHostName>.local`, detected at build time; override it with `DEV_HOST=` in `local.properties`). Keep the phone and the Mac on the same Wi-Fi network and allow the "Local Network" prompt on first launch. Choose your team under *Signing & Capabilities* in Xcode, select the device and press Run.

To use a hosted Supabase project, add this to `local.properties`:

```
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon key>
```

and push the schema with `npx supabase link` + `npx supabase db push`.

### Demo accounts (password `password123`)

| Email            | Role   | Notes                                     |
|------------------|--------|-------------------------------------------|
| coach@demo.app   | Coach  | Templates, library, check-in questions    |
| anna@demo.app    | Client | Has workout/meal/supplement plans + check-ins |
| ben@demo.app     | Client | No plans yet                              |
| other@demo.app   | Coach  | Second coach (used by the RLS tests)      |

Invite code `WELCOME1` signs a new client up under the demo coach.

## How it works

- **Roles**: `profiles.role` is set at signup by the `handle_new_user` trigger. Clients join a coach with an invite code: it is checked by `check_invite()` before signup and redeemed atomically in the trigger, or later with `redeem_invite()`.
- **Templates vs. client plans**: a plan with `client_id = null` is a template. `assign_plan(kind, template, client)` deep-copies it as the client's single active plan.
- **Plan builders** send the whole tree to `save_workout_plan` / `save_meal_plan` / `save_supplement_plan`. Existing child ids are updated in place, so logs keep their references.
- **Security**: everything goes through RLS. Coaches see only their own clients, and clients read their own plans and write only their own logs and check-ins. A trigger stops clients from reviewing their own check-ins and stops coaches from editing client metrics.
- **Photos** are stored in the private `checkin-photos` bucket under `{client_id}/{checkin_id}/{pose}.jpg` and shown through signed URLs.

## Tests

```bash
./gradlew :composeApp:testAndroidHostTest   # commonTest unit tests (JVM)
npx supabase test db                        # database / RLS tests
```
