# Material Files Batonz — dav4jvm Build Investigation Report

## Scope and guardrails

This investigation was performed against the user-owned repository `mrbdalslam606-netizen/MaterialFiles-Bartonz`, on branch `feature/storage-analysis-beta`. The upstream Material Files repository was not modified. The existing Storage Analysis, Restore Last Opened Path, and Show Full File Names implementation was preserved; only a duplicate-resource cleanup and an incorrect FileStore import exposed during compilation were corrected because they blocked validation of the current branch.

No final dependency change was pushed. The repository was returned to the original declaration:

```gradle
implementation('com.github.bitfireAT:dav4jvm:02fe1a95e6')
```

## Baseline

The first baseline build used the Android SDK installed locally and the repository's Gradle wrapper. The command was:

```bash
ANDROID_HOME=/home/ubuntu/android-sdk \
PATH=/home/ubuntu/android-sdk/cmdline-tools/latest/bin:/home/ubuntu/android-sdk/platform-tools:$PATH \
./gradlew assembleDebug --refresh-dependencies --no-daemon
```

The build failed at `:app:compileDebugAidl` before Kotlin compilation because Gradle could not resolve `com.github.bitfireAT:dav4jvm:02fe1a95e6`. Direct requests to the JitPack POM and JAR URLs also returned HTTP 404. This matches upstream Material Files issue [#1590][1].

## Solution 1 — rescue the existing dependency

| Check | Result |
|---|---|
| Direct JitPack POM request | HTTP 404 for `02fe1a95e6` |
| Direct JitPack JAR request | HTTP 404 for `02fe1a95e6` |
| Referenced upstream commit | `c1bc14348831bcdb00f3a6eec4859b81c7dc3728` exists in the official dav4jvm repository |
| Gradle command | `./gradlew assembleDebug --refresh-dependencies --no-daemon` |
| Dependency resolved | No |
| Kotlin compilation reached | No |
| `assembleDebug` succeeded | No |
| First real error | `Could not find com.github.bitfireAT:dav4jvm:02fe1a95e6` |

The upstream issue contains a report that the full generated JitPack commit hash can work:

```gradle
implementation('com.github.bitfireAT:dav4jvm:02fe1a95e6b86e323bec3784d7d2fe2d4081dde6')
```

That full hash was tested with `--refresh-dependencies`, but it also returned `Could not find` from Google Maven, Maven Central, and JitPack. Therefore solution 1 failed, and the original short coordinate was restored.

## Solution 2 — published dav4jvm version

The official dav4jvm repository currently publishes newer semver releases, including 3.0.2 and 4.0.0. Source inspection showed a significant API-generation boundary. Material Files imports the legacy package/API surface such as `at.bitfire.dav4jvm.BasicDigestAuthHandler`, `DavResource`, `DavCollection`, `DavResourceAccessor`, and response/property APIs. dav4jvm 3.0.2 uses the newer Ktor-oriented implementation and changes authentication and response APIs. The official project build also exposes Ktor dependencies in this generation [2].

The candidate `3.0.2` was tested using:

```bash
./gradlew assembleDebug --refresh-dependencies --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=512m'
```

After resolving the candidate, Gradle reached `:app:compileDebugKotlin`, but compilation failed before `assembleDebug` completed. The decisive incompatibility was:

```text
Authentication.kt:43:76
Argument type mismatch: actual type is 'CharArray', but 'String' was expected.
```

An earlier run also experienced a daemon disappearance under memory pressure, so the constrained single-worker build was used to obtain a deterministic compiler result. The candidate was rejected because it does not preserve the existing WebDAV API without source changes. No candidate dependency was left in the project.

## Solution 3 — local source dependency

The exact upstream commit `c1bc14348831bcdb00f3a6eec4859b81c7dc3728` was cloned from the official dav4jvm repository, vendored temporarily as a Gradle composite build, and substituted for the external module. No prebuilt JAR or AAR was used.

The command was:

```bash
./gradlew clean assembleDebug --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1024m -XX:MaxMetaspaceSize=512m'
```

The build successfully resolved and compiled the local source project far enough to reach the Material Files Kotlin compilation. It then failed with the same compatibility boundary:

```text
Authentication.kt:43:76
Argument type mismatch: actual type is 'CharArray', but 'String' was expected.
```

This confirms that the exact source commit cannot be consumed by the current Material Files WebDAV code without rewriting the authentication integration. Such a rewrite is outside the requested dependency-only scope and would violate the requirement to preserve the current API and behavior. The composite build directory and all temporary settings were removed.

| Solution | Dependency resolved | Kotlin compilation reached | `assembleDebug` | Final status |
|---|---:|---:|---:|---|
| 1. Existing short coordinate | No | No | No | Failed at dependency resolution |
| 1b. Full JitPack commit hash | No | No | No | Failed at dependency resolution |
| 2. dav4jvm 3.0.2 | Yes | Yes | No | Rejected: API incompatibility |
| 3. Local c1bc143 source | Yes | Yes | No | Rejected: same API incompatibility |

## Files changed during this investigation

The dependency declaration was not changed in the final worktree. Temporary `settings.gradle` composite-build wiring, the temporary `dav4jvm-local` source tree, and `local.properties` were removed.

The remaining worktree changes are limited to the previously implemented feature branch plus two necessary build-blocker corrections: `StorageAnalysis.kt` now imports the repository's existing `Path.getFileStore` extension from its correct package, and duplicate preference-key declarations were removed from the base string resource because the keys already belong in `donottranslate_prefs.xml`. The existing feature implementation itself was not redesigned.

## Final conclusion

No solution is currently proven to produce a successful `assembleDebug` without either restoring the unavailable JitPack artifact or rewriting the WebDAV integration. Consequently, no dependency change was pushed, no APK was created, and no new release was published from this investigation. The existing source-only beta release remains unchanged and must not be treated as an installable APK release.

The least invasive next step is to make the exact original artifact available through a reliable internal or mirrored Maven repository while retaining the original API. A published dav4jvm 3.x/4.x upgrade should not be selected merely because it resolves; it requires a deliberate WebDAV adapter migration and separate regression testing.

## References

[1]: https://github.com/zhanghai/MaterialFiles/issues/1590 "Material Files issue #1590: Building from sources fails due to broken dav4jvm dependency"

[2]: https://github.com/bitfireAT/dav4jvm "Official dav4jvm repository and current source/API"

[3]: https://jitpack.io/ "JitPack Maven publishing service"
