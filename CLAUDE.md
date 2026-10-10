# CLAUDE.md

## Publishing a new revision to Google Play

Whenever the user asks to publish, release, or build a new revision for the Play Store, follow
these steps in order. Do not skip the version question, and do not reuse a versionCode.

1. **Ask how to bump the version.** Use AskUserQuestion to let the user pick:
   - **Patch**: `1.1.3` → `1.1.4` (fixes and small UI changes)
   - **Minor**: `1.1.3` → `1.2.0` (new features)
   - **Major**: `1.1.3` → `2.0.0` (large or breaking changes)

   Never pick the bump yourself.
2. **Bump the version** in `app/build.gradle.kts` (`defaultConfig`):
   - `versionName` follows the user's choice.
   - `versionCode` goes up by exactly 1, whatever the bump type. Play rejects a versionCode
     it has already seen.
   - Versions are kept in step with the iOS app (`convoy/convotrack.xcodeproj`). Remind the
     user if the iOS version differs.
3. **Write the changelog.** Collect the commits since the last release tag
   (`git log <last vX.Y.Z tag>..HEAD`; if there is no tag, start from the last version-bump
   commit). Add a section to the top of `CHANGELOG.md` with this heading:
   `## X.Y.Z (versionCode N) — YYYY-MM-DD`. Group the entries under Added / Changed / Fixed.
4. **Commit everything to `master`.** This repo's main branch is `master`. Commit all pending
   changes, including the version bump and changelog. Use the message
   `Release X.Y.Z (versionCode N)` and include the commit attribution trailer. Then tag the
   commit `vX.Y.Z` so the next changelog knows where to start. Ask before pushing.
5. **Build the signed bundle** with `./gradlew clean bundleRelease`.
   - The Gradle daemon needs a JetBrains JDK 21 (`gradle/gradle-daemon-jvm.properties`) and
     can't download one on macOS arm64. Use Android Studio's bundled JBR:
     `J="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; JAVA_HOME="$J" ./gradlew -Porg.gradle.java.installations.paths="$J" clean bundleRelease`
   - The upload key comes from `RELEASE_*` in `local.properties`, and the keystore lives outside
     the repo. Never print or commit those values.
   - Never use Android Studio's "Generate Signed App Bundle" wizard (it writes to `app/release/`
     and can sign with the wrong key).
   - Check the bundle is signed with `jarsigner -verify app/build/outputs/bundle/release/app-release.aab`.
     Also confirm its manifest carries the new versionCode and versionName.
6. **Point the user to the build:**
   `app/build/outputs/bundle/release/app-release.aab`
   (absolute: `/Users/ricky/Workspace/convotrack-android/app/build/outputs/bundle/release/app-release.aab`).
7. **Give the Play Store copy** in chat, ready to paste:
   - **What's new** (release notes): plain text, user-facing, **≤ 500 characters**, inside
     `<en-US> … </en-US>` tags as Play Console expects. Describe what a rider notices, not
     internal refactors.
   - **New features**: a short bulleted list of the headline features for the listing or a
     promo post. Only include this when the release has real new features.
