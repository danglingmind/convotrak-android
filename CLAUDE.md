# CLAUDE.md

## Publishing a new revision to Google Play

Use the **`android-play-release`** skill (`~/.claude/skills/android-play-release/`) for every
release, version bump, Play track cleanup or Play status check. It covers these steps:
1. Ask patch/minor/major and bump the version (versionCode +1).
2. Update `CHANGELOG.md`.
3. Commit to `master` and tag `vX.Y.Z`.
4. Build and verify the signed bundle.
5. Upload with the service-account key so the testing track holds exactly one release.
6. Write the Play "What's new" copy.

Never skip the version question, and never reuse a versionCode.

### Play release config

- **Package:** `com.danglingmind.convotrack`
- **Service-account key:** its path is `PLAY_SERVICE_ACCOUNT_KEY` in `local.properties`
  (gitignored). The key file stays outside the repo. Never print or commit it or its path.
- **Testing track:** `alpha` (closed testing). It holds a single release, the build under test.
  Always pass `--protect alpha` to `play.py`.
  - The app is still working toward Play's production-access gate: **12+ testers opted in
    continuously for 14 days** on closed testing. 8 testers were opted in on 2026-10-10.
  - Uploading new builds doesn't reset the count, but emptying alpha could, so alpha is never
    emptied.
  - `internal` doesn't count toward the gate. It still holds v6 from early testing: Play
    ignores API requests to remove a completed release, so only the Console's track settings
    can retire it.
  - Leave beta and production empty until the user asks.
- **Main branch:** `master`. Tag releases `vX.Y.Z`. Ask before pushing.
- **Version parity:** keep in step with the iOS app (`MARKETING_VERSION` in
  the sibling `convoy/convotrack.xcodeproj/project.pbxproj`), since riders quote one
  version for both platforms.
- **Signing:** the upload key comes from `RELEASE_*` in `local.properties`, with the keystore
  kept outside the repo, and the certificate is `CN=ConvoTrack, O=DanglingMind, C=IN`. Never
  use Android Studio's "Generate Signed App Bundle" wizard (it writes to `app/release/` and can
  sign with the wrong key).
- **Build command:** the Gradle daemon needs a JetBrains JDK 21 and can't download one on
  macOS arm64, so use Android Studio's bundled JBR:
  `J="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; JAVA_HOME="$J" ./gradlew -Porg.gradle.java.installations.paths="$J" clean bundleRelease`
- **Bundle output:** `app/build/outputs/bundle/release/app-release.aab`
