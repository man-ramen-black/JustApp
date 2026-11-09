import com.android.build.api.variant.HostTestBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.jetbrains.kotlin.parcelize)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.jetbrains.kotlin.compose)
    id("project-report") // 터미널에 ./gradlew app:htmlDependencyReport 입력하여 디펜던시 리포트 확인 가능 (...classpath 폴더 확인)
}

android {
    namespace = "com.black.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.black.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.black.app.HiltTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // 계측 테스트 전용 빌드 타입(com.black.app.uitest), 매일 쓰는 debug 앱과 패키지가 달라 am instrument의 강제 종료·테스트 데이터가 그 앱에 닿지 않음
        create("uitest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            // core·feature 모듈에는 uitest 빌드 타입이 없어 debug 변형 사용(initWith가 debug의 빈 목록을 복사하므로 그 뒤에 추가)
            matchingFallbacks += listOf("debug")
        }
    }

    testBuildType = "uitest"

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

    hilt {
        enableAggregatingTask = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

androidComponents {
    // AGP 9는 JVM 유닛 테스트를 testBuildType(uitest) 변형에만 만들므로(android.onlyEnableUnitTestForTheTestedBuildType 기본 true)
    // 유닛 테스트는 debug(testDebugUnitTest, testDebug 소스셋 포함)에서만 만들고 계측 테스트 호스트 전용인 uitest에서는 생략
    beforeVariants { variantBuilder ->
        variantBuilder.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = variantBuilder.buildType == "debug"
    }
}

// google-services.json의 클라이언트는 com.black.app뿐이라 uitest 변형은 처리 생략("No matching client" 빌드 실패 회피)
// google_app_id 리소스가 없어 테스트 앱은 Firebase 초기화 없이 실행(Firebase API를 직접 부르는 코드가 생기면 이 앱에서 예외)
tasks.named { it == "processUitestGoogleServices" }.configureEach { enabled = false }

dependencies {
    implementation(project(":core"))
    implementation(project(":feature:pokerogue"))
    implementation(project(":feature:floatingbutton"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.androidx.datastore)

    implementation(libs.androidx.room.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    testImplementation(project(":test"))
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.fragment.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // createComposeRule이 쓰는 ComponentActivity 등록(계측 테스트 빌드 타입 전용)
    "uitestImplementation"(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
