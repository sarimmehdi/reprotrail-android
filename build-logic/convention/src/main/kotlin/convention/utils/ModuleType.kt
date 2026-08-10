package convention.utils

enum class ModuleType {
    SDK,
    DOMAIN,
    DATA,
    DI,
    PRESENTATION,
    NAV,
    UTILS,
    UI,
}

internal val ModuleType.dependsOnAppUtils: Boolean
    get() = this != ModuleType.SDK
