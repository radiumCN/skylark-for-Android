import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------
// 版本号：唯一来源为 gradle/libs.versions.toml 的 appVersionName。
// versionCode 由 versionName 自动派生，保证预发布 < 正式版且单调递增。
// 例：0.1.0-beta.1 -> 9901，0.1.0 -> 10000
// ---------------------------------------------------------------------------
val appVersionName: String = libs.versions.appVersionName.get()

fun deriveVersionCode(name: String): Int {
    val parts = name.split("-", limit = 2)
    val core = parts[0]
    val preRelease = parts.getOrNull(1)
    val (major, minor, patch) = core.split(".").map { it.trim().toInt() }
    val base = major * 1_000_000 + minor * 10_000 + patch * 100
    return if (preRelease == null) {
        base
    } else {
        // 预发布号 (beta.N / rc.N)，取末尾数字，落在 [base-100, base) 区间内
        val preNumber = preRelease.substringAfterLast('.').toIntOrNull() ?: 1
        base - 100 + preNumber.coerceIn(1, 99)
    }
}

android {
    namespace = "com.radium.skylark"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.radium.skylark"
        minSdk = 35
        targetSdk = 36
        versionCode = deriveVersionCode(appVersionName)
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // 发布签名配置：仅当 CI/本地提供 keystore 环境变量时启用（密钥不入库）
    val releaseKeystorePath: String? = System.getenv("SKYLARK_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("SKYLARK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SKYLARK_KEY_ALIAS")
                keyPassword = System.getenv("SKYLARK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin 编译目标：使用运行 Gradle 的 JDK（21）编译，字节码目标 17，与 Java 保持一致
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room schema 导出目录（KSP 项目级配置）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Kotlin ecosystem
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // YAML 解析 (Clash 订阅)
    implementation(libs.snakeyaml)

    // 网络 (订阅拉取)
    implementation(libs.okhttp)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
