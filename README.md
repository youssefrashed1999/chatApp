# ChatApp

A real-time chat app for Android built with Kotlin, Jetpack Compose, and Supabase. Users are identified by their device ID — no sign-up or login required. All users share a single chat room with live message updates.

## Features

- **Real-time messaging** — new messages arrive instantly via Supabase Realtime (postgres change stream on the `messages` table).
- **Text + media messages** — send text, up to 10 images, or a voice note per message.
- **Reliable sending** — messages are sent in the background via WorkManager, so sends survive process death and device restarts.
- **Send status UI** — every outgoing message shows `Sending` → `Sent` → `Failed`, with tap-to-retry and tap-to-cancel.
- **Notifications** — ongoing notification while media uploads (with a Cancel action) and a failure notification (with a Retry action).
- **Offline-friendly history** — message history is paged with Paging 3; a connectivity observer refreshes missed messages on reconnect.
- **Profile** — username + profile image, stored in Supabase; identity is tied to the device ID (`Settings.Secure.ANDROID_ID`).
- **Full-screen image viewer** — tap an image in chat to swipe through all images of that message.

## Tech Stack

- **Language/UI**: Kotlin, Jetpack Compose, Material 3
- **Architecture**: Clean Architecture + MVI (unidirectional data flow, immutable `StateFlow` state, intent-based events)
- **Async**: Coroutines + Flow
- **DI**: Hilt (including `HiltWorker` for WorkManager)
- **Backend**: Supabase — Postgrest (database), Realtime (live inserts), Storage (media)
- **Background work**: WorkManager
- **Paging**: Paging 3
- **Images**: Coil
- **Serialization**: kotlinx.serialization

## Project Structure

```
app/src/main/java/com/example/chatapp/
├── core/                     # Shared building blocks
│   ├── di/                   # Hilt modules (Supabase client, repository bindings)
│   ├── error/                # DomainException hierarchy + Throwable → user message mapping
│   ├── network/              # ConnectivityObserver
│   └── session/              # CurrentUserProvider
├── features/
│   ├── chat/
│   │   ├── data/             # ChatDatasource (Supabase), ChatRepositoryImpl,
│   │   │                     # MessagesPagingSource, SendMessageWorker,
│   │   │                     # MessageSendSchedulerImpl, MessageSendNotifier,
│   │   │                     # retry/cancel BroadcastReceivers
│   │   ├── domain/           # Message/UserProfile entities, repository & scheduler
│   │   │                     # interfaces, use cases
│   │   └── presentation/     # ChatScreen (Compose), ChatViewModel, ChatState, ChatIntent
│   └── users/
│       ├── data/             # UserDataSource, UserRepositoryImpl (SharedPreferences cache)
│       ├── domain/           # UserProfile entity, UserRepository, use cases
│       └── presentation/     # Onboarding + CreateProfile screens and ViewModels
└── ui/theme/                 # Material 3 theme
```

Each feature follows `data → domain → presentation`. The presentation layer uses MVI: UI sends `Intent`s to the ViewModel, which exposes a single immutable `State`.

## App Flow

```
Onboarding ──user exists──▶ Chat
    │
    └──no user──▶ CreateProfile ──registered──▶ Chat
```

- **Onboarding** verifies the device ID against the `users` table on every cold start (`forceRefresh = true`), so a profile deleted server-side can't be bypassed by the local cache.
- **CreateProfile** collects a username and an optional profile image, uploads the image to `chat-media/profiles/<deviceId>.jpg`, and inserts the `users` row.
- **Chat** is a single shared room: paged history + live inserts + outgoing-message status overlay.

## Architecture Notes

