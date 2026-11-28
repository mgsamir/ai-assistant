# Assistant modules (assistant-modules branch)

This branch adds isolated defensive modules that reuse the existing `Lab`, `Providers`, `Models`, `Keys`, and `GitHub` concepts without changing the existing assistant entry point, workflows, or Gradle build configuration.

## Safety boundaries
- Android system inventory is read-only. Arbitrary shell syntax is rejected; no shell is invoked.
- Network checks accept literal, exact IPs approved in `security_approved_ips`; loopback is the only built-in target. CIDRs, hostnames, public addresses, and unconfigured devices are rejected. `nmap` is limited to a basic host-presence probe.
- Knowledge results are untrusted text and never alter allowlists or execute commands.
- Upgrade proposals remain pending until the user approves. A PR can only be opened when a patch has been explicitly attached to a proposal; the upgrader never writes to `main` or merges a PR.
- GitHub PAT encryption uses Android Keystore AES-GCM and stores ciphertext only.

## Integration note
`AssistantModuleScreens.upgrades(...)` is wired into the main menu as the "Upgrades" screen, and `SelfAuditor` is wired into the "Health check" screen (both in `MainActivity.kt`). `Assistant.kt` exposes the same capabilities as voice/text tools (`system_check`, `run_phone_command`, `confirm_phone_command`, `self_audit`, `research_upgrades`, `list_upgrades`, `approve_upgrade`, `deny_upgrade`) through its existing tool-declaration / pre-fetch / voice-dispatch pattern. The two JUnit test files (`app/src/test/java/com/example/assistant/SecurityManagerTest.kt`, `ModelRouterTest.kt`) run with the existing `testImplementation 'junit:junit:4.13.2'` dependency already in `app/build.gradle` (`./gradlew testDebugUnitTest`); no extra Gradle dependency was needed.

A Settings → "Cybersecurity Assistant" section lets the user enter their approved home-network IPs (`security_approved_ips`) and a fine-grained GitHub PAT for `GitHubUpgrader` (stored Android-Keystore-encrypted, never in plain SharedPreferences).
