# ReproTrail Android

[![CI](https://github.com/sarimmehdi/reprotrail-android/actions/workflows/ci.yml/badge.svg)](https://github.com/sarimmehdi/reprotrail-android/actions/workflows/ci.yml)

Privacy-aware Android interaction capture for portable, text-based bug reproduction. ReproTrail records a semantic sequence of user actions, exports the sequence as canonical JSON, and turns it into a repeatable [Maestro](https://maestro.mobile.dev/) flow through [ReproTrail CLI](https://github.com/sarimmehdi/reprotrail-cli).

> [!IMPORTANT]
> This repository is an internal-test prototype, not a published production SDK. Milestone 2 supports taps on classic Android `View` hierarchies, explicit `Activity` event forwarding, in-memory capture, and local JSON export. It does not yet provide Compose capture, Room persistence, upload, consent UI, automatic Gradle instrumentation, or a stable artifact.

## Milestone 2 status

The first vertical slice is complete:

1. Observe a primary-pointer tap without consuming it.
2. Resolve the deepest visible `View` under the tap.
3. Record ordered selectors: privacy-reviewed replay ID, Android resource ID, then normalized coordinates.
4. Export a schema-compatible JSON trace to app-specific storage.
5. Validate and convert the trace with ReproTrail CLI.
6. Replay the generated flow repeatedly with Maestro.

The path has been exercised on an Android emulator from a real tap through three successful Maestro replay runs. The portable format is owned by [reprotrail-spec](https://github.com/sarimmehdi/reprotrail-spec).

## Repository layout

| Path | Responsibility |
| --- | --- |
| `runtime/` | DI-neutral public facade, tap classification, semantic target resolution, trace encoding, and local export |
| `app/` | Controlled Android Views fixture used for capture and replay acceptance |
| `build-logic/` | Generated convention plugins |
| `nav/`, `ui/`, `utils/` | Generated application-foundation modules reserved for later milestones |
| `tools/clean-android-skeleton-gradle-plugin/` | Pinned generator source as a Git submodule |

The repository foundation was generated with [`com.sarim.clean-android-skeleton`](https://github.com/sarimmehdi/clean-android-skeleton-gradle-plugin) using its minimal app-shell profile.

## Build and verify

Prerequisites:

- JDK 21
- Android SDK platform 37.0
- Git submodules

Clone and run the same gate as CI:

```shell
git clone --recurse-submodules git@github.com:sarimmehdi/reprotrail-android.git
cd reprotrail-android
./gradlew test ktlintCheck detekt :app:assembleDebug
```

## Current integration contract

Create one recorder instance with application-safe context and a host-owned privacy policy version:

```kotlin
private lateinit var reproTrail: ReproTrail

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    reproTrail = ReproTrail.create(
        context = this,
        configuration = ReproTrailConfig(policyVersion = "internal-test-v1"),
    )
}
```

Assign stable identifiers only to controls that are safe and useful to replay:

```kotlin
ReproTrail.setReplayId(checkoutButton, "checkout.submit")
```

Forward each `Activity` touch event before normal dispatch. The recorder observes the event; it neither consumes nor redispatches it:

```kotlin
override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    reproTrail.captureTouchEvent(this, event)
    return super.dispatchTouchEvent(event)
}
```

Calling capture before `super.dispatchTouchEvent` also makes the completed `ACTION_UP` available when a click listener immediately exports the trace:

```kotlin
val file = reproTrail.exportLatestTrace()
```

Close the instance with its owner:

```kotlin
override fun onDestroy() {
    reproTrail.close()
    super.onDestroy()
}
```

The sample app provides the complete working fixture in [`MainActivity.kt`](app/src/main/java/dev/reprotrail/sample/MainActivity.kt).

## Host dependency injection

The public API is deliberately DI-neutral. All host configurations call `ReproTrail.create`; the host decides only how to own and distribute the returned facade.

| Consuming app | Recommended integration |
| --- | --- |
| No DI framework | Create and retain the facade in the appropriate Android owner. |
| Manual DI | Construct the facade in the composition root and pass it to consumers. |
| Hilt/Dagger | Provide the facade from a host module with the required lifecycle scope. Do not expose or bind internal runtime classes. |
| Koin | Bind the facade in the host graph if desired. Do not load ReproTrail's private module into the host graph. |

Internally, each recorder owns a private Koin application created with `koinApplication`, following Koin's [context-isolation guidance](https://insert-koin.io/docs/reference/koin-core/context-isolation/). It does not start, read, stop, or mutate Koin's global context. Tests cover a host with no Koin, a running host Koin graph, independent recorder instances, and recorder shutdown.

Isolation is not the same as dependency relocation. The prototype currently has a normal `koin-core` dependency, so artifact publication is intentionally blocked until compatibility testing determines whether to relocate Koin, adopt the currently beta [Koin Embedded distribution](https://insert-koin.io/docs/support/embedded/), or remove internal DI. A consuming app is therefore not yet promised freedom to use an arbitrary Koin version.

## Export and replay

`exportLatestTrace()` writes `reprotrail/latest-trace.json` under the app-specific external files directory, falling back to internal files storage when external app storage is unavailable. The prototype needs no broad storage permission.

For the sample package, a debug export is available through ADB at:

```text
/sdcard/Android/data/dev.reprotrail.sample/files/reprotrail/latest-trace.json
```

After pulling the file, use ReproTrail CLI to validate and generate a Maestro flow:

```shell
reprotrail-cli validate latest-trace.json
reprotrail-cli generate latest-trace.json --output replay.yaml
reprotrail-cli replay latest-trace.json --repeat 3
```

The CLI prefers an Android resource ID that Maestro can resolve and retains the replay ID and normalized coordinate fallback in the portable trace.

## Privacy and safety boundary

Milestone 2 intentionally captures only structural replay metadata:

- tap order and time offset;
- stable replay ID when explicitly assigned by the host;
- Android resource ID when present;
- target bounds and normalized coordinates;
- package, display, locale, UI mode, font scale, API level, and policy version.

It does not capture visible text, content descriptions, typed input, screenshots, accessibility dumps, authentication tokens, network payloads, or arbitrary view tags. Do not enable this prototype for production users. Production rollout requires explicit consent and activation policy, redaction, retention limits, encryption, authenticated upload, deletion controls, and abuse review.

Do not attach real user traces to public GitHub issues. Report security concerns through GitHub's private vulnerability reporting for this repository.

## Known limitations

- Android Views only; Jetpack Compose semantics are not captured yet.
- One explicitly wired `Activity`; no automatic application-wide instrumentation yet.
- Primary-pointer taps only; no text input, scroll, swipe, long press, multi-touch, back, or navigation events.
- Captured actions live in memory until export.
- The export filename is replaced by the latest session.
- No backend transport, queue, retry, or Room database yet.
- Environment parity is recorded but not enforced before replay.
- The runtime is source-only and has no binary-compatibility guarantee.

## Development approach

This project follows test-driven development. Each behavior starts with a failing focused test, is implemented minimally, and is committed as a small independently reviewable unit. The repository-wide gate is:

```shell
./gradlew test ktlintCheck detekt :app:assembleDebug
```

## Related repositories

- [reprotrail-spec](https://github.com/sarimmehdi/reprotrail-spec) — versioned trace schema, fixtures, and architecture decisions
- [reprotrail-cli](https://github.com/sarimmehdi/reprotrail-cli) — validation, Maestro flow generation, and repeat orchestration
- [clean-android-skeleton-gradle-plugin](https://github.com/sarimmehdi/clean-android-skeleton-gradle-plugin) — reusable project generator used to bootstrap this repository

## License

Apache License 2.0. See [`LICENSE`](LICENSE).
