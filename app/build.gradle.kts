plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.12"
}

android {
    namespace = "com.dimowner.audiorecorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dimowner.audiorecorder"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        viewBinding = true
    }

    signingConfigs {
        create("dev") {
            storeFile = file("key/debug/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                "proguard-rules.pro"
            )
//			firebaseCrashlytics {
//				mappingFileUploadEnabled true
//			}
        }
        getByName("debug") {
            isMinifyEnabled = false
            enableUnitTestCoverage = true
        }
    }

    flavorDimensions += listOf("default")
    productFlavors {
        create("debugConfig") {
            dimension = "default"
            applicationId = "com.dimowner.audiorecorder.debug"
            signingConfig = signingConfigs.getByName("dev")
        }
        create("releaseConfig") {
            dimension = "default"
            signingConfig = signingConfigs.getByName("dev")
            applicationId = "com.dimowner.audiorecorder"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources.excludes.addAll(
            listOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
        )
    }
    testOptions {
        unitTests {
            // Required for Robolectric to access Android resources
            isIncludeAndroidResources = true
            all {
                it.ignoreFailures = true
            }
        }
    }
}

// ── JaCoCo coverage report & verification ──────────────────────────────────────

val jacocoExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.class",
    "**/*_Factory.class",
    "**/*_MembersInjector.class",
    "**/*_Impl.class",
    "**/*_Impl$*.class",
    "**/Hilt_*.class",
    "**/*Module_*.class",
    "**/*_HiltModules*.class",
    "**/*Directions*.class",
    "**/*Args*.class",
    "**/databinding/**",
    "**/di/**",
)

val jacocoClassDirectories = files(
    fileTree("${layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debugConfigDebug") {
        exclude(jacocoExcludes)
    },
    fileTree("${layout.buildDirectory.get().asFile}/intermediates/javac/debugConfigDebug") {
        exclude(jacocoExcludes)
    },
)

val jacocoSourceDirectories = files("src/main/java")

val jacocoExecutionData = fileTree(layout.buildDirectory) {
    include("outputs/unit_test_code_coverage/debugConfigDebugUnitTest/**/*.exec")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugConfigDebugUnitTest")

    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoTestReport.xml"))
    }

    classDirectories.setFrom(jacocoClassDirectories)
    sourceDirectories.setFrom(jacocoSourceDirectories)
    executionData.setFrom(jacocoExecutionData)
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")

    classDirectories.setFrom(jacocoClassDirectories)
    sourceDirectories.setFrom(jacocoSourceDirectories)
    executionData.setFrom(jacocoExecutionData)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.05".toBigDecimal()
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}

dependencies {
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.android.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.hilt.android)
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.gson)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.viewbinding)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