- **Message sending pipeline**: `ChatViewModel` → `SendMessageUseCase` → `MessageSendScheduler` → `WorkManager` unique work (`send_message_<id>`) → `SendMessageWorker` → uploads any `content://` media to Supabase Storage → inserts the row via Postgrest → dismisses the notification. Failures surface a notification with Retry/Cancel `BroadcastReceiver`s that re-enqueue or cancel the work.
- **Live updates**: `ChatDatasource.observeMessageInserts()` opens a Realtime channel (`messages-inserts`) filtered on `INSERT` events; `ChatViewModel` merges live messages with the `PagingData` history stream so new messages appear without invalidating the pager.
- **Catch-up on reconnect**: `ConnectivityObserver` emits network changes; when connectivity returns, `RefreshMessagesUseCase` fetches everything newer than the newest known `created_at` and merges it into the list — covering messages missed while the websocket was down.
- **User session**: `UserRepositoryImpl` caches the profile in `SharedPreferences`; onboarding always revalidates against the backend (`forceRefresh`) so deleted accounts can't slip through a stale cache.
- **Error handling**: data sources throw platform exceptions; repositories map them to a `DomainException` hierarchy (`toDomainException()`), and the UI maps those to string resources (`toUserMessage()`).

## Message Lifecycle

```
Send intent ──▶ SENDING (optimistic tail item + WorkManager enqueued)
                  │
                  ├─ worker succeeds ──▶ SENT
                  │
                  ├─ worker fails ─────▶ FAILED ──tap──▶ Retry (re-enqueue)
                  │
                  └─ user cancels ─────▶ FAILED (via Cancel intent or notification action)
```

- `SendStatus` lives on the `Message` entity (`SENDING` / `SENT` / `FAILED`).
- `ObserveMessageStatusUseCase` maps `WorkInfo.State` to `SendStatus`, so the UI status is driven by the actual work state — including after process death.
- Media messages keep local `content://` URIs until the worker replaces them with uploaded `https://` URLs, so the bubble shows the picked images immediately.

## Design Decisions

- **Device-ID identity** — the task calls for anonymous usage; `Settings.Secure.ANDROID_ID` is stable per app install and needs no account system. The trade-off (ID changes on factory reset / app reinstall on some devices) is acceptable for a demo chat.
- **Photo Picker over storage permissions** — `PickMultipleVisualMedia` grants per-URI access, so the app needs zero `READ_MEDIA_*`/`READ_EXTERNAL_STORAGE` permissions and is fully scoped-storage compliant.
- **WorkManager for sends** — guaranteed execution across process death and reboots, unique-work names make retry idempotent, and `WorkInfo` doubles as the status source of truth.
- **Denormalized sender fields** — `username`/`profile_image_url` are copied onto each `messages` row so history renders without joins or extra fetches.
- **Single chat room** — one `messages` table, no room scoping, per the task requirements.

## Supabase Setup

