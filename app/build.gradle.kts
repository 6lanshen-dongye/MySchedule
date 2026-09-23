import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/* 发布签名:密钥与密码只放在本机的 local.properties(已被 .gitignore 排除),不进仓库。
   没有配置时 release 直接不签名,任何人都能 clone 下来自己构建。 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(key: String): String? = keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.myschedule.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myschedule.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    val ksPath = prop("RELEASE_STORE_FILE")
    val ksFile = if (ksPath != null) rootProject.file(ksPath) else null
    val canSign = ksFile != null && ksFile.exists() &&
            prop("RELEASE_STORE_PASSWORD") != null && prop("RELEASE_KEY_ALIAS") != null

    if (canSign) {
        signingConfigs {
            create("release") {
                storeFile = ksFile
                storePassword = prop("RELEASE_STORE_PASSWORD")
                keyAlias = prop("RELEASE_KEY_ALIAS")
                keyPassword = prop("RELEASE_KEY_PASSWORD") ?: prop("RELEASE_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
