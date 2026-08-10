import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
    alias(libs.plugins.kspPlugin)
    alias(libs.plugins.roomPlugin)
}

android {
    namespace = "dev.reprotrail.runtime.data"
}

libraryConfig {
    moduleType.set(ModuleType.DATA)
    internalDependencies.set(
        listOf(
            ":runtime:domain",
        ),
    )
}

dependencies {
    implementation(libs.kotlinxCoroutinesCoreLibrary)
    implementation(libs.bundles.roomBundle)
    ksp(libs.androidxRoomCompilerLibrary)

    androidTestImplementation(libs.androidxRoomTestingLibrary)

    androidTestImplementation(libs.androidxJunitLibrary)
    androidTestImplementation(libs.androidxTestCoreLibrary)
    androidTestImplementation(libs.androidxTestRunnerLibrary)
    androidTestImplementation(libs.kotlinxCoroutinesTestLibrary)
}
