import com.android.build.gradle.BaseExtension
import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

/**
 * Converts a camelCase or mixedCase string to ENV_VAR_STYLE (uppercase with underscores).
 * Example: githubAccessToken -> GITHUB_ACCESS_TOKEN
 */
fun String.toEnvVarStyle(): String =
    this.replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .uppercase()

/**
 * Note: To configure GitHub credentials, you have to generate an access token with at least
 * `read:packages` scope at https://github.com/settings/tokens/new and then
 * add it to any of the following:
 *
 * - Add `ghUsername` and `ghAccessToken` to Global Gradle Properties
 * - Set `GH_USERNAME` and `GH_ACCESS_TOKEN` in your environment variables or
 * - Create a `github.properties` file in your project folder with the following content:
 *      ghUsername=&lt;YOUR_GITHUB_USERNAME&gt;
 *      ghAccessToken=&lt;YOUR_GITHUB_ACCESS_TOKEN&gt;
 */
// Load GitHub credentials from properties file, gradle properties, or environment variables
fun getGithubProperty(key: String): String {
    val githubProperties = Properties().apply {
        val file = rootProject.file("github.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
    return githubProperties.getProperty(key)
        ?: rootProject.findProperty(key)?.toString()
        ?: System.getenv(key.toEnvVarStyle())
        ?: throw GradleException("GitHub $key not found")
}

val githubUsername = getGithubProperty("ghUsername")
val githubAccessToken = getGithubProperty("ghAccessToken")

allprojects {
    repositories {
        google()
        mavenLocal()
        mavenCentral()
        maven("https://jitpack.io")
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-androidx")
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-material-components-android")
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/oneui-design")
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
    }
}

plugins.withId("com.android.base") {
    project.extensions.findByType(BaseExtension::class.java)?.apply {
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }
}
