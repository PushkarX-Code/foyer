plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

// TMDB credential for artwork (v3 API key or v4 read token). Set TMDB_TOKEN in ~/.gradle/gradle.properties,
// local.properties or the environment (CI uses a repository secret). Without it the
// home screen falls back to the apps-only layout.
val tmdbToken: String = providers.gradleProperty("TMDB_TOKEN")
    .orElse(providers.environmentVariable("TMDB_TOKEN"))
    .getOrElse("")

android {
    namespace = "app.foyer.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.foyer.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = (providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1)
        versionName = "0.1." + (providers.environmentVariable("GITHUB_RUN_NUMBER").orNull ?: "dev")
        buildConfigField("String", "TMDB_TOKEN", "\"$tmdbToken\"")
    }

    // Development key only. Every build (local and CI) is signed with the same key so
    // new builds install over old ones. The Play Store release will use a separate,
    // private upload key.
    signingConfigs {
        create("dev") {
            storeFile = file("dev.keystore")
            storePassword = "foyerdev"
            keyAlias = "foyer"
            keyPassword = "foyerdev"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("dev")
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
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}

// Screenshot tests read sample artwork from FOYER_ART_DIR when set (not committed:
// film artwork is copyrighted). Without it they render grey placeholders.
tasks.withType<Test>().configureEach {
    providers.environmentVariable("FOYER_ART_DIR").orNull?.let { systemProperty("foyer.art", it) }
}
