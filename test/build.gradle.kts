plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.black.test"
    compileSdk = 34

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
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
    api(libs.androidx.core.ktx)
    api(libs.robolectric)
    api(libs.mockk)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}