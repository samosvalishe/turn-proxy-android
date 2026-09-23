import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Подпись release из keystore.properties (вне git). Нет файла - подпись через IDE.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.freeturn.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.freeturn.app"
        // gomobile собирает ядро с -androidapi 24.
        minSdk = 24
        targetSdk = 37
        versionName = "5.0.0" // x-release-please-version
        // Производный от versionName (M*10000+m*100+p) - release-please бампит только строку версии
        versionCode = versionName!!.split(".").let { (ma, mi, pa) ->
            ma.toInt() * 10000 + mi.toInt() * 100 + pa.toInt()
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    packaging {
        resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }

    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "FreeTurn Debug")
        }
        release {
            resValue("string", "app_name", "FreeTurn")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // SshRepository использует java.time, поэтому нужен desugaring.
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

composeCompiler {
    if (project.findProperty("composeReports") == "true") {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

dependencies {
    // Ядро + gomobile-биндинг; файл кладёт fetchFreeturnAar
    implementation(files(layout.projectDirectory.file("libs/freeturn.aar")))

    implementation(libs.androidx.core.splashscreen)
    implementation(libs.jsch)
    implementation(libs.bouncycastle)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
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
    implementation(libs.koin.androidx.compose)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.koin.test)
}

/**
 * Тянет из релиза free-turn-proxy `freeturn.aar` (ядро + gomobile-биндинг) и `install.sh`
 * (серверный RPC, стримится по SSH) в app/libs. Версия общая: протокол скрипта - часть релиза.
 * `local` - брать уже лежащие app/libs/freeturn.aar и app/libs/install.sh, в сеть не ходить.
 */
abstract class FetchFreeturnAar : DefaultTask() {
    @get:Input
    abstract val repo: Property<String>

    /** "latest", "local" или конкретный тег/версия ("2.1.0", "v2.1.0"). */
    @get:Input
    abstract val version: Property<String>

    @get:Input
    @get:Optional
    abstract val token: Property<String>

    /** Вне build/ - переживает clean, общий на все проекты. */
    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputFile
    abstract val aarFile: RegularFileProperty

    @get:OutputFile
    abstract val scriptFile: RegularFileProperty

    /** Generated assets варианта; путь задаёт AGP. */
    @get:OutputDirectory
    abstract val assetsDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val aar = aarFile.get().asFile
        val script = scriptFile.get().asFile
        val stamp = File(aar.parentFile, ".aar-version")
        val installed = if (stamp.isFile) stamp.readText().trim() else null

        if (version.get().trim().equals("local", ignoreCase = true)) {
            listOf(aar, script).firstOrNull { !it.isFile }?.let {
                throw GradleException("FreeTurn: freeturnAar=local, но ${it.path} нет")
            }
            publishScript(script)
            return
        }

        val tag = try {
            resolveTag()
        } catch (e: Exception) {
            // Оффлайн со скачанным ядром - не повод ронять сборку
            if (aar.isFile && script.isFile && installed != null) {
                logger.warn("FreeTurn: не удалось узнать версию (${e.message}), оставляю $installed")
                publishScript(script)
                return
            }
            throw GradleException("FreeTurn: не удалось определить версию из ${repo.get()}: ${e.message}", e)
        }

        if (tag == installed && aar.isFile && script.isFile) {
            publishScript(script)
            return
        }

        val base = "https://github.com/${repo.get()}/releases/download/$tag"
        val cache = File(cacheDir.get().asFile, tag).apply { mkdirs() }
        val sums = cachedFile(File(cache, "checksums.txt"), "$base/checksums.txt")
            .readLines()
            .mapNotNull { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size == 2) p[1].removePrefix("*") to p[0] else null
            }.toMap()

        for ((asset, dest) in listOf(AAR to aar, SCRIPT to script)) {
            val src = cachedFile(File(cache, asset), "$base/$asset")
            val expected = sums[asset]
                ?: throw GradleException("FreeTurn: $asset нет в checksums.txt релиза $tag")
            val actual = sha256(src)
            if (!actual.equals(expected, ignoreCase = true)) {
                src.delete()
                throw GradleException("FreeTurn: sha256 $asset не сошёлся ($actual != $expected)")
            }
            dest.parentFile.mkdirs()
            src.copyTo(dest, overwrite = true)
        }

        stamp.writeText(tag)
        publishScript(script)
        logger.lifecycle("FreeTurn: $tag -> ${aar.parentFile.path}")
    }

    private fun publishScript(script: File) {
        script.copyTo(assetsDir.get().file(SCRIPT).asFile, overwrite = true)
    }

    private fun resolveTag(): String {
        val v = version.get().trim()
        if (!v.equals("latest", ignoreCase = true)) return if (v.startsWith("v")) v else "v$v"
        val json = String(
            httpGet("https://api.github.com/repos/${repo.get()}/releases/latest", "application/vnd.github+json"),
            Charsets.UTF_8
        )
        return Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            ?: throw GradleException("нет tag_name в ответе GitHub API")
    }

    private fun cachedFile(dest: File, url: String): File {
        if (dest.isFile && dest.length() > 0) return dest
        val tmp = File(dest.parentFile, "${dest.name}.part")
        tmp.writeBytes(httpGet(url, "application/octet-stream"))
        tmp.renameTo(dest)
        return dest
    }

    private fun httpGet(url: String, accept: String): ByteArray {
        var current = URI(url).toURL()
        repeat(6) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 300_000
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "freeturn-android-build")
                // Токен только на github.com - на редиректе в CDN он ломает подписанный URL
                val t = token.orNull
                if (!t.isNullOrBlank() && current.host.endsWith("github.com")) {
                    setRequestProperty("Authorization", "Bearer $t")
                }
            }
            try {
                when (val code = conn.responseCode) {
                    in 200..299 -> return conn.inputStream.use { it.readBytes() }
                    301, 302, 303, 307, 308 -> {
                        val loc = conn.getHeaderField("Location")
                            ?: throw GradleException("редирект без Location на $current")
                        current = URI(current.toURI().resolve(loc).toString()).toURL()
                    }
                    else -> throw GradleException("HTTP $code на $current")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw GradleException("слишком много редиректов на $url")
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val AAR = "freeturn.aar"
        const val SCRIPT = "install.sh"
    }
}

