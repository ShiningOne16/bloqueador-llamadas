package com.ejemplo.bloqueador

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SpamListaTest {
    private val context get() = RuntimeEnvironment.getApplication()
    companion object { private val stamp = AtomicLong(1000) }

    private fun write(text: String) {
        File(context.filesDir, "spam_list.json").apply {
            writeText(text)
            assertTrue(setLastModified(stamp.incrementAndGet()))
        }
    }

    @Test fun oldCommunityListsRemainCompatible() {
        write("""{"v":1,"generado":1,"n":[["community",1,3]]}""")
        assertEquals(mapOf("community" to 1), SpamLista.mapa(context, 100))
    }

    @Test fun tellowsExpiresEvenWhenTheFileHasNotChangedAndThereIsNoNetwork() {
        write("""{"v":1,"generado":1,"tellows_hasta":200,"n":[["community",1,3],["tellows",0,0,"tellows",8]]}""")
        assertEquals(mapOf("community" to 1, "tellows" to 0), SpamLista.mapa(context, 100))
        assertEquals(mapOf("community" to 1), SpamLista.mapa(context, 200))
        assertEquals(mapOf("community" to 1), SpamLista.mapa(context, 300))
    }

    @Test fun tellowsWithoutExpiryIsNeverUsedForBlocking() {
        write("""{"v":1,"n":[["tellows",0,0,"tellows",9]]}""")
        assertTrue(SpamLista.mapa(context, 100).isEmpty())
    }

    @Test fun aNewDownloadCanReplaceExpiredTellowsData() {
        write("""{"v":1,"tellows_hasta":100,"n":[["old",0,0,"tellows",9]]}""")
        assertTrue(SpamLista.mapa(context, 200).isEmpty())
        write("""{"v":1,"tellows_hasta":400,"n":[["new",0,0,"tellows",7]]}""")
        assertEquals(mapOf("new" to 0), SpamLista.mapa(context, 200))
    }

    @Test fun tellowsMexicoDoesNotMatchForeignCallsWithTheSameLastTenDigits() {
        val key = SpamRules.huella("5512345678")
        val until = System.currentTimeMillis() + 60_000
        write("""{"v":1,"tellows_hasta":$until,"n":[["$key",0,0,"tellows",8]]}""")
        assertEquals(CategoriaSpam.OTRO, SpamLista.categoria(context, "+525512345678"))
        assertEquals(CategoriaSpam.OTRO, SpamLista.categoria(context, "5512345678"))
        assertNull(SpamLista.categoria(context, "+15512345678"))
        assertNull(SpamLista.categoria(context, "+345512345678"))
    }

    @Test fun communityExpiresOfflineWithoutChangingTheFile() {
        val until = 100 + SpamRules.VIGENCIA_LISTA_MS
        write("""{"v":1,"generado":100,"comunidad_hasta":$until,"n":[["community",1,3]]}""")
        assertEquals(mapOf("community" to 1), SpamLista.mapa(context, 101))
        assertTrue(SpamLista.mapa(context, until).isEmpty())
        assertTrue(SpamLista.mapa(context, until + 1).isEmpty())
    }

    @Test fun legacyCommunityUsesGenerationTimeAndCannotExtendTheSevenDayLimit() {
        for (expiry in listOf("", ",\"comunidad_hasta\":9999999999999")) {
            write("""{"v":1,"generado":100$expiry,"n":[["community",1,3]]}""")
            assertEquals(mapOf("community" to 1), SpamLista.mapa(context, 101))
            assertTrue(SpamLista.mapa(context, 100 + SpamRules.VIGENCIA_LISTA_MS).isEmpty())
        }
    }

    @Test fun undatedAndZeroReportCommunityEntriesAreNotUsed() {
        write("""{"v":1,"n":[["community",1,3]]}""")
        assertTrue(SpamLista.mapa(context, 100).isEmpty())
        write("""{"v":1,"generado":1,"n":[["community",1,0]]}""")
        assertTrue(SpamLista.mapa(context, 100).isEmpty())
    }

    @Test fun oneReportIsEnoughAndStillExpiresOffline() {
        write("""{"v":1,"min":1,"generado":1,"n":[["single",3,1],["multiple",1,2]]}""")
        assertEquals(mapOf("single" to 3, "multiple" to 1), SpamLista.mapa(context, 100))
        assertTrue(SpamLista.mapa(context, 1 + SpamRules.VIGENCIA_LISTA_MS).isEmpty())
    }

    @Test fun communityDoesNotMatchForeignNumbersWithTheSameSuffix() {
        val key = SpamRules.huella("5512345678")
        val now = System.currentTimeMillis()
        write("""{"v":1,"generado":$now,"n":[["$key",1,3]]}""")
        assertEquals(CategoriaSpam.COBRANZA, SpamLista.categoria(context, "+525512345678"))
        assertNull(SpamLista.categoria(context, "+345512345678"))
    }
}
