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
    namespace = "com.black.feature.floatingbutton"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
        dataBinding = true
        compose = true
    }

    dataBinding {
        // 유닛 테스트에서 데이터 바인딩 오버레이 뷰(FloatingView) 생성용
        // androidx.databinding.DataBinderMapperImpl은 앱 모듈 빌드에서만 생성되므로 이 모듈의 테스트 변형에서도 생성
        enableForTests = true
    }

    testOptions {
        // 리소스를 포함한 Robolectric 테스트는 매니페스트 targetSdk(기본은 compileSdk 36)를 쓰는데
        // 현재 Robolectric은 34까지 지원하므로 app의 targetSdk와 같게 지정
        targetSdk = 34
        unitTests {
            // FloatingView 레이아웃을 Robolectric 유닛 테스트에서 inflate하도록 포함
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.hilt)
    implementation(libs.androidx.hilt.navigation.compose)
    testImplementation(project(":test"))
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
