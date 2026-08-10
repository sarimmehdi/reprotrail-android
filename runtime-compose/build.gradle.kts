import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.kotlinComposePlugin)
}

android {
    namespace = "dev.reprotrail.runtime.compose"
}

libraryConfig {
    moduleType.set(ModuleType.SDK)
}

dependencies {
    api(project(":runtime"))
    implementation(platform(libs.androidxComposeBomLibrary))
    implementation(libs.androidxComposeUiLibrary)

    androidTestImplementation(platform(libs.androidxComposeBomLibrary))
    androidTestImplementation(libs.androidxActivityComposeLibrary)
    androidTestImplementation(libs.androidxComposeMaterial3Library)
    androidTestImplementation(libs.androidxComposeUiTestJunit4Library)
    androidTestImplementation(libs.androidxEspressoCoreLibrary)
    androidTestImplementation(libs.androidxJunitLibrary)
    debugImplementation(libs.androidxComposeUiTestManifestLibrary)
}