1. Create a project at [supabase.com](https://supabase.com).
2. Create the tables (in the `public` schema):

```sql
create table users (
    device_id text primary key,
    username text not null,
    profile_image_url text
);

create table messages (
    id text primary key,
    device_id text not null references users(device_id),
    media_type text not null,
    content text,
    media_urls text[],
    created_at timestamptz not null default now(),
    username text,
    profile_image_url text
);
```

3. Enable Realtime for the `messages` table:

```sql
alter publication supabase_realtime add table messages;
```

4. Create a **public** Storage bucket named `chat-media` (profile images go under `profiles/`, message media under `messages/`).
5. Adjust Row Level Security policies to your needs (the app uses the anon key, so permissive read/insert policies are needed for a demo).

## Configuration

The app reads its Supabase credentials from `local.properties` (gitignored) and injects them via `BuildConfig`:

```properties
# local.properties (project root)
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-anon-key
```

These are wired in `app/build.gradle.kts` (`buildConfigField`) and consumed in `core/di/AppModule.kt` when creating the `SupabaseClient`.

## Build & Run

Requirements: Android Studio (latest stable), JDK 11+, Android SDK 36, min device/emulator API 26.

```bash
./gradlew :app:assembleDebug
```

Or open the project in Android Studio and press **Run**.

## Testing

Unit tests live in `app/src/test/` and cover the critical components:

- `ChatRepositoryImplTest` — repository mapping and error propagation
- `MessagesPagingSourceTest` — cursor-based paging behavior
- `SendMessageWorkerTest` — background send, media upload, failure/notification paths
- `UserRepositoryImplTest` — session caching and `forceRefresh` revalidation
- `OnboardingViewModelTest` — onboarding navigation and error states

Run them with:

```bash
./gradlew :app:testDebugUnitTest
```

Test libraries: JUnit4, MockK, kotlinx-coroutines-test, Turbine, androidx-work-testing.

## Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE` — networking and connectivity observation.
- `POST_NOTIFICATIONS` — requested at runtime on Android 13+ for send-status notifications.
- `RECORD_AUDIO` — requested at runtime when the user starts a voice recording.
- **No storage permissions** — images are picked via the Photo Picker, which grants per-URI access (scoped storage friendly).

## Notifications

- Channel: `message_send` ("Message sending").
- **Ongoing** notification while a media message uploads — **Cancel** action → `MessageSendCancelReceiver` cancels the WorkManager task.
- **Failure** notification when a send fails — **Retry** action → `MessageSendRetryReceiver` re-enqueues the same message payload.

## Troubleshooting

- **Messages never send / onboarding fails** — check `SUPABASE_URL`/`SUPABASE_KEY` in `local.properties`, then resync Gradle so `BuildConfig` regenerates.
- **History loads but new messages don't appear live** — Realtime isn't enabled: run `alter publication supabase_realtime add table messages;`.
- **Images fail to upload or don't render** — the `chat-media` bucket must exist and be public (or have matching policies).
- **"Permission denied" / empty query results** — RLS is enabled on the tables without permissive policies for the anon key.
- **No notifications on Android 13+** — the `POST_NOTIFICATIONS` runtime permission was denied; re-enable it in system settings.

## Audio Messages

### Recording

- Tap the mic/record icon in the input bar to start recording. The icon switches to a stop button while recording.
- `MediaRecorder` captures AAC audio inside an M4A container and writes it to the app cache under `audio_messages/audio_<uuid>.m4a`.
- `RECORD_AUDIO` is requested at runtime on Android 6+ before recording starts.
- Any currently playing voice note is paused before recording begins.
- When the stop button is tapped, `ChatViewModel.stopRecording()` checks a minimum duration (`MIN_RECORDING_MS`); very short recordings are discarded. Otherwise the cache file is wrapped in a `content://` URI via `FileProvider` and sent like any other media message.

### Sending

Audio messages use the same `WorkManager` pipeline as images:

```
recorded file (content://) ──▶ SendMessageWorker ──▶ upload to chat-media/messages/<messageId>/... ──▶ messages row with media_type = audio
```

`SendMessageWorker` reads the `content://` URI, uploads the bytes through `ChatRepository.uploadImage()` (used for all message media), and replaces the local URI with the public Supabase Storage URL before inserting the row.

### Playback

- Each voice note has its own `MediaPlayer`.
- Players live in `ChatViewModel._audioPlayers`, a `Map<String, MediaPlayer>` keyed by `message.id`. A public `audioPlayers: StateFlow<Map<String, MediaPlayer>>` is collected by `ChatScreen` and passed down to `AudioMessagePlayer`.
- When a note is tapped:
  - If no player exists for that id, `ChatViewModel.prepareAudioPlayer()` creates a new `MediaPlayer`, calls `prepareAsync()`, and on prepared stores it in the map.
  - `prepareAudioPlayer()` pauses every other active player before calling `player.start()`.
  - If a player already exists, `toggleAudioPlayback()` pauses it or starts it (again pausing other notes first).
- The `AudioMessagePlayer` composable looks up `audioPlayers[message.id]` and keys its `remember`/`LaunchedEffect` state by `message.id` and the player reference, so the play/pause icon, progress, and duration are always bound to the correct message — even while `LazyColumn` recycles rows during the initial page load.
- On completion the player seeks back to 0 so the note can be replayed. On error the player is released and removed from the map.
- All active players are released when the `ViewModel` is cleared.

## Known Limitations & Roadmap

- **FCM push notifications** — `firebase-messaging` is on the classpath but no `FirebaseMessagingService`/token upload is wired yet (bonus item).
- **Instrumentation/UI tests** — Compose test dependencies are declared; only unit tests are currently written (bonus item).
- **Message edits/deletes, multiple rooms, auth** — out of scope for the current task.

## Git Flow

The repository follows Git Flow: `master` for releases, `develop` for integration, and `feature/*` branches for individual features.
