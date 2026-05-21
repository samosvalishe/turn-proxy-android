import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.freeturn.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.freeturn.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 22
        versionName = "2.4.0"
    }

    packaging {
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        jniLibs.useLegacyPackaging = true
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    compileSdkMinor = 1
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.jsch)
    implementation(libs.eddsa)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.wireguard.tunnel)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.nav.suite)

    debugImplementation(libs.androidx.compose.ui.tooling)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

val coreVersionsFile = layout.projectDirectory.file("core-versions.properties")

fun latestGithubTag(repo: String): String {
    val url = URL("https://api.github.com/repos/$repo/releases/latest")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 4_000
        readTimeout = 4_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "FreeTurn-Android-build")
    }
    return connection.inputStream.bufferedReader().use { reader ->
        val body = reader.readText()
        Regex(""""tag_name"\s*:\s*"([^"]+)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("tag_name not found for $repo")
    }
}

fun normalizeTag(value: String): String =
    value.trim().removePrefix("refs/tags/").removePrefix("release-")

tasks.register("checkCoreUpdates") {
    group = "verification"
    description = "Checks GitHub releases for libvkturn.so and libxray.so updates."

    inputs.file(coreVersionsFile)

    doLast {
        val props = Properties().apply {
            coreVersionsFile.asFile.inputStream().use(::load)
        }
        val cores = listOf("vkturn", "xray")
        cores.forEach { id ->
            val repo = props.getProperty("$id.repo").orEmpty()
            val current = props.getProperty("$id.current").orEmpty()
            if (repo.isBlank()) {
                logger.warn("Core update check: $id repo is not configured")
                return@forEach
            }
            runCatching { latestGithubTag(repo) }
                .onSuccess { latest ->
                    val currentNorm = normalizeTag(current)
                    val latestNorm = normalizeTag(latest)
                    when {
                        current.isBlank() || current.equals("unknown", ignoreCase = true) ->
                            logger.warn("Core update check: $id latest is $latest, current version is unknown")
                        currentNorm != latestNorm ->
                            logger.warn("Core update check: $id update available: current=$current latest=$latest")
                        else ->
                            logger.lifecycle("Core update check: $id is up to date ($current)")
                    }
                }
                .onFailure { e ->
                    logger.warn("Core update check: cannot check $id ($repo): ${e.message}")
                }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("checkCoreUpdates")
}
