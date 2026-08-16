# ReproTrail Android

[![CI](https://github.com/sarimmehdi/reprotrail-android/actions/workflows/ci.yml/badge.svg)](https://github.com/sarimmehdi/reprotrail-android/actions/workflows/ci.yml)

Privacy-aware Android interaction capture for portable, text-based bug reproduction. ReproTrail records a semantic sequence of user actions, exports canonical JSON, and turns it into a repeatable [Maestro](https://maestro.mobile.dev/) flow through [ReproTrail CLI](https://github.com/sarimmehdi/reprotrail-cli).

> [!IMPORTANT]
> This repository is a source-level Android capture and hosted-upload alpha, not a published production SDK. It is intended for controlled internal testing. It has no automatic Gradle instrumentation, consent UI, or stable binary artifact yet.

## Milestone 4 Android status

The Android capture alpha now provides:

1. Explicit `start`, `pause`, `resume`, and `stop` recording lifecycle operations.
2. Tap, long-press, and single-pointer swipe capture without consuming host touch events.
3. Ordered View selectors using privacy-reviewed replay ID, Android resource ID, optionally allowlisted visible text or content description, and normalized-coordinate fallback.
4. Optional Compose `testTag` resolution in a separate adapter; the core runtime has no Compose dependency.
5. Durable Room storage with bounded sessions, actions, and asynchronous pending work.
6. Canonical local JSON export, trace deletion, startup capture policy, and a runtime privacy kill switch.
7. A private Koin graph that remains independent of the host application's no-DI, manual-DI, Hilt, or Koin ownership model.
8. Explicit authenticated upload through Retrofit and unique network-constrained WorkManager jobs with bounded exponential retry.
9. Durable Room upload lifecycle, attempt count, safe terminal reason, and successful-ingestion time.

The trace format is owned by [reprotrail-spec](https://github.com/sarimmehdi/reprotrail-spec). ReproTrail CLI validates an exported trace and converts supported actions into Maestro instructions; Maestro is not embedded in the recording library.

## Repository layout

| Path | Responsibility |
| --- | --- |
| `runtime/` | DI-neutral public facade, capture lifecycle, gesture classification, View target resolution, bounded persistence, and export |
| `runtime/domain/` | Persistence models and repository contract |
| `runtime/data/` | Room entities, DAOs, database, and repository implementation |
| `runtime/upload/domain/` | Framework-independent hosted-upload request, outcome, and credential contracts |
| `runtime/upload/data/` | Retrofit transport and backend status mapping |
| `runtime-compose/` | Optional, explicitly selected Compose `testTag` resolver |
| `app/` | Controlled Android Views fixture used for capture and replay acceptance |
| `build-logic/` | Generated conventions plus SDK dependency isolation |
| `scripts/` | Repeatable local and CI verification helpers |
| `tools/clean-android-skeleton-gradle-plugin/` | Pinned generator source as a Git submodule |

The project foundation and persistence layers were generated with [`io.github.sarimmehdi.clean-android-skeleton`](https://github.com/sarimmehdi/clean-android-skeleton-gradle-plugin). ReproTrail-specific behavior was then developed contract-first with focused tests.

## Build and verify

Prerequisites:

- JDK 21
- Android SDK platform 37.0
- Git submodules
- an emulator or device for connected Room and Compose tests

Run the CI gate:

```shell
git clone --recurse-submodules git@github.com:sarimmehdi/reprotrail-android.git
cd reprotrail-android
./gradlew test ktlintCheck detekt :runtime:assembleRelease :runtime-compose:assembleRelease :app:assembleDebug
./scripts/verify-runtime-dependency-isolation.sh
```

With a device connected, also run:

```shell
./gradlew :runtime:data:connectedDebugAndroidTest :runtime-compose:connectedDebugAndroidTest
```

## Core integration

The alpha is not available from a Maven repository yet. Within this repository, a Views host depends on `:runtime`; a Compose host depends on `:runtime-compose`, which exposes the core API transitively.

Create one recorder for the lifecycle scope that owns the trace. Configuration defaults retain 10 sessions, accept 500 actions per session, and buffer up to 64 pending actions without blocking the UI thread:

```kotlin
val recorder = ReproTrail.create(
    context = applicationContext,
    configuration = ReproTrailConfig(
        policyVersion = "internal-test-v1",
        storage = ReproTrailStorageConfig(
            maxRetainedSessions = 10,
            maxActionsPerSession = 500,
            maxPendingActions = 64,
        ),
    ),
)
```

Recording is explicitly opt-in and lifecycle calls are suspending:

```kotlin
val sessionId = recorder.startRecording()
recorder.pauseRecording()
recorder.resumeRecording()
recorder.stopRecording()
val traceFile = recorder.exportLatestTrace()
```

Forward every Activity touch event before normal dispatch. ReproTrail observes the event; it neither consumes nor redispatches it:

```kotlin
override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    recorder.captureTouchEvent(this, event)
    return super.dispatchTouchEvent(event)
}
```

Close the facade when its owner is permanently released. Stop an active session first if it must be exportable:

```kotlin
recorder.close()
```

## Hosted upload

Hosted upload is opt-in and explicit. Configure a project, HTTPS backend, and host-owned credential provider when creating the recorder:

```kotlin
val recorder = ReproTrail.create(
    applicationContext,
    ReproTrailConfig(
        policyVersion = "internal-test-v1",
        upload = ReproTrailUploadConfig(
            baseUrl = "https://reprotrail.example.com/",
            projectId = projectId,
            credentialProvider = ReproTrailIngestCredentialProvider {
                credentialStore.currentIngestToken()
            },
        ),
    ),
)
```

After stopping a session, enqueue the newest completed trace and optionally inspect its durable state:

```kotlin
val ticket = recorder.enqueueLatestTraceUpload()
val status = recorder.latestTraceUploadStatus()
```

The session UUID is sent as `Idempotency-Key`. A backend `201` and an identical retry returning `200` both complete successfully. Authentication, validation, conflict, and payload-size responses stop permanently; throttling, I/O failure, and server failure retry exponentially from WorkManager's 10-second minimum. The default is five total attempts and the allowed configuration range is one through ten.

The raw credential is requested only when an HTTP attempt starts. It is never written to Room, WorkManager input/output, trace JSON, or ReproTrail logs. WorkManager data contains only project ID, session ID, and maximum attempt count; trace content is reconstructed from the private Room database immediately before transport.

HTTPS is mandatory by default. `allowInsecureHttp = true` exists only for controlled local development and does not override Android's own cleartext-network policy.

### Process recreation requirement

WorkManager persists requests across process death and reboot, but intentionally cannot persist a credential provider. An upload-enabled `ReproTrail` instance for that project must therefore be recreated from `Application.onCreate` before a pending worker runs. If the graph is temporarily unavailable, the worker consumes a bounded retry without reading or persisting a credential. Only one live recorder may register the same project ID.

Use an application-owned recorder for hosted upload. Activity-retained ownership remains appropriate for capture-only flows, but closing an upload-enabled recorder unregisters its in-memory credential bridge; pending work can continue only after the application recreates it.

The sample app provides a complete Views fixture in [`MainActivity.kt`](app/src/main/java/dev/reprotrail/sample/MainActivity.kt).

## Stable selectors

Assign a stable replay ID only to Views whose identity is safe to record:

```kotlin
ReproTrail.setReplayId(checkoutButton, "checkout.submit")
```

By default, visible text and content descriptions are never captured. An exact value is eligible only when the same string is deliberately included in the recorder's allowlist:

```kotlin
ReproTrailPrivacyConfig(
    visibleSelectorAllowlist = setOf("Continue"),
)
```

This is an exact-value policy, not a pattern or broad view-tree scrape. Typed input remains excluded.

### Optional Compose support

Add the adapter only in a Compose host, attach a stable `Modifier.testTag`, and register its resolver when creating the recorder:

```kotlin
val config = ReproTrailConfig(
    policyVersion = "internal-test-v1",
    targetResolvers = listOf(ReproTrailCompose.targetResolver()),
)

Button(
    onClick = ::submit,
    modifier = Modifier.testTag("checkout.submit"),
) { /* content */ }
```

The adapter reads exact test tags and node bounds from Compose semantics. It does not persist the node's displayed text, accessibility description, or full semantics tree.

## Host dependency injection

The public boundary consists of `ReproTrail.create`, configuration values, and the returned facade. A consumer never loads or accesses ReproTrail's internal Koin module.

### No DI framework

Create the facade directly in an Android owner and close it with that owner. For hosted upload, implement `ReproTrailIngestCredentialProvider` with an application-owned secure credential store and create the recorder from `Application.onCreate`.

### Manual DI

Construct the facade in the application's composition root and pass only `ReproTrail` to consumers:

```kotlin
class AppContainer(context: Context) : AutoCloseable {
    val reproTrail = ReproTrail.create(context, reproTrailConfig)

    override fun close() = reproTrail.close()
}
```

The same composition root can construct the credential provider and pass it only through `ReproTrailUploadConfig`; no internal ReproTrail type is exposed.

### Hilt

Provide the public facade from a host-owned scope. An Activity-retained scope is a useful default when recording is owned by an Activity flow:

```kotlin
@Module
@InstallIn(ActivityRetainedComponent::class)
object ReproTrailModule {
    @Provides
    @ActivityRetainedScoped
    fun provideReproTrail(
        @ApplicationContext context: Context,
    ): ReproTrail = ReproTrail.create(context, reproTrailConfig)
}
```

Choose a different host scope if the recording lifecycle differs, and arrange explicit `close()` ownership. Do not expose or bind internal runtime classes.

For hosted upload, bind `ReproTrailIngestCredentialProvider` and the recorder in `SingletonComponent`, then inject or otherwise request the recorder during `Application.onCreate` so construction establishes the worker bridge before pending work runs. The provider may delegate to a Hilt-owned secure credential repository; ReproTrail does not access the Hilt graph directly.

### Koin

A Koin host may bind the public facade in its own graph:

```kotlin
val hostModule = module {
    single { ReproTrail.create(androidContext(), reproTrailConfig) }
}
```

A Koin host can bind `ReproTrailIngestCredentialProvider` in its own application graph and pass `get()` into `ReproTrailUploadConfig`. The consuming graph and ReproTrail's private `koinApplication` remain separate; the same approach works when the host uses Hilt, manual DI, or no DI framework.

Each recorder internally uses `koinApplication`, following Koin's [context-isolation guidance](https://insert-koin.io/docs/reference/koin-core/context-isolation/). It does not start, read, stop, or mutate Koin's global context. Tests cover all four host models, independent recorder instances, and shutdown while a host Koin graph remains usable.

The runtime uses Koin Embedded 3.5.6, which relocates `org.koin.*` to
`embedded.koin.*`. The production runtime therefore exposes no standard Koin
artifact or Koin type to a consuming application. Host-compatibility tests use
standard Koin 4.2.2 alongside the relocated runtime and prove that the host
global graph remains independent.

Koin Embedded is still an upstream beta. Source builds resolve only its two core
modules from Kotzilla's dedicated repository through an exclusive Gradle
content filter. Release publication remains blocked until ReproTrail publishes
or bundles a reviewed relocated artifact so consumers do not need to add that
third-party repository themselves.

## Persistence, export, and deletion

Room stores sessions and actions in the app-private `reprotrail.db` database. Starting a session enforces the configured session retention limit. Action persistence is asynchronous and bounded; excess queued or per-session actions are dropped and accounted for in the stored session rather than growing memory indefinitely.

`stopRecording()` drains earlier queued actions before marking the session complete. `exportLatestTrace()` exports the newest completed session that contains an eligible action to `reprotrail/latest-trace.json` under app-specific external files storage, falling back to internal files storage. Each export replaces the previous file and needs no broad storage permission.

For the sample package, the usual debug path is:

```text
/sdcard/Android/data/dev.reprotrail.sample/files/reprotrail/latest-trace.json
```

Delete retained sessions only while idle:

```kotlin
recorder.deleteAllTraces()
```

After pulling an export, validate and generate or run a Maestro flow:

```shell
reprotrail-cli validate latest-trace.json
reprotrail-cli generate-maestro latest-trace.json --output replay.yaml
reprotrail-cli replay latest-trace.json --repeat 3
```

## Privacy and safety boundary

The alpha records structural replay metadata:

- action order and monotonic time offset;
- gesture duration and normalized swipe endpoints where applicable;
- explicitly assigned replay ID or Compose test tag;
- Android resource ID when present;
- allowlisted text or content description only when explicitly configured;
- target bounds and normalized coordinate fallback;
- package, display, locale, UI mode, font scale, API level, and host policy version.

It does not capture typed input, screenshots, arbitrary View tags, full accessibility or Compose trees, authentication tokens, backend response bodies, or screen video.

Set `captureEnabledAtStartup = false` when policy must enable recording later. Calling `setCaptureEnabled(false)` immediately pauses an active session. Re-enabling capture does not silently resume it; the host must call `resumeRecording()` explicitly.

Do not enable this alpha for production users. A production rollout still requires explicit consent and activation policy, encryption and authenticated upload, deletion propagation, abuse review, and published retention guarantees. Never attach real user traces to public GitHub issues; report security concerns through GitHub's private vulnerability reporting.

## Known limitations

- Activity touch forwarding is explicit; there is no automatic Gradle bytecode instrumentation yet.
- Only primary-pointer tap, long press, and swipe gestures are modeled; multi-touch cancels the current gesture.
- Back presses, keyboard/IME actions, typed text, and semantic scroll commands are not captured.
- An interrupted active session remains durable in Room but is not automatically resumed or exportable as a completed trace after process restart.
- Export always targets the newest eligible completed session and replaces the previous local file.
- Upload currently targets only the newest completed trace and requires an application-owned recorder to recreate the in-memory credential bridge before pending workers run.
- There is no Android API for listing every retained trace or cancelling/deleting one pending upload yet.
- The hosted backend's developer read/download/delete, retention, audit, reconciliation, provisioning, and deployment slices remain incomplete.
- Environment parity is recorded but not enforced before replay.
- The runtime is source-only and has no binary-compatibility guarantee.
- Relocated Koin is isolated from host Koin, but its publication packaging is
  not complete and Koin Embedded remains an upstream beta.

## Development approach

This project follows test-driven development. Each behavior begins with a focused executable expectation, is implemented minimally, and is committed as a small independently reviewable unit. The repository-wide gate is:

```shell
./gradlew test ktlintCheck detekt :runtime:assembleRelease :runtime-compose:assembleRelease :app:assembleDebug
./scripts/verify-runtime-dependency-isolation.sh
```

Connected Room and Compose tests run on a local emulator before a milestone is declared complete.

## Related repositories

- [reprotrail-spec](https://github.com/sarimmehdi/reprotrail-spec) — versioned trace schema, fixtures, and architecture decisions
- [reprotrail-cli](https://github.com/sarimmehdi/reprotrail-cli) — validation, Maestro flow generation, and repeat orchestration
- [clean-android-skeleton-gradle-plugin](https://github.com/sarimmehdi/clean-android-skeleton-gradle-plugin) — reusable project generator used to bootstrap this repository

## License

Apache License 2.0. See [`LICENSE`](LICENSE).
