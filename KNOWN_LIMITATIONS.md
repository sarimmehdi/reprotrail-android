# ReproTrail Android known limitations

This document describes limitations of the source-level production-beta work in
progress. It is not a waiver of the project-wide privacy or security budgets.
The affected version remains unreleased until the publication limitations below
are removed and the remaining entries are accepted in signed release notes.

## Host touch integration is explicit

- **Component and versions:** Core runtime, all unreleased alpha snapshots.
- **User impact:** Every recording Activity must forward `dispatchTouchEvent`;
  an Activity that does not forward events produces no gesture actions.
- **Trigger:** The host omits or incorrectly orders the forwarding call.
- **Detection:** The sample capture acceptance journey records zero actions, and
  integration review cannot find the forwarding call.
- **Mitigation:** Install the documented forwarding hook before normal Activity
  dispatch and exercise it in a host acceptance test.
- **Security or privacy consequence:** Automatic instrumentation is absent, so
  capture cannot begin merely because the dependency is present. No privacy
  budget is relaxed.
- **Removal target:** Decide whether an opt-in instrumentation plugin is safe and
  necessary after the first production-beta dogfood cycle.

## Gesture and semantic action coverage is intentionally narrow

- **Component and versions:** Core and Compose runtimes, all unreleased alpha
  snapshots.
- **User impact:** Only primary-pointer tap, long press, and swipe are recorded.
  Multi-touch cancels capture; back, keyboard, IME, typed text, and semantic
  scroll actions are not represented.
- **Trigger:** A reproduction depends on one of the unsupported interactions.
- **Detection:** The exported trace omits that interaction and the replay cannot
  reach the same application state.
- **Mitigation:** Keep controlled fixtures within supported actions or add a
  privacy-reviewed explicit replay fixture outside captured user input.
- **Security or privacy consequence:** Typed and arbitrary visible text remain
  excluded. Unsupported actions must never be approximated by capturing raw
  accessibility or input streams.
- **Removal target:** Add one privacy-reviewed action family per compatible
  trace-format release after production beta.

## Interrupted sessions are not resumable

- **Component and versions:** Runtime persistence, all unreleased alpha
  snapshots.
- **User impact:** Process death leaves the active session durable but it cannot
  be resumed or exported as a completed trace.
- **Trigger:** The application process terminates before `stopRecording`.
- **Detection:** Room contains an interrupted active session and export selects
  an earlier completed session or reports no eligible trace.
- **Mitigation:** Stop recordings at the host's explicit workflow boundary and
  communicate interruption to internal testers.
- **Security or privacy consequence:** The data remains within bounded private
  storage and normal retention; it is never uploaded as a completed session.
- **Removal target:** Define a schema-compatible interrupted-session disposition
  before stable 1.0.

## Local trace management exposes only the newest completed trace

- **Component and versions:** Runtime facade, all unreleased alpha snapshots.
- **User impact:** Export and hosted upload select the newest eligible completed
  session; there is no public list, per-session export, pending-upload cancel, or
  per-session delete API.
- **Trigger:** A host needs to manage an older retained session or cancel one
  queued upload.
- **Detection:** The required operation is absent from the public facade.
- **Mitigation:** Use bounded retention, `deleteAllTraces` while idle, and a
  dedicated internal-test workflow that handles one trace at a time.
- **Security or privacy consequence:** Hosts cannot yet offer granular local
  deletion controls; this blocks general production-user activation.
- **Removal target:** Add project-reviewed listing and project/session-scoped
  deletion before enabling recording for production users.

## Upload credentials require process-local reconstruction

- **Component and versions:** WorkManager upload bridge, all unreleased alpha
  snapshots.
- **User impact:** Pending uploads cannot obtain a credential after process
  recreation until an application-owned recorder for that project is created.
- **Trigger:** WorkManager starts before host application initialization
  recreates the upload-enabled runtime.
- **Detection:** The worker records a bounded retry without reading or persisting
  a credential.
- **Mitigation:** Construct the application-scoped runtime from
  `Application.onCreate` and keep the provider backed by the host's secure store.
- **Security or privacy consequence:** Credentials are deliberately excluded
  from Room and WorkManager data. Persisting a raw token is not an acceptable
  workaround.
- **Removal target:** Reassess the bridge after dogfooding; retain it if a safer
  persistent credential abstraction cannot be defined.

## Replay does not enforce environment parity

- **Component and versions:** Captured environment metadata and downstream
  replay, all unreleased alpha snapshots.
- **User impact:** A replay may run despite API level, locale, display, theme, or
  font-scale differences that change behavior.
- **Trigger:** The controlled replay environment differs from recorded metadata.
- **Detection:** Console diagnostics show environment differences; no Android
  client gate rejects them automatically.
- **Mitigation:** Provision the recorded environment where possible and treat a
  mismatch as diagnostic context rather than deterministic equivalence.
- **Security or privacy consequence:** No extra data is collected. Environment
  metadata remains coarse and schema-bounded.
- **Removal target:** Add server-side compatibility classification before stable
  1.0 without claiming deterministic state replay.

## Relocated Koin publication is not self-contained

- **Component and versions:** Internal DI in the unreleased production-beta
  branch; Koin Embedded 3.5.6 beta.
- **User impact:** Source builds work and do not conflict with host Koin, but a
  future Maven consumer would currently need Kotzilla's repository to resolve
  the relocated transitive dependency.
- **Trigger:** The runtime AAR is published with its current dependency metadata.
- **Detection:** A clean consumer using only Google Maven and Maven Central
  cannot resolve `embedded-koin-core`.
- **Mitigation:** Do not publish the SDK yet. Qualify and publish or bundle a
  reviewed relocated artifact under the ReproTrail release process.
- **Security or privacy consequence:** Host DI isolation is preserved; the risk
  is supply-chain availability and review, not cross-graph access.
- **Removal target:** Must be removed before the first public Android release
  candidate.

## Binary compatibility and public artifact publication are not established

- **Component and versions:** Core and Compose AARs, all source snapshots.
- **User impact:** There is no supported Maven coordinate or compatibility
  promise for upgrading between commits.
- **Trigger:** A consumer copies source or an AAR from one commit and upgrades to
  another.
- **Detection:** No signed candidate tag, published module metadata, API baseline,
  provenance, or consumer matrix exists.
- **Mitigation:** Pin the exact source commit for internal testing and do not
  redistribute snapshot binaries as stable SDK releases.
- **Security or privacy consequence:** Unverified binaries may not match reviewed
  source; only locally built controlled-test artifacts are supported.
- **Removal target:** Must be removed before the first public Android release
  candidate.

## Production-user consent and granular deletion are incomplete

- **Component and versions:** Host policy boundary and runtime facade, all
  unreleased alpha snapshots.
- **User impact:** The SDK supplies opt-in capture controls but not a complete
  product consent UI, disclosure flow, or per-session deletion experience.
- **Trigger:** A host attempts to enable capture for real production users.
- **Detection:** Release review cannot demonstrate the project-specific consent,
  disclosure, revocation, and granular deletion journey.
- **Mitigation:** Restrict use to synthetic or controlled internal testing with
  capture disabled by default.
- **Security or privacy consequence:** Enabling general production capture would
  violate the production-beta privacy gate.
- **Removal target:** Must be resolved by the host and ReproTrail dogfood evidence
  before production-user activation; it may remain a host-integration duty in
  stable documentation.
