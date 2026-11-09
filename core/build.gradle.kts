import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "com.black.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        dataBinding = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    api(libs.androidx.lifecycle.runtime.compose)

    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    // BaseFragment 하위 화면이 쓰는 by viewModels()(이전에는 navigation-fragment-ktx를 거쳐 들어옴)
    api(libs.androidx.fragment.ktx)
    api(libs.material)

    api(libs.androidx.lifecycle.livedata.ktx)
    api(libs.androidx.lifecycle.viewmodel.ktx)

    api(libs.androidx.datastore)

    api(libs.flexbox)

    api(libs.kotlinx.serialization.json)
    // JsonUtil이 공개 API로 노출(이전에는 retrofit converter-gson을 거쳐 들어오던 버전 유지)
    api(libs.gson)

    api(libs.hilt)
    ksp(libs.hilt.compiler)

    testImplementation(project(":test"))
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
