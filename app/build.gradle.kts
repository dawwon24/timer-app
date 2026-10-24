plugins {
    id("com.android.application")
}

android {
    namespace = "com.hs.timer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hs.timer"
        minSdk = 29
        targetSdk = 36
        // CI 빌드 번호로 매 릴리스마다 증가 (로컬 빌드는 1)
        val build = System.getenv("TIMER_BUILD")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // 개인 설치용: 디버그 키로 서명
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
