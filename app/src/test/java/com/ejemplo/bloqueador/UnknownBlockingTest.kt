package com.ejemplo.bloqueador

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.telecom.Call
import android.telecom.TelecomManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UnknownBlockingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val number = "5512345678"
    private lateinit var service: BlockerService
    private lateinit var contacts: UnknownBlockingContactsProvider

    @Before fun setup() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
        contacts = Robolectric.buildContentProvider(UnknownBlockingContactsProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("com.android.contacts", contacts)
        service = Robolectric.buildService(BlockerService::class.java).create().get()
    }

    private fun decision(hidden: Boolean = false): Veredicto {
        val details = ReflectionHelpers.newInstance(Call.Details::class.java)
        ReflectionHelpers.setField(details, "mHandle", Uri.parse("tel:$number"))
        ReflectionHelpers.setField(details, "mHandlePresentation",
            if (hidden) TelecomManager.PRESENTATION_RESTRICTED else TelecomManager.PRESENTATION_ALLOWED)
        return service.decidir(details)
    }

    @Test fun upgradeKeepsExistingBlockingAndMasterPause() {
        val prefs = Prefs(context)
        assertTrue(prefs.blockUnknown)
        assertEquals(Veredicto.BLOQUEAR, decision())
        prefs.enabled = false
        assertEquals(Veredicto.PASAR, decision())
        assertTrue(PremiumStore(context).blockUnknown())
        assertFalse(Prefs(context).enabled)
    }

    @Test fun premiumSwitchAllowsUnknownCallsWithoutDisablingOtherFilters() {
        PremiumStore(context).setBlockUnknown(false)
        assertFalse(PremiumStore(context).blockUnknown())
        assertTrue(Prefs(context).enabled)
        assertEquals(Veredicto.PASAR, decision())
        assertTrue(Prefs(context).blockedCalls().isEmpty())
        PremiumStore(context).setBlockUnknown(true)
        assertEquals(Veredicto.BLOQUEAR, decision())
    }

    @Test fun manualAndPrivateBlocksStillApplyWhenUnknownBlockingIsOff() {
        PremiumStore(context).setBlockUnknown(false)
        Prefs(context).setManualBlock(number, true)
        assertEquals(Veredicto.BLOQUEAR, decision())
        Prefs(context).setManualBlock(number, false)
        assertEquals(Veredicto.BLOQUEAR, decision(hidden = true))
        Prefs(context).blockHidden = false
        assertEquals(Veredicto.PASAR, decision(hidden = true))
    }

    @Test fun contactsAndMissingPermissionAreNeverBlockedAsUnknown() {
        contacts.known = true
        assertEquals(Veredicto.PASAR, decision())
        contacts.known = false
        shadowOf(context).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertEquals(Veredicto.PASAR, decision())
    }

    @Test fun premiumSwitchChangesOnlyTheSelectedProfile() {
        Prefs(context).blockUnknown = false
        val profile = ProtectionProfile("active", "Activo")
        val other = ProtectionProfile("other", "Otro")
        val store = PremiumStore(context)
        store.save(PremiumConfiguration(listOf(profile, other), profile.id))
        assertTrue(store.blockUnknown())
        assertEquals(Veredicto.BLOQUEAR, decision())
        val updated = store.setBlockUnknown(false)
        assertFalse(updated.activeProfile!!.blockUnknown)
        assertTrue(updated.profiles.single { it.id == other.id }.blockUnknown)
        assertFalse(Prefs(context).blockUnknown)
        assertEquals(Veredicto.PASAR, decision())
        store.setBlockUnknown(true)
        assertEquals(Veredicto.BLOQUEAR, decision())
        store.save(store.read().copy(activeProfileId = null))
        assertFalse(store.blockUnknown())
        assertEquals(Veredicto.PASAR, decision())
    }

    @Test fun profileExceptionsStillOverrideUnknownBlocking() {
        val profile = ProtectionProfile("p", "Perfil", exceptions = listOf(
            NumberException(number, action = NumberAction.ALLOW)))
        PremiumStore(context).save(PremiumConfiguration(listOf(profile), profile.id))
        assertEquals(Veredicto.PASAR, decision())
        PremiumStore(context).setBlockUnknown(false)
        PremiumStore(context).save(PremiumConfiguration(listOf(profile.copy(blockUnknown = false,
            exceptions = listOf(NumberException(number, action = NumberAction.BLOCK)))), profile.id))
        assertEquals(Veredicto.BLOQUEAR, decision())
    }

    @Test fun temporaryPermissionAndFocusKeepTheirPriorities() {
        val temporal = TemporalStore(context)
        val now = System.currentTimeMillis()
        temporal.permitirDesconocidos(30, now)
        assertEquals(Veredicto.PASAR, decision())
        PremiumStore(context).setBlockUnknown(false)
        temporal.iniciarConcentracion(60, now)
        assertEquals(Veredicto.BLOQUEAR, decision())
        temporal.permitirNumero(number, "Permitido", 30, now)
        assertEquals(Veredicto.PASAR, decision())
    }
}

class UnknownBlockingContactsProvider : ContentProvider() {
    var known = false
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/contact"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?) =
        MatrixCursor(arrayOf("_id")).apply { if (known) addRow(arrayOf(1L)) }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}
