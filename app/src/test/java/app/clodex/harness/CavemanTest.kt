package app.clodex.harness

import app.clodex.harness.data.Caveman
import app.clodex.harness.data.CavemanIntensity
import app.clodex.harness.data.SkillEntity
import app.clodex.harness.data.Src
import app.clodex.harness.data.skillInstructions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CavemanTest {
    @Test fun `every intensity has a nonempty style prompt`() {
        CavemanIntensity.entries.forEach { assertTrue(Caveman.prompt(it).contains("Caveman")) }
    }

    @Test fun `five skills ship with stable ids and bodies`() {
        assertEquals(
            listOf("caveman-commit", "caveman-review", "caveman-investigate", "caveman-patch", "caveman-verify"),
            Caveman.skills.map { it.id },
        )
        assertTrue(Caveman.skills.all { it.body.isNotBlank() && it.source == Src.CAVEMAN })
    }

    @Test fun `caveman survives oversized user skills within the cap`() {
        val user = SkillEntity("x", "Big", "", Src.MINE, true, "Pen", body = "a".repeat(20_000))
        val block = Caveman.prompt(CavemanIntensity.FULL)
        val out = skillInstructions(listOf(user), block)
        assertTrue(out.length <= 12_000)
        assertTrue(out.contains("Caveman"))
        assertTrue(out.indexOf("Caveman") > out.indexOf("Скилл «Big»") || !out.contains("Скилл «Big»"))
    }

    @Test fun `no caveman block without installation`() {
        val user = SkillEntity("x", "Mine", "", Src.MINE, true, "Pen", body = "keep this")
        val out = skillInstructions(listOf(user), "")
        assertTrue(out.contains("keep this"))
        assertFalse(out.contains("Caveman"))
    }
}
