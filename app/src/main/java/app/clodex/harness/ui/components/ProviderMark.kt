package app.clodex.harness.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.clodex.harness.R
import app.clodex.harness.ui.theme.H

data class ProviderPresentation(@DrawableRes val drawableRes: Int?, val tintArgb: Long)

private val families = mapOf(
    "claude" to "claude", "gpt" to "openai", "chatgpt" to "openai", "o1" to "openai", "o3" to "openai", "o4" to "openai",
    "deepseek" to "deepseek", "gemini" to "gemini", "gemma" to "gemini", "grok" to "grok", "llama" to "meta",
    "qwen" to "qwen", "qwq" to "qwen", "kimi" to "kimi", "moonshot" to "kimi", "glm" to "zhipu",
    "minimax" to "minimax", "mistral" to "mistral", "mixtral" to "mistral", "codestral" to "mistral",
    "magistral" to "mistral", "sonar" to "perplexity", "command" to "cohere",
)
private val words = Regex("[^a-z0-9]+")
fun modelBrand(model: String): String? = model.substringAfterLast('/').lowercase().split(words)
    .firstNotNullOfOrNull { word -> families[word] ?: families.entries.firstOrNull { (prefix, _) -> word.startsWith(prefix) && word.getOrNull(prefix.length)?.isDigit() == true }?.value }

private val logos = mapOf(
    "anthropic" to (R.drawable.logo_anthropic_light to R.drawable.logo_anthropic_dark),
    "openai" to (R.drawable.logo_openai_light to R.drawable.logo_openai_dark),
    "grok" to (R.drawable.logo_grok_light to R.drawable.logo_grok_dark),
    "xai" to (R.drawable.logo_xai_light to R.drawable.logo_xai_dark),
    "kimi" to (R.drawable.logo_kimi_light to R.drawable.logo_kimi_dark),
    "moonshot" to (R.drawable.logo_moonshot_light to R.drawable.logo_moonshot_dark),
    "openrouter" to (R.drawable.logo_openrouter_light to R.drawable.logo_openrouter_dark),
    "groq" to (R.drawable.logo_groq_light to R.drawable.logo_groq_dark),
    "ollama" to (R.drawable.logo_ollama_light to R.drawable.logo_ollama_dark),
) + mapOf(
    "claude" to R.drawable.logo_claude, "deepseek" to R.drawable.logo_deepseek,
    "gemini" to R.drawable.logo_gemini, "mistral" to R.drawable.logo_mistral,
    "meta" to R.drawable.logo_meta, "qwen" to R.drawable.logo_qwen, "zhipu" to R.drawable.logo_zhipu,
    "minimax" to R.drawable.logo_minimax, "perplexity" to R.drawable.logo_perplexity, "cohere" to R.drawable.logo_cohere,
).mapValues { (_, image) -> image to image }

fun providerPresentation(providerId: String, name: String = "", url: String = "", model: String = "", dark: Boolean = false): ProviderPresentation {
    val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
    val provider = sequenceOf(providerId.lowercase()) + name.lowercase().split(words).asSequence() + host.lowercase().split(words).asSequence()
    val logo = (modelBrand(model) ?: provider.firstOrNull { it in logos })?.let(logos::get)
    return ProviderPresentation(logo?.let { if (dark) it.second else it.first }, 0xFF6D3FD0)
}

@Composable
fun ProviderMark(providerId: String, name: String, size: Dp = 34.dp, url: String = "", model: String = "") {
    val presentation = providerPresentation(providerId, name, url, model, H.c.dark)
    val tint = Color(presentation.tintArgb)
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier.size(size).clip(shape).background(H.c.s2)
            .border(1.dp, H.c.line, shape).padding(size * 0.18f),
        contentAlignment = Alignment.Center,
    ) {
        val icon = presentation.drawableRes
        if (icon != null) {
            Image(
                painterResource(icon), name,
                Modifier.size(size * 0.62f),
            )
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "C",
                style = H.t.small.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
            )
        }
    }
}
