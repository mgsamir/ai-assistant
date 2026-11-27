# Assistant modules (assistant-modules branch)

This branch adds isolated defensive modules that reuse the existing `Lab`, `Providers`, `Models`, `Keys`, and `GitHub` concepts without changing the existing assistant entry point, workflows, or Gradle build configuration.

## Safety boundaries
- Android system inventory is read-only. Arbitrary shell syntax is rejected; no shell is invoked.
- Network checks accept literal, exact IPs approved in `security_approved_ips`; loopback is the only built-in target. CIDRs, hostnames, public addresses, and unconfigured devices are rejected. `nmap` is limited to a basic host-presence probe.
- Knowledge results are untrusted text and never alter allowlists or execute commands.
- Upgrade proposals remain pending until the user approves. A PR can only be opened when a patch has been explicitly attached to a proposal; the upgrader never writes to `main` or merges a PR.
- GitHub PAT encryption uses Android Keystore AES-GCM and stores ciphertext only.

## Integration note
`AssistantModuleScreens` exposes reusable Views, but the existing `MainActivity` navigation has not been modified. Add these screens to the app navigation in a separate reviewed integration change. The two test harnesses are plain Kotlin entry points and need a Kotlin/JVM test runner to execute; Gradle dependencies were intentionally left untouched per repository safety constraints.
