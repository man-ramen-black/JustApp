import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "com.black.feature.studypopup"
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
        dataBinding = true
        compose = true
    }

    dataBinding {
        // 유닛 테스트에서 데이터 바인딩 오버레이 뷰(StudyPopupView) 생성용
        // androidx.databinding.DataBinderMapperImpl은 앱 모듈 빌드에서만 생성되므로 이 모듈의 테스트 변형에서도 생성
        enableForTests = true
    }

    testOptions {
        // 리소스를 포함한 Robolectric 테스트는 매니페스트 targetSdk(기본은 compileSdk 36)를 쓰는데
        // 현재 Robolectric은 34까지 지원하므로 app의 targetSdk와 같게 지정
        targetSdk = 34
        unitTests {
            // StudyPopupView 레이아웃과 문자열 리소스를 Robolectric 유닛 테스트에서 읽도록 포함
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.hilt)
    implementation(libs.androidx.hilt.navigation.compose)
    // 팝업 내용 목록(StudyPopup.db), core가 room을 api로 내보내지 않아 직접 선언
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(project(":test"))
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
