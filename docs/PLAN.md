# CoachApp: plan and status

A Kahunas-style coaching platform. Coaches give each client personalised **workout**, **meal** and **supplement** plans and review **weekly check-ins**. Clients follow the plans, log their training and nutrition, and send check-ins with progress photos.

*Last updated: 2026-10-10*

---

## 1. Status at a glance

| Area | Status |
|---|---|
| Backend (Supabase: schema, RLS, functions, storage, seed) | ✅ Done, with 16 pgTAP RLS tests passing |
| Auth & onboarding (coach signup, client invite codes, join coach) | ✅ Done, tested on Android |
| Coach: clients, invites, libraries, plan builders, check-in review | ✅ Done |
| Client: today, workout session, plans, check-in with photos, history | ✅ Done |
| Charts: activity heatmap, progress rings, macro donut | ✅ Done, not yet checked visually |
| Design system (palette, light/dark, shapes) | ✅ Done |
| Android app | ✅ Builds and runs (emulator tested) |
| Web (wasmJs) | ✅ Builds and runs on the dev server; layout not checked visually |
| iOS: native SwiftUI shell around the Kotlin screens | 🟡 Kotlin side builds; Swift side being tested in Xcode |
| Unit tests (commonTest) | ✅ 19 passing |

---

## 2. Tech stack

- **Kotlin Multiplatform 2.4** + **Compose Multiplatform 1.12** (Material 3), targeting Android, iOS (arm64 and simulator) and Web (wasmJs).
- **AGP 9.4 / Gradle 9.8**. AGP 9 forbids `kotlin-multiplatform` and `com.android.application` in one module, so there's a separate `androidApp` module.
- **Supabase** (supabase-kt 3.8: Auth, Postgrest, Storage) over Ktor 3 (OkHttp, Darwin and Js engines).
- **Koin 4.2** for DI, **Navigation Compose 2.9** (type-safe routes) on Android and web.
- **Coil 3** for images, **FileKit 0.16** for the photo picker, **kotlinx-datetime/serialization**.
- **SwiftUI** (iOS 17+) shell on iOS: native TabView and NavigationStack, with Liquid Glass bars on iOS 26 and later.

---

## 3. Architecture

```
composeApp/                  Shared KMP module
  commonMain/ch/coachapp/
    core/                    Supabase client, apiCall/Result errors, Dates, formatting
      ui/                    Theme + Palette + ChartColors, shared components,
                             ScreenChrome (Material vs native iOS bars), Charts,
                             ActivityCard, MacroDonut, CheckinSummary, AdaptiveScaffold
    data/model               @Serializable models (snake_case via @SerialName)
    data/repository          Interfaces + Supabase implementations
                             (Auth, Coach, Library, Plan, Log, Checkin)
    domain/                  Plan drafts (builder state → JSON), Macros, Activity levels/streaks
    feature/auth|coach|client  Screen (stateless) + ViewModel (StateFlow) + route composable
    navigation/AppRoot       Role-based root for Android/web
    di/                      Koin modules
  iosMain/ch/coachapp/ios/   Bridge for the SwiftUI shell (see §3.2)
androidApp/                  Android application (MainActivity → App())
iosApp/                      Xcode project: SwiftUI shell
supabase/                    migrations/, seed.sql, tests/
docs/PLAN.md                 This file
```

**Screen pattern:** each screen is a stateless `XxxScreen(state, callbacks)`, an `XxxViewModel` exposing `StateFlow<UiState>`, and an `XxxRoute` or `XxxDestination` composable that wires up `koinViewModel()`. Repositories return `Result<T>` with readable error messages.

### 3.1 Navigation per platform

- **Android and web:** `App()` → `AppRoot` picks the coach or client graph by role. Each graph is a Compose `NavHost` with an `AdaptiveScaffold`: a bottom bar on phones, a navigation rail at 840dp and wider.
- **iOS:** SwiftUI owns the tabs, navigation, titles, swipe-back and toolbar. Every screen is a separate `ComposeUIViewController`.

### 3.2 iOS bridge (SwiftUI shell)

| Kotlin (`iosMain/ios/`) | Swift (`iosApp/iosApp/`) | Purpose |
|---|---|---|
| `setUpApp()` | `iOSApp.init` | Starts Koin once (global context) |
| `SessionBridge`, `SessionSnapshot`, `SessionKind` | `SessionModel`, `RootView` | Auth state → which shell to show |
| `ScreenHost` (implements `NativeChrome`) | `ScreenChromeModel`, `ComposeScreen` | Screen title and bar buttons → native toolbar |
| `IosScreensKt.*Screen(host, callbacks…)` | `CoachShell`, `ClientShell` | One factory per screen; Swift handles push/pop |

- Shared top bars are described as data (`ScreenTopBar` / `BarAction`). Material draws them on Android and web; on iOS they're reported to SwiftUI.
- The primary action is a FAB on Material and a toolbar button on iOS.
- Screens with unsaved input (the workout session) can intercept Back so the user confirms first.
- Kotlin/Native exports enum entries in lower case (`.signedout`, `.joincoach`) and renames `init…` functions to `doInit…`. Avoid Kotlin type names that clash with Combine or SwiftUI (`Subscription` was renamed to `SessionObservation`).

---

## 4. Backend

### 4.1 Data model (Postgres)

- **Users:** `profiles` (role `coach|client`, `coach_id`) and `invites` (code, expiry, used_by).
- **Libraries:** `exercises`, `foods` (macros per 100 g) and `supplements`. A null `coach_id` means a global item.
- **Plans** (a null `client_id` means a template):
  - `workout_plans` → `workout_days` → `workout_exercises`
  - `meal_plans` → `meals` → `meal_items`
  - `supplement_plans` → `supplement_items`
  - Each client has one active plan per kind; a trigger deactivates the previous one.
- **Logs:** `workout_logs` → `set_logs`, `meal_logs` and `supplement_logs` (one row per item per day).
- **Check-ins:** `checkin_templates` (questions as jsonb), `checkins` (metrics, answers, status, feedback) and `checkin_photos`.

### 4.2 Functions

| Function | Use |
|---|---|
| `handle_new_user` (trigger) | Creates the profile on signup and redeems `invite_code` from the signup metadata |
| `check_invite(code)` / `redeem_invite(code)` | Validates an invite before signup / joins a coach later |
| `save_workout_plan` / `save_meal_plan` / `save_supplement_plan(jsonb)` | Builders save the whole tree; existing child ids are kept so logs stay linked |
| `assign_plan(kind, template, client)` | Copies a template to the client as their active plan |
| `coach_client_overview` (view) | Coach dashboard: last check-in, pending reviews, last workout, 7-day adherence |
| `daily_activity(client, days)` | Per-day workouts, meals and supplements plus planned counts, for the heatmap |

### 4.3 Security

- **Row-level security** is on for every table:
  - Coaches see only their own clients, libraries and templates.
  - Clients read their own plans and write only their own logs and check-ins.
- **Check-in guard:** a trigger stops clients from reviewing their own check-ins, and stops coaches from changing a client's metrics.
- **Photos:** stored in the private `checkin-photos` bucket under `{client_id}/{checkin_id}/{pose}.jpg` and shown through signed URLs.

---

## 5. Design system

- **Palette:** System colours (Magenta, Blue, Green, Yellow and Red, each in 400 to 100 shades) plus Neutrals.
  - Light: background `#F8F8FF`, white cards, text `#0D0A2C` and `#615E83`, primary Magenta 400.
  - Dark (derived): background `#0D0A2C`, cards `#1A1740`, primary Magenta 300.
- **`ChartColors`** (via `LocalChartColors`):
  - Workouts magenta, meals green, supplements blue.
  - Protein blue, carbs yellow, fat red.
  - Heatmap levels follow the Magenta ramp.
- **Shapes:** large corners (cards 20–24dp).
- **Charts** (`core/ui/Charts.kt`):
  - `ProgressRing` and `StatRing`: animated rings with rounded ends.
  - `SegmentedRing` and `MacroDonut`: macro split by kcal.
  - `ActivityHeatmap`: GitHub-style grid. It fits as many weeks as the screen width allows, Monday is the first row, tapping a day shows details.
  - `ActivityCard`: heatmap plus 7-day completion, workouts in the last four weeks, and streak.
