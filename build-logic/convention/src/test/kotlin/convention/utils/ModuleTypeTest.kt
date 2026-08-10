package convention.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleTypeTest {
    @Test
    fun `sdk modules remain independent of app utils`() {
        assertFalse(ModuleType.SDK.dependsOnAppUtils)
    }

    @Test
    fun `existing app module types retain their utils dependency`() {
        assertTrue(ModuleType.DOMAIN.dependsOnAppUtils)
        assertTrue(ModuleType.DATA.dependsOnAppUtils)
    }
}
