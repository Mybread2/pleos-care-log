import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.carelog.app"
    compileSdk = 35
    useLibrary("android.car") // CarUxRestrictions(정차 판정), 외기온 속성

    defaultConfig {
        applicationId = "com.carelog.app"
        minSdk = 28 // Pleos 요구사항 (사이닝 키 로테이션)
        targetSdk = 34 // Pleos Emulator v2.1.0 = API 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // 릴리스 서명 키는 signing/ (git 제외). 없으면 릴리스 APK는 서명하지 않는다 — 디버그 키로 몰래 서명하지 않게.
    val keystore = rootProject.file("signing/keystore.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use(::load) } }
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
            enableV3Signing = true // Pleos: 사이닝 키 로테이션·리니지 지원 (v3, API 28+)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    sourceSets["test"].resources.srcDir(rootProject.file("mock-fixtures/fixtures"))
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

/** 제출 APK 이름의 팀명. 실제 팀명으로 바꿀 것 (gradle.properties는 한글이 깨져서 여기 둔다). */
val submissionTeamName = "팀명"

/** 공모전 제출 경로로 복사: apk/{팀명}_{앱이름}_release.apk */
tasks.register<Copy>("exportReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.file("apk"))
    rename { "${submissionTeamName}_PleosCareLog_release.apk" }
}