- **Heat level** (`domain/Activity.kt`): meal and supplement completion of 0, more than 0, 50% or more, or 85% or more gives level 0–3. A workout adds one level (minimum 2, maximum 4).
- **iOS:** the asset catalog colours `AccentColor`, `AppBackground` and `LaunchBackground` match the palette.

---

## 6. Features

### Coach
- Clients list: adherence ring, pending-review badge, last check-in and last workout, search.
- Invites: create a code (optional email), copy it, see used or expired codes, delete.
- Client detail tabs: Overview (this-week rings, activity heatmap, active plans), Workout, Meals, Supplements, Check-ins and Logs. Logs shows a 7-day table and sets per workout.
- Plan templates, plus builders for workouts (days and exercises, reorder), meals (live macro donut and target bars) and supplements (dosage and timing). Each builder can save a template or a client plan, and templates can be assigned to clients.
- Library: exercises, foods and supplements, with search and add/edit/delete. Global items are read-only.
- Check-in questions editor (scale, yes/no, number, text).
- Check-in review: metrics against the previous week, answers, photos side by side, feedback, mark reviewed.

### Client
- Today: progress rings, next workout with day picker, meals and supplements checklists (they update instantly and undo themselves if the save fails).
- Workout session: the coach's prescription, sets pre-filled from last time, confirmation before discarding.
- Plan: read-only workout, meals (macro donut) and supplements.
- Weekly check-in: weight, body fat, measurements, coach questions, front/side/back photos. A submitted check-in can be edited until the coach reviews it.
- History: activity heatmap, past check-ins with feedback, workout log.

---

## 7. Running and verifying

```bash
npx supabase start            # local backend (Docker); migrations + seed
npx supabase test db          # RLS tests
npx supabase db reset         # back to clean demo data

./gradlew :composeApp:testAndroidHostTest               # unit tests
./gradlew :androidApp:installDebug                      # Android (emulator uses 10.0.2.2)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun       # Web on :8080
open iosApp/iosApp.xcodeproj                            # iOS: Cmd+R in Xcode
```

- **Physical iPhone:** the build bakes in the Mac's Bonjour name (`<LocalHostName>.local`); override it with `DEV_HOST=` in `local.properties`. Keep the phone and Mac on the same Wi-Fi and allow Local Network access.
- **Hosted Supabase:** set `SUPABASE_URL` and `SUPABASE_ANON_KEY` in `local.properties`.
- **Demo accounts** (password `password123`): `coach@demo.app`, `anna@demo.app` (plans plus about 10 weeks of activity), `ben@demo.app`, `other@demo.app`. Invite code `WELCOME1`.

---

## 8. Known gaps

- [ ] **iOS shell:** finish the Xcode build and test on a device. Large titles don't collapse when scrolling, because Compose content isn't a `UIScrollView`.
- [ ] **Rotunda font:** waiting for the font files. Then wire them in through Compose resources and register them in the iOS `Info.plist` (`UIAppFonts`) for the native bars.
- [ ] **Wide screens:** no list-detail split yet (the client list and detail could sit side by side).
- [ ] **Number fields:** in the builders, clearing a required number field keeps the last valid value.
- [ ] **Coach client detail:** makes 9 requests to load; replace them with one aggregate function or view.
- [ ] **Previous sets:** `LogRepository.lastSets` scans the last 20 workout logs; use a query keyed by exercise.
- [ ] **Time zones:** activity days use the server's time zone (UTC), so late-evening logs can land on the next day. Store or pass the client's time zone.
- [ ] **Light-mode contrast:** check text links in Magenta 400.

---

## 9. Roadmap

### Phase 2: Engagement
- Coach-client chat (Supabase Realtime) with unread badges.
- Push notifications (FCM/APNs through an Edge Function): check-in due, feedback received, new plan.
- Progress charts: weight and measurements trend lines, check-in photo timeline, strength progress per exercise.
- Check-in reminders and a check-in day set by the coach.

### Phase 3: Business
- Stripe subscriptions and packages for coaches, and client billing.
- Branding: coach logo and colours (theme from the database), possibly white-label builds.
- Habits and daily tasks, automations (auto-assign plans, onboarding forms).
- Apple Health / Google Fit import (steps, weight, sleep).

### Phase 4: Scale
- Teams (several coaches), roles and permissions.
- Analytics dashboard for coaches.
- Optional split into a coach app (web or "Pro") and a client app, using the same code.
