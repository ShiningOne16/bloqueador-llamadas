package com.ejemplo.bloqueador

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
