import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ============================================================================
// 绛惧悕锛氬敮涓€鍙俊鏉ユ簮锛堥槻姝㈢敤閿欏瘑閽ユ墦鍑恒€屾棤娉曡鐩栧畨瑁呫€嶇殑鍖咃級
// ----------------------------------------------------------------------------
// 鏈粨搴撴墍鏈夊澶栧垎鍙戠殑 APK 蹇呴』鐢卞悓涓€鎶婂瘑閽ョ鍚嶏紝鍚﹀垯宸插畨瑁呯敤鎴锋棤娉曡鐩栧崌绾с€?// 杩欐妸瀵嗛挜鐨勮瘉涔︽寚绾瑰浐瀹氬湪涓嬮潰锛孋I 涓庢湰鍦版瀯寤洪兘浼氭牎楠岋細
//
//   CN=QQBotQQ, OU=Local, O=Private, L=Local, ST=Local, C=CN
//   SHA-256: a695b1e8f15e40b6e5219d6dd9d41c68985b7907cfd669907cd878cc9685e005
//
// 鎸囩汗鏄叕寮€淇℃伅锛堟瘡涓凡绛惧悕鐨?APK 閲岄兘鏈夛級锛屾斁杩涗粨搴撴病鏈夐棶棰橈紱
// 瀵嗛挜鏂囦欢涓庡彛浠ゆ槸鏈哄瘑锛屽彧瀛樺湪浜庯細
//   - CI锛欸itHub Actions secrets锛圞EYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_PASSWORD / KEY_ALIAS锛?//   - 鏈湴锛歬eystore.jks锛堝凡 gitignore锛?+ local.properties 閲岀殑鍙ｄ护锛堝凡 gitignore锛?//
// 鏈湴鎯充骇鍑哄彲瀹夎鐨?release 鍖咃紝鍦?local.properties 閲屽姞鍥涜鍗冲彲锛?//   QQBOT_STORE_FILE=../keystore.jks
//   QQBOT_STORE_PASSWORD=***
//   QQBOT_KEY_ALIAS=***
//   QQBOT_KEY_PASSWORD=***
// 涔熷彲浠ョ敤鍚屽悕鐜鍙橀噺浠ｆ浛锛圕I 灏辨槸杩欎箞浼犵殑锛夈€?// ============================================================================

val expectedCertSha256 = "a695b1e8f15e40b6e5219d6dd9d41c68985b7907cfd669907cd878cc9685e005"

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/** 鍙栫鍚嶉厤缃細鐜鍙橀噺浼樺厛浜?local.properties */
fun secret(key: String): String? =
    (System.getenv(key) ?: localProps.getProperty(key))?.trim()?.takeIf { it.isNotEmpty() }

/** storeFile 鍏佽鍐欑浉瀵硅矾寰勶紝鐩稿**鏍圭洰褰?*瑙ｆ瀽 */
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
        "[绛惧悕] 鏈厤缃鍚嶅嚟鎹?鈫?release 浜х墿灏嗘槸**鏈鍚?* APK锛屾棤娉曞畨瑁呫€俓n" +
        "        鏈湴鏋勫缓璇峰湪 local.properties 閰嶇疆 QQBOT_STORE_FILE / QQBOT_STORE_PASSWORD /\n" +
        "        QQBOT_KEY_ALIAS / QQBOT_KEY_PASSWORD锛堣鏂囦欢椤堕儴娉ㄩ噴锛夈€俓n" +
        "        CI 鐢?secrets 娉ㄥ叆鍚屾牱鐨勫洓涓€笺€?
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
            // v2/v3 閮藉紑锛屼繚璇?Android 7+ 鍒版渶鏂扮郴缁熼兘鑳借
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
            // 娌℃湁鍑嵁鏃朵笉鎸囧畾绛惧悕 鈫?浜у嚭鏈鍚嶅寘锛圕I 浼氬湪鏋勫缓鍚庣敤 apksigner 琛ョ锛?            if (releaseSigning != null) signingConfig = releaseSigning
        }
    }

    // 浜х墿缁熶竴鍛藉悕涓?QQBot.apk / QQBot-debug.apk锛屼笉鍐嶅嚭鐜?app-release 绛夊瓧鐪?    applicationVariants.all {
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
 * 鏍￠獙 release APK 鐨勭鍚嶈瘉涔︽寚绾广€? *
 * 鐢ㄦ硶锛歚./gradlew verifyReleaseSignature`锛堜細鍏堟瀯寤?release锛? * 浠讳綍涓€鐜鍚嶄笉瀵癸紙鏈鍚?/ 鎹簡瀵嗛挜 / 璇敤 debug 瀵嗛挜锛夐兘浼氬湪杩欓噷鐩存帴澶辫触锛? * 鑰屼笉鏄瓑鐢ㄦ埛瑁呭埌鎵嬫満涓婃墠鍙戠幇銆屽簲鐢ㄦ湭瀹夎銆嶃€? */
tasks.register("verifyReleaseSignature") {
    group = "verification"
    description = "鏍￠獙 release APK 鐨勭鍚嶈瘉涔︽槸鍚︿负鍙戝竷瀵嗛挜"
    dependsOn("assembleRelease")

    doLast {
        val apk = file("build/outputs/apk/release/QQBot.apk")
        if (!apk.exists()) throw GradleException("鎵句笉鍒?APK锛?apk")

        val sdkDir = sequenceOf(
            localProps.getProperty("sdk.dir"),
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
        ).filterNotNull().firstOrNull { it.isNotBlank() }
            ?: throw GradleException("鎵句笉鍒?Android SDK 璺緞锛坰dk.dir / ANDROID_HOME锛?)

        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val apksignerName = if (isWindows) "apksigner.bat" else "apksigner"
        val buildTools = File(sdkDir, "build-tools")
            .listFiles { f -> f.isDirectory }
            ?.maxByOrNull { it.name }
            ?: throw GradleException("鎵句笉鍒?build-tools 鐩綍锛?sdkDir/build-tools")
        val apksigner = File(buildTools, apksignerName)
        if (!apksigner.exists()) throw GradleException("鎵句笉鍒?apksigner锛?apksigner")

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
                |release APK 鏈鍚嶏紝瑁呬笉涓婏紒
                |
                |鏈湴鏋勫缓璇峰厛鍦?local.properties 閰嶇疆绛惧悕鍑嵁锛?                |  QQBOT_STORE_FILE=../keystore.jks
                |  QQBOT_STORE_PASSWORD=***
                |  QQBOT_KEY_ALIAS=***
                |  QQBOT_KEY_PASSWORD=***
                |
                |apksigner 杈撳嚭锛?                |$output
                """.trimMargin()
            )
        }

        if (!actual.equals(expectedCertSha256, ignoreCase = true)) {
            throw GradleException(
                """
                |绛惧悕璇佷功涓嶅尮閰嶏紝杩欎釜鍖呬細瀵艰嚧宸插畨瑁呯敤鎴锋棤娉曡鐩栧崌绾э紒
                |
                |  鏈熸湜 (鍙戝竷瀵嗛挜): $expectedCertSha256
                |  瀹為檯            : $actual
                |
                |璇风‘璁?QQBOT_STORE_FILE 鎸囧悜鐨勬槸浠撳簱閰嶅鐨?keystore.jks銆?                """.trimMargin()
            )
        }

        logger.lifecycle("[绛惧悕鏍￠獙閫氳繃] 璇佷功 SHA-256 = $actual")
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
