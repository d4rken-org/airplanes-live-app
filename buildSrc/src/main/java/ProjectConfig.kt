import com.android.build.api.dsl.Packaging
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import java.io.File
import java.io.FileInputStream
import java.util.Properties

object ProjectConfig {
    const val packageName = "eu.darken.apl"

    const val minSdk = 26
    const val compileSdk = 36
    const val targetSdk = 34

    object Version {
        val versionProperties = Properties().apply {
            var currentDir = File(System.getProperty("user.dir"))
            var versionFile = File(currentDir, "version.properties")

            if (!versionFile.exists()) {
                currentDir = currentDir.parentFile
                versionFile = File(currentDir, "version.properties")
            }

            if (!versionFile.exists()) {
                throw IllegalStateException("Could not find version.properties in ${currentDir.absolutePath}")
            }

            load(FileInputStream(versionFile))
        }
        val major = versionProperties.getProperty("project.versioning.major").toInt()
        val minor = versionProperties.getProperty("project.versioning.minor").toInt()
        val patch = versionProperties.getProperty("project.versioning.patch").toInt()
        val build = versionProperties.getProperty("project.versioning.build").toInt()

        val name = "${major}.${minor}.${patch}-rc${build}"
        val code = major * 10000000 + minor * 100000 + patch * 1000 + build * 10
    }
}

/**
 * Configures the Kotlin compiler options.
 */
private fun LibraryExtension.compilerOptions(configure: Action<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions>): Unit =
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("compilerOptions", configure)

fun LibraryExtension.setupLibraryDefaults() {
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xopt-in=kotlinx.coroutines.FlowPreview",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xopt-in=kotlin.RequiresOptIn"
        )
    }

    fun configurePackaging(packaging: Packaging) {
        packaging.resources.excludes += "DebugProbesKt.bin"
    }
}

fun com.android.build.api.dsl.SigningConfig.setupCredentials(
    signingPropsPath: File? = null
) {

    val keyStoreFromEnv = System.getenv("STORE_PATH")?.let { File(it) }

    if (keyStoreFromEnv?.exists() == true) {
        println("Using signing data from environment variables.")
        storeFile = keyStoreFromEnv
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    } else {
        println("Using signing data from properties file.")
        val props = Properties().apply {
            signingPropsPath?.takeIf { it.canRead() }?.let { load(FileInputStream(it)) }
        }

        val keyStorePath = props.getProperty("release.storePath")?.let { File(it) }

        if (keyStorePath?.exists() == true) {
            storeFile = keyStorePath
            storePassword = props.getProperty("release.storePassword")
            keyAlias = props.getProperty("release.keyAlias")
            keyPassword = props.getProperty("release.keyPassword")
        }
    }
}

fun getBugSnagApiKey(
    propertiesPath: File?
): String? {
    val bugsnagProps = Properties().apply {
        propertiesPath?.takeIf { it.canRead() }?.let { load(FileInputStream(it)) }
    }

    return System.getenv("BUGSNAG_API_KEY") ?: bugsnagProps.getProperty("bugsnag.apikey")
}
