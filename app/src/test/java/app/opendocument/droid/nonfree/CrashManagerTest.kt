package app.opendocument.droid.nonfree

import org.junit.Assert.assertSame
import org.junit.Test

class CrashManagerTest {
    @Test
    fun repeatedInitializationDoesNotChainExceptionHandlers() {
        CrashManager().initialize()
        val installed = Thread.getDefaultUncaughtExceptionHandler()

        repeat(100) { CrashManager().initialize() }

        assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
    }
}
