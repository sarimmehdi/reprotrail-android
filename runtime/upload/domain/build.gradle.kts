import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "dev.reprotrail.runtime.upload.domain"
}

libraryConfig {
    // This is an SDK-internal boundary, so it must not inherit the app feature's :utils dependency.
    moduleType.set(ModuleType.SDK)
}
