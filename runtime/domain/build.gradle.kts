import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "dev.reprotrail.runtime.domain"
}

libraryConfig {
    moduleType.set(ModuleType.DOMAIN)
}
