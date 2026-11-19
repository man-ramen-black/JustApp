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

    // 기능 모듈의 Retrofit 서비스가 Retrofit 애너테이션과 OkHttp 타입(MultipartBody 등)을 직접 씀
    api(libs.retrofit)
    api(libs.retrofit.converter.kotlinx.serialization)
    // Retrofit 2.11.0이 끌어오는 OkHttp 3.14 대신 4.x 사용
    api(libs.okhttp)
    // 로그 인터셉터는 Network 안에서만 쓰고 공개 API는 NetworkLogLevel
    implementation(libs.okhttp.logging.interceptor)

    api(libs.hilt)
    ksp(libs.hilt.compiler)

    testImplementation(project(":test"))
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
