package fr.easter.brewhome

import fr.easter.brewhome.data.PendingQueue
import fr.easter.brewhome.data.PendingStockOp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingQueueTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `add et load persistent les operations`() {
        val q = PendingQueue(tmp.root)
        q.add(PendingStockOp(beerId = 1, d33 = -1))
        q.add(PendingStockOp(beerId = 1, d33 = -1))
        q.add(PendingStockOp(beerId = 2, d75 = 1))
        assertEquals(3, q.load().size)
        // Une nouvelle instance relit le même fichier
        assertEquals(3, PendingQueue(tmp.root).load().size)
        q.clear()
        assertEquals(0, PendingQueue(tmp.root).load().size)
    }

    @Test
    fun `coalesce somme les deltas par biere`() {
        val ops = listOf(
            PendingStockOp(1, d33 = -1),
            PendingStockOp(1, d33 = -1),
            PendingStockOp(1, d75 = 2),
            PendingStockOp(2, dKeg = 0.5),
            PendingStockOp(2, dKeg = -0.5),
        )
        val merged = PendingQueue.coalesce(ops).associateBy { it.beerId }
        assertEquals(-2, merged.getValue(1).d33)
        assertEquals(2, merged.getValue(1).d75)
        assertEquals(0.0, merged.getValue(2).dKeg, 1e-9)
        assertEquals(2, merged.size)
    }
}
