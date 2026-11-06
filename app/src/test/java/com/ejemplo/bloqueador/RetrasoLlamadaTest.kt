package com.ejemplo.bloqueador

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RetrasoLlamadaTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        init { registry.addObserver(RetrasoLlamada) }
    }

    @After fun cleanup() = RetrasoLlamada.cancelar()

    @Test fun callsUseTheSavedDurationAndKeepItUntilTheyAreCancelled() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = Prefs(context)
        prefs.delayCall = true
        for (seconds in 1..10) {
            prefs.delayCallSeconds = seconds
            assertEquals(seconds, Prefs(context).delayCallSeconds)
            Acciones.llamar(context, "5512345678")
            assertEquals(seconds * 1000, RetrasoLlamada.esperaMs)
            prefs.delayCallSeconds = if (seconds == 10) 1 else 10
            RetrasoLlamada.pedir("5598765432", prefs.delayCallSeconds)
            assertEquals("5512345678", RetrasoLlamada.pendiente)
            assertEquals(seconds * 1000, RetrasoLlamada.esperaMs)
            RetrasoLlamada.cancelar()
        }
    }

    @Test fun defaultsToTwoSecondsAndClampsInvalidDurations() {
        val prefs = Prefs(RuntimeEnvironment.getApplication())
        assertFalse(prefs.delayCall)
        assertEquals(2, prefs.delayCallSeconds)
        for ((requested, expected) in listOf(0 to 1, Int.MIN_VALUE to 1, 11 to 10, Int.MAX_VALUE to 10)) {
            prefs.delayCallSeconds = requested
            assertEquals(expected, prefs.delayCallSeconds)
            RetrasoLlamada.pedir("5512345678", requested)
            assertEquals(expected * 1000, RetrasoLlamada.esperaMs)
            RetrasoLlamada.cancelar()
        }
    }

    @Test fun leavingTheActivityCancelsTheCallAndReturningDoesNotRestoreIt() {
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.RESUMED
        RetrasoLlamada.pedir("5512345678")
        assertEquals("5512345678", RetrasoLlamada.pendiente)
        owner.registry.currentState = Lifecycle.State.CREATED
        assertNull(RetrasoLlamada.pendiente)
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertNull(RetrasoLlamada.pendiente)
    }

    @Test fun destroyingAndRecreatingTheActivityDoesNotRestoreTheCall() {
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.RESUMED
        RetrasoLlamada.pedir("5512345678")
        owner.registry.currentState = Lifecycle.State.DESTROYED
        Owner().registry.currentState = Lifecycle.State.RESUMED
        assertNull(RetrasoLlamada.pendiente)
    }

    @Test fun destructionAlsoClearsAPendingCallBeforeTheActivityWasStarted() {
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.CREATED
        RetrasoLlamada.pedir("5512345678")
        owner.registry.currentState = Lifecycle.State.DESTROYED
        assertNull(RetrasoLlamada.pendiente)
    }
}
