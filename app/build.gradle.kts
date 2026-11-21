plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val updatesRepo = providers.gradleProperty("clodexUpdatesRepo")
    .orElse("BlackStormLegenda629/clodex-harness").get().trim()
require(updatesRepo.isEmpty() || (
    Regex("[A-Za-z0-9-]{1,39}/[A-Za-z0-9_.-]{1,100}").matches(updatesRepo) &&
        updatesRepo.substringAfter('/') !in setOf(".", "..")
)) { "clodexUpdatesRepo must be a GitHub owner/repo or blank" }

android {
    namespace = "app.clodex.harness"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.clodex.harness"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0"
        buildConfigField("String", "UPDATES_REPO", "\"$updatesRepo\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.work.runtime)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
