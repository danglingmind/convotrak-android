import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Secrets live in local.properties (gitignored) so they never land in source control.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Google Maps key. Falls back to empty so the project still builds without it.
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY", "")

// PostHog product analytics. The project API key is public and write-only. Empty = analytics is off.
// Host depends on the project region: https://us.i.posthog.com or https://eu.i.posthog.com.
val posthogApiKey: String = localProps.getProperty("POSTHOG_API_KEY", "")
val posthogHost: String = localProps.getProperty("POSTHOG_HOST", "https://us.i.posthog.com")

// Meta app events (ad attribution and optimisation). App ID and client token are both public, from
// the Meta app dashboard (Settings → Basic / Advanced). Empty = Meta events are off.
val metaAppId: String = localProps.getProperty("META_APP_ID", "")
val metaClientToken: String = localProps.getProperty("META_CLIENT_TOKEN", "")

// Debug builds send no analytics (PostHog or Meta) unless built with -PanalyticsDebug=true, the
// Android twin of iOS's `-analyticsDebug YES` launch argument. Ignored by release builds.
val analyticsDebug: Boolean = (findProperty("analyticsDebug") as String?)?.toBoolean() ?: false

// Upload signing. Play refuses an unsigned bundle at upload, and with no signingConfig declared
// that is exactly what `bundleRelease` produced: the signReleaseBundle task still runs, finds
// nothing to sign with, and succeeds — so the build looked clean and the failure only appeared
// after uploading to the Console.
//
// Set all four in local.properties:
//   RELEASE_STORE_FILE=/absolute/path/to/upload-keystore.jks
//   RELEASE_STORE_PASSWORD=...
//   RELEASE_KEY_ALIAS=upload
//   RELEASE_KEY_PASSWORD=...
//
// Environment variables of the same names win when set, so CI can supply them without a file.
fun signingProp(name: String): String? =
    (System.getenv(name) ?: localProps.getProperty(name))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingProp("RELEASE_STORE_FILE")
val releaseStorePassword = signingProp("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingProp("RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingProp("RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
    releaseStoreFile != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.danglingmind.convotrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.danglingmind.convotrack"
        minSdk = 26
        targetSdk = 36
        // Tracked against iOS rather than counted independently: the two apps are a
        // feature-parity pair, so a rider reporting "I'm on 1.1.2" means the same thing on
        // either platform, and a fix can be described once for both.
        versionCode = 14
        versionName = "2.0.5"

        // Exposed to the manifest (Maps SDK meta-data) and to code (REST services).
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "POSTHOG_API_KEY", "\"$posthogApiKey\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$posthogHost\"")
        buildConfigField("String", "META_APP_ID", "\"$metaAppId\"")
        buildConfigField("String", "META_CLIENT_TOKEN", "\"$metaClientToken\"")
        buildConfigField("boolean", "ANALYTICS_DEBUG", "$analyticsDebug")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Left unset when the credentials are absent, so a local `bundleRelease` still works
            // for checking that the thing compiles. The warning below is what stops that being
            // mistaken for an uploadable artifact again.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Networking + serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Auth
    implementation(libs.clerk.android.api)
    // Clerk ships Credential Manager as a RUNTIME dependency, so it is on the device but not on
    // our compile classpath. Declared here for the one call we make ourselves: clearing the
    // credential state on sign-out, which is what stops the next Google sign-in auto-selecting
    // the account that just left.
    implementation(libs.androidx.credentials)

    // Maps
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Location
    implementation(libs.play.services.location)

    // Realtime
    implementation(libs.socket.io.client)

    // QR scanning (CameraX + ML Kit)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // Billing
    implementation(libs.billing.ktx)

    // Meta app events (ad attribution)
    implementation(libs.facebook.core)

    // QR code generation (invite-code sharing)
    implementation(libs.zxing.core)

    debugImplementation(libs.androidx.ui.tooling)
}

if (!hasReleaseSigning) {
    logger.warn(
        "\n[signing] No release signing credentials found. `bundleRelease` will produce an " +
            "UNSIGNED app-release.aab, which Google Play rejects at upload.\n" +
            "[signing] Set RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS and " +
            "RELEASE_KEY_PASSWORD in local.properties (or as env vars).\n",
    )
}
