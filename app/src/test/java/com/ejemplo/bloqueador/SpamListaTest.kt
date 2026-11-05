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
        write("""{"v":1,"n":[["community",1,3]]}""")
        assertEquals(mapOf("community" to 1), SpamLista.mapa(context, 100))
    }

    @Test fun tellowsExpiresEvenWhenTheFileHasNotChangedAndThereIsNoNetwork() {
        write("""{"v":1,"tellows_hasta":200,"n":[["community",1,3],["tellows",0,0,"tellows",8]]}""")
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
}
