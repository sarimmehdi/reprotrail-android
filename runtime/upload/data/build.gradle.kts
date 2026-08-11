import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "dev.reprotrail.runtime.upload.data"
}

libraryConfig {
    moduleType.set(ModuleType.DATA)
    internalDependencies.set(
        listOf(
            ":runtime:upload:domain",
        ),
    )
}

dependencies {
    implementation(libs.kotlinxCoroutinesCoreLibrary)
    implementation(libs.okhttpLibrary)
    implementation(libs.retrofitLibrary)

    testImplementation(libs.mockWebServerLibrary)

    androidTestImplementation(libs.androidxJunitLibrary)
    androidTestImplementation(libs.androidxTestCoreLibrary)
    androidTestImplementation(libs.androidxTestRunnerLibrary)
    androidTestImplementation(libs.kotlinxCoroutinesTestLibrary)
}
