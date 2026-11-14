import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ============================================================================
// 签名：唯一可信来源（防止用错密钥打出「无法覆盖安装」的包）
// ----------------------------------------------------------------------------
// 本仓库所有对外分发的 APK 必须由同一把密钥签名，否则已安装用户无法覆盖升级。
// 这把密钥的证书指纹固定在下面，CI 与本地构建都会校验：
//
//   CN=QQBotQQ, OU=Local, O=Private, L=Local, ST=Local, C=CN
//   SHA-256: a695b1e8f15e40b6e5219d6dd9d41c68985b7907cfd669907cd878cc9685e005
//
// 指纹是公开信息（每个已签名的 APK 里都有），放进仓库没有问题；
// 密钥文件与口令是机密，只存在于：
//   - CI：GitHub Actions secrets（KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_PASSWORD / KEY_ALIAS）
//   - 本地：keystore.jks（已 gitignore） + local.properties 里的口令（已 gitignore）
//
// 本地想产出可安装的 release 包，在 local.properties 里加四行即可：
//   QQBOT_STORE_FILE=../keystore.jks
//   QQBOT_STORE_PASSWORD=***
//   QQBOT_KEY_ALIAS=***
//   QQBOT_KEY_PASSWORD=***
// 也可以用同名环境变量代替（CI 就是这么传的）。
// ============================================================================

val expectedCertSha256 = "a695b1e8f15e40b6e5219d6dd9d41c68985b7907cfd669907cd878cc9685e005"

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/** 取签名配置：环境变量优先于 local.properties */
fun secret(key: String): String? =
    (System.getenv(key) ?: localProps.getProperty(key))?.trim()?.takeIf { it.isNotEmpty() }

/** storeFile 允许写相对路径，相对**根目录**解析 */
fun resolveStoreFile(path: String): File {
    val f = File(path)
    return if (f.isAbsolute) f else rootProject.file(path)
}

val storeFileValue = secret("QQBOT_STORE_FILE")
val storePasswordValue = secret("QQBOT_STORE_PASSWORD")
val keyAliasValue = secret("QQBOT_KEY_ALIAS")
val keyPasswordValue = secret("QQBOT_KEY_PASSWORD")

val signingAvailable = storeFileValue != null &&
    storePasswordValue != null &&
    keyAliasValue != null &&
    keyPasswordValue != null &&
    resolveStoreFile(storeFileValue).exists()

if (!signingAvailable) {
    logger.lifecycle(
        "[签名] 未配置签名凭据 → release 产物将是**未签名** APK，无法安装。\n" +
        "        本地构建请在 local.properties 配置 QQBOT_STORE_FILE / QQBOT_STORE_PASSWORD /\n" +
        "        QQBOT_KEY_ALIAS / QQBOT_KEY_PASSWORD（见文件顶部注释）。\n" +
        "        CI 由 secrets 注入同样的四个值。"
    )
}

android {
    namespace = "com.qqbot.client"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.qqbot.client"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "1.8"
    }

    val releaseSigning = if (signingAvailable) {
        signingConfigs.create("release") {
            storeFile = resolveStoreFile(storeFileValue!!)
            storePassword = storePasswordValue
            keyAlias = keyAliasValue
            keyPassword = keyPasswordValue
            // v2/v3 都开，保证 Android 7+ 到最新系统都能装
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 没有凭据时不指定签名 → 产出未签名包（CI 会在构建后用 apksigner 补签）
            if (releaseSigning != null) signingConfig = releaseSigning
        }
    }

    // 产物统一命名为 QQBot.apk / QQBot-debug.apk，不再出现 app-release 等字眼
    applicationVariants.all {
        val variantName = name
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                when (variantName) {
                    "debug" -> "QQBot-debug.apk"
                    "release" -> "QQBot.apk"
                    else -> "QQBot-$variantName.apk"
                }
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

/**
 * 校验 release APK 的签名证书指纹。
 *
 * 用法：`./gradlew verifyReleaseSignature`（会先构建 release）
 * 任何一环签名不对（未签名 / 换了密钥 / 误用 debug 密钥）都会在这里直接失败，
 * 而不是等用户装到手机上才发现「应用未安装」。
 */
tasks.register("verifyReleaseSignature") {
    group = "verification"
    description = "校验 release APK 的签名证书是否为发布密钥"
    dependsOn("assembleRelease")

    doLast {
        val apk = file("build/outputs/apk/release/QQBot.apk")
        if (!apk.exists()) throw GradleException("找不到 APK：$apk")

        val sdkDir = sequenceOf(
            localProps.getProperty("sdk.dir"),
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
        ).filterNotNull().firstOrNull { it.isNotBlank() }
            ?: throw GradleException("找不到 Android SDK 路径（sdk.dir / ANDROID_HOME）")

        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val apksignerName = if (isWindows) "apksigner.bat" else "apksigner"
        val buildTools = File(sdkDir, "build-tools")
            .listFiles { f -> f.isDirectory }
            ?.maxByOrNull { it.name }
            ?: throw GradleException("找不到 build-tools 目录：$sdkDir/build-tools")
        val apksigner = File(buildTools, apksignerName)
        if (!apksigner.exists()) throw GradleException("找不到 apksigner：$apksigner")

        val process = ProcessBuilder(
            apksigner.absolutePath, "verify", "--print-certs", apk.absolutePath
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()

        val actual = Regex("""certificate SHA-256 digest:\s*([0-9a-fA-F]+)""")
            .find(output)?.groupValues?.get(1)

        if (actual == null) {
            throw GradleException(
                """
                |release APK 未签名，装不上！
                |
                |本地构建请先在 local.properties 配置签名凭据：
                |  QQBOT_STORE_FILE=../keystore.jks
                |  QQBOT_STORE_PASSWORD=***
                |  QQBOT_KEY_ALIAS=***
                |  QQBOT_KEY_PASSWORD=***
                |
                |apksigner 输出：
                |$output
                """.trimMargin()
            )
        }

        if (!actual.equals(expectedCertSha256, ignoreCase = true)) {
            throw GradleException(
                """
                |签名证书不匹配，这个包会导致已安装用户无法覆盖升级！
                |
                |  期望 (发布密钥): $expectedCertSha256
                |  实际            : $actual
                |
                |请确认 QQBOT_STORE_FILE 指向的是仓库配套的 keystore.jks。
                """.trimMargin()
            )
        }

        logger.lifecycle("[签名校验通过] 证书 SHA-256 = $actual")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
}
