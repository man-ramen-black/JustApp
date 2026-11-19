import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.black.test"
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

    testOptions {
        unitTests {
            // 테스트 코드의 API가 구현되어있지 않으면 null 또는 0등을 리턴하도록하여 테스트를 진행
            isReturnDefaultValues = true

            // Robolectric을 사용하여 Unit Test에 안드로이드 리소스 포함
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // core 타입(InstalledAppResolver 등) 목업 헬퍼용, core 유닛 테스트도 이 모듈을 쓰므로 api로 다시 노출하지 않음
    implementation(project(":core"))
    api(libs.androidx.core.ktx)
    api(libs.robolectric)
    api(libs.mockk)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
