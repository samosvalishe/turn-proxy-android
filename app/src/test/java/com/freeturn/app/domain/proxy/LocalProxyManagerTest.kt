package com.freeturn.app.domain.proxy

import com.freeturn.app.data.config.ClientConfig
import com.freeturn.app.domain.ConnectionStats
import com.freeturn.app.domain.ProxyState
import com.freeturn.app.domain.StartupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalProxyManagerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val validCfg = ClientConfig(serverAddress = "1.2.3.4", vkLink = "https://vk.com/x")

    private class FakeLauncher : ProxyServiceLauncher {
        var started = false
        var stopped = false
        override fun start() { started = true }
        override fun stop() { stopped = true }
    }

    private class ThrowingLauncher : ProxyServiceLauncher {
        override fun start(): Unit = throw IllegalStateException("not allowed")
        override fun stop() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        resetProxyState()
    }

    @After
    fun tearDown() {
        resetProxyState()
        Dispatchers.resetMain()
    }

    private fun resetProxyState() {
        ProxyServiceState.setRunning(false)
        ProxyServiceState.setStartupResult(null)
        ProxyServiceState.setConnectionStats(ConnectionStats.IDLE)
        ProxyServiceState.setCaptchaSession(null)
        ProxyServiceState.clearConnectedSince()
        ProxyServiceState.clearLogs()
    }

    // Регрессия P1 #2: FGS-фейл пишет Failed при isRunning == false - раньше менеджер ждал 5 минут.
    @Test
    fun fgsStartFailureReportsErrorWithoutWaiting() = runTest(dispatcher) {
        val launcher = FakeLauncher()
        val mgr = LocalProxyManager(launcher)

        val job = launch { mgr.startProxy(validCfg) }
        runCurrent()
        assertTrue(launcher.started)

        // startForeground упал: сервис зафиксировал ошибку, isRunning так и не стал true.
        ProxyServiceState.setStartupResult(StartupResult.Failed("FGS start failed"))
        runCurrent()

        val state = mgr.proxyState.value
        assertTrue("ожидался Error, было $state", state is ProxyState.Error)
        assertEquals("FGS start failed", (state as ProxyState.Error).message)
        assertTrue(launcher.stopped)
        job.cancel()
    }

    @Test
    fun launcherExceptionReportsErrorImmediately() = runTest(dispatcher) {
        val mgr = LocalProxyManager(ThrowingLauncher())

        mgr.startProxy(validCfg)

        val state = mgr.proxyState.value
        assertTrue("ожидался Error, было $state", state is ProxyState.Error)
        assertEquals("not allowed", (state as ProxyState.Error).message)
    }

    @Test
    fun successfulStartupTransitionsToRunning() = runTest(dispatcher) {
        val launcher = FakeLauncher()
        val mgr = LocalProxyManager(launcher)

        val job = launch { mgr.startProxy(validCfg) }
        runCurrent()

        ProxyServiceState.setConnectionStats(ConnectionStats(active = 1, total = 1))
        ProxyServiceState.setRunning(true)
        ProxyServiceState.setStartupResult(StartupResult.Success)
        runCurrent()

        val state = mgr.proxyState.value
        assertTrue("ожидался Running, было $state", state is ProxyState.Running)
        assertEquals(1, (state as ProxyState.Running).active)
        job.cancel()
    }
}
