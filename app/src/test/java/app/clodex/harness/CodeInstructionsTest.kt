package app.clodex.harness

import app.clodex.harness.data.*
import app.clodex.harness.data.code.*
import org.junit.Assert.*
import org.junit.Test

class CodeInstructionsTest {
    private fun skill(id: String, enabled: Boolean, body: String) = SkillEntity(id, id, "description", Src.MINE, enabled, "Code", body)

    @Test fun onlyEnabledNonEmptySkillsReachInstructions() {
        val text = skillInstructions(listOf(skill("enabled", true, "Visible instruction"), skill("disabled", false, "Hidden instruction"), skill("empty", true, "")))
        assertTrue(text.contains("Visible instruction"))
        assertFalse(text.contains("Hidden instruction"))
        assertFalse(text.contains("empty"))
        assertTrue(skillInstructions(emptyList()).isEmpty())
    }

    @Test fun skillBudgetIsBoundedAndCodePromptPreservesCapabilities() {
        val skills = skillInstructions(List(100) { skill("skill-$it", true, "x".repeat(5000)) })
        assertTrue(skills.length <= 12000)
        val ws = WorkspaceEntity(name = "demo", kind = "gh", path = "owner/repo")
        val text = codeSystemPrompt(ws, "main", CodeMode.PLAN, "User instruction" + skills)
        assertTrue(text.contains("User instruction"))
        assertFalse(text.contains("workspace_write"))
        assertTrue(text.contains("не можешь запускать команды"))
    }
}