/**
 * `app/libs/server-linux-*` -> assets/server debug-сборки с `freeturnAar=local`: "Обновить"
 * в хабе сервера загружает их по SSH вместо релиза (отладка серверной части ядра).
 */
abstract class LocalServerBinaries : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val binaries: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val assetsDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = assetsDir.get().dir("server").asFile
        out.deleteRecursively()
        out.mkdirs()
        for (f in binaries.files) {
            val asset = assetName(f.name)
            if (asset == null) {
                logger.warn("FreeTurn: ${f.name} - неизвестная архитектура, пропускаю")
                continue
            }
            f.copyTo(File(out, asset), overwrite = true)
            logger.lifecycle("FreeTurn: ${f.name} -> assets/server/$asset")
        }
        if (out.list().isNullOrEmpty()) {
            logger.warn("FreeTurn: в app/libs нет server-linux-* - \"Обновить\" поставит релиз")
        }
    }

    // Имя ассета релиза (как server_asset в install.sh), в т.ч. из имён dist/ goreleaser:
    // server_linux_arm64_v8.0, server_linux_amd64_v1, server_linux_arm_7.
    private fun assetName(name: String): String? {
        val arch = Regex("^server[-_]linux[-_](.+)$").find(name)?.groupValues?.get(1) ?: return null
        return "server-linux-" + when {
            arch.startsWith("amd64") -> "amd64"
            arch.startsWith("arm64") -> "arm64"
            arch == "armv7" || arch.startsWith("arm_7") -> "armv7"
            arch.startsWith("386") -> "386"
            arch.startsWith("riscv64") -> "riscv64"
            else -> return null
        }
    }
}

val freeturnAarVersion: Provider<String> = providers.gradleProperty("freeturnAar")
    .orElse(providers.environmentVariable("FREETURN_AAR_VERSION"))
    .orElse("latest")

val fetchFreeturnAar = tasks.register<FetchFreeturnAar>("fetchFreeturnAar") {
    description = "Качает freeturn.aar и install.sh из релизов free-turn-proxy в app/libs"
    group = "build"
    repo.set(providers.gradleProperty("freeturnAarRepo").orElse("samosvalishe/free-turn-proxy"))
    version.set(freeturnAarVersion)
    token.set(providers.environmentVariable("GITHUB_TOKEN"))
    cacheDir.set(layout.dir(provider { File(gradle.gradleUserHomeDir, "caches/freeturn-core") }))
    aarFile.set(layout.projectDirectory.file("libs/freeturn.aar"))
    scriptFile.set(layout.projectDirectory.file("libs/install.sh"))
    // Версия резолвится в рантайме - актуальность решает stamp-файл внутри таски
    outputs.upToDateWhen { false }
}

// AAR нужен на компиляции, а не на упаковке - качаем в любой сборке.
// matching, а не named - таски вариантов AGP создаёт позже конфигурации скрипта.
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchFreeturnAar) }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            fetchFreeturnAar,
            FetchFreeturnAar::assetsDir
        )
        if (variant.buildType == "debug" && freeturnAarVersion.get().trim().equals("local", ignoreCase = true)) {
            val bins = tasks.register<LocalServerBinaries>("${variant.name}LocalServerBinaries") {
                binaries.from(fileTree("libs") { include("server-linux*", "server_linux*") })
            }
            variant.sources.assets?.addGeneratedSourceDirectory(bins, LocalServerBinaries::assetsDir)
        }
    }
}
