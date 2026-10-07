import groovy.json.StringEscapeUtils

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.android)
}

val testAccountPass = System.getenv("TEST_ACCOUNT_PASS") ?: project.properties["testAccountPassword"] as? String ?: "Pass"

android {
    namespace = "ch.protonvpn.android.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TEST_ACCOUNT_PASSWORD",  "\"${StringEscapeUtils.escapeJava(testAccountPass)}\"")
    }

    targetProjectPath = ":app"

    flavorDimensions += listOf("environment", "functionality", "distribution")
    productFlavors {
        create("production") { dimension = "environment" }
        create("google") { dimension = "functionality" }
        create("playStore") { dimension = "distribution" }
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(projects.uiAutomatorTestUtil)

    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}