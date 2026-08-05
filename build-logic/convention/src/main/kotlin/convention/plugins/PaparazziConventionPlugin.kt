package convention.plugins

import com.android.build.api.dsl.LibraryExtension
import convention.utils.Config
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class PaparazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("app.cash.paparazzi")
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    compileSdk = Config.COMPILE_SDK
                }
            }
            tasks.configureEach {
                val isAndroidLintTask =
                    name == "lint" ||
                        name.startsWith("lint") ||
                        name.startsWith("updateLint")
                val exactNames =
                    setOf(
                        "checkDebugAarMetadata",
                        "checkReleaseAarMetadata",
                        "bundleDebugAar",
                        "bundleReleaseAar",
                        "bundleDebugLocalLintAar",
                        "bundleReleaseLocalLintAar",
                        "assembleDebug",
                        "assembleRelease",
                    )
                if (name in exactNames || isAndroidLintTask) {
                    enabled = false
                }
            }
        }
    }
}
