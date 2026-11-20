package app.clodex.harness

import app.clodex.harness.data.*
import app.clodex.harness.data.agent.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProviderAgentClientTest {
    private val sseHeaders = headersOf(HttpHeaders.ContentType, "text/event-stream")
    private fun request(vararg messages: AgentMessage, tools: List<AgentToolSpec> = emptyList(), model: String = "m-1", webSearch: Boolean = false) =
        ProviderRequest(model, 256, "sys", messages.toList(), tools, webSearch)

    private fun mock(body: String, status: HttpStatusCode = HttpStatusCode.OK, seen: MutableList<String>? = null) =
        HttpClient(MockEngine { req -> seen?.add((req.body as TextContent).text); respond(body, status, sseHeaders) })

    @Test fun `OpenRouter search uses the web plugin without changing model or function tools`() = runBlocking {
        val seen = mutableListOf<String>()
        val client = OpenAiAgentClient(mock("data: [DONE]\n\n", seen = seen), "https://openrouter.ai/api/v1/", "k")
        val tools = listOf(AgentToolSpec("notes__read", "Read notes", JsonObject(emptyMap())))
        for (enabled in listOf(true, false)) {
            client.stream(request(AgentMessage.User("Search for current news"), tools = tools, model = "openrouter/auto", webSearch = enabled)).toList()
        }

        val search = Json.parseToJsonElement(seen[0]).jsonObject
        val plain = Json.parseToJsonElement(seen[1]).jsonObject
        assertEquals(Json.parseToJsonElement("""[{"id":"web"}]"""), search["plugins"])
        assertEquals(JsonPrimitive("openrouter/auto"), search["model"])
        assertNotNull(search["tools"])
        assertEquals(plain["tools"], search["tools"])
        assertFalse("plugins" in plain)
    }

    @Test fun `other Chat Completions endpoints do not silently answer without requested search`() = runBlocking {
        val seen = mutableListOf<String>()
        for (base in listOf("https://api.openai.com/v1", "https://api.deepseek.com/v1", "https://openrouter.ai.example/api/v1")) {
            val client = OpenAiAgentClient(mock("data: [DONE]\n\n", seen = seen), base, "k")
            val e = runCatching { client.stream(request(AgentMessage.User("Look this up"), webSearch = true)).toList() }.exceptionOrNull()
            assertTrue("$base: $e", e is ClaudeException && e.message.orEmpty().contains("Веб-поиск"))
        }
        assertTrue(seen.isEmpty())
    }

    @Test fun `Anthropic search tool errors delivered over HTTP 200 are surfaced`() = runBlocking {
        val body = """
            data: {"type":"content_block_start","index":0,"content_block":{"type":"server_tool_use","id":"srvtoolu_1","name":"web_search","input":{}}}

            data: {"type":"content_block_start","index":1,"content_block":{"type":"web_search_tool_result","tool_use_id":"srvtoolu_1","content":{"type":"web_search_tool_result_error","error_code":"unavailable"}}}

            data: {"type":"message_stop"}

        """.trimIndent()
        val seen = mutableListOf<String>()
        val client = AnthropicAgentClient(mock(body, seen = seen), "https://api.anthropic.com", "k")
        val e = runCatching { client.stream(request(AgentMessage.User("Look this up"), webSearch = true)).toList() }.exceptionOrNull()
        assertEquals(Json.parseToJsonElement("""[{"type":"web_search_20250305","name":"web_search"}]"""), Json.parseToJsonElement(seen.single()).jsonObject["tools"])
        assertTrue(e is ClaudeException && e.message.orEmpty().contains("unavailable"))
    }

    @Test fun `search citations are included in the streamed answer as source links`() = runBlocking {
        val anthropic = """
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Answer"}}

            data: {"type":"content_block_delta","index":0,"delta":{"type":"citations_delta","citation":{"type":"web_search_result_location","title":"News","url":"https://example.com/news","cited_text":"Answer","encrypted_index":"ignore"}}}

            data: {"type":"message_stop"}

        """.trimIndent()
        val openRouter = """
            data: {"choices":[{"index":0,"delta":{"content":"Answer"},"finish_reason":null}]}

            data: {"choices":[{"index":0,"delta":{"annotations":[{"type":"url_citation","url_citation":{"title":"News","url":"https://example.com/news","start_index":0,"end_index":6}},{"type":"url_citation","url_citation":{"title":"Bad","url":"javascript:alert(1)","start_index":0,"end_index":6}}]},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent()
        for (client in listOf(
            AnthropicAgentClient(mock(anthropic), "https://api.anthropic.com", "k"),
            OpenAiAgentClient(mock(openRouter), "https://openrouter.ai/api/v1", "k"),
        )) {
            val events = client.stream(request(AgentMessage.User("Look this up"), webSearch = true)).toList()
            val text = events.filterIsInstance<ProviderEvent.TextDelta>().joinToString("") { it.text }
            assertTrue(text.startsWith("Answer"))
            assertTrue(text.contains("[example.com](https://example.com/news)"))
            assertFalse(text.contains("javascript:"))
        }
    }

    @Test fun `sse parser joins multi-line data and ignores comments`() {
        val p = SseParser()
        assertEquals(emptyList<String>(), p.feed(": ping"))
        assertEquals(emptyList<String>(), p.feed("event: message"))
        assertEquals(emptyList<String>(), p.feed("data: {\"a\":"))
        assertEquals(emptyList<String>(), p.feed("data: 1}"))
        assertEquals(listOf("{\"a\":\n1}"), p.feed(""))
        p.feed("data:[DONE]")
        assertEquals(listOf("[DONE]"), p.flush())
    }

    @Test fun `anthropic streams text, drops raw thinking and completes tool use`() = runBlocking {
        val body = """
            event: message_start
            data: {"type":"message_start","message":{}}

            data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}

            data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"секретные мысли"}}

            data: {"type":"content_block_stop","index":0}

            data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}

            data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Смотрю"}}

            data: {"type":"content_block_stop","index":1}

            data: {"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"toolu_1","name":"gh__find","input":{}}}

            data: {"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"q\":"}}

            data: {"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"\"bug\"}"}}

            data: {"type":"content_block_stop","index":2}

            data: {"type":"message_delta","delta":{"stop_reason":"tool_use"}}

            data: {"type":"message_stop"}

        """.trimIndent()
        val seen = mutableListOf<String>()
        val events = AnthropicAgentClient(mock(body, seen = seen), "https://api.anthropic.com", "k")
            .stream(request(AgentMessage.User("hi"))).toList()

        assertEquals(
            listOf(
                ProviderEvent.TextDelta("Смотрю"),
                ProviderEvent.ToolCallDelta("toolu_1", "gh__find"),
                ProviderEvent.ToolCallComplete(ProviderToolCall("toolu_1", "gh__find", buildJsonObject { put("q", "bug") })),
                ProviderEvent.Completed("tool_use"),
            ),
            events,
        )
        assertFalse(events.toString().contains("секретные"))
        assertTrue(seen.single().contains("\"system\":\"sys\""))
    }

    @Test fun `anthropic body sends tool results back as one user message`() {
        val call = ProviderToolCall("toolu_1", "gh__find", buildJsonObject { put("q", "bug") })
        val body = anthropicBody(
            request(
                AgentMessage.User("hi"),
                AgentMessage.Assistant("Смотрю", listOf(call)),
                AgentMessage.ToolResult("toolu_1", "gh__find", AgentToolResult.of("3 issues")),
                tools = listOf(AgentToolSpec("gh__find", "Search", JsonObject(emptyMap()))),
            ),
        )
        val msgs = body["messages"]!!.jsonArray
        assertEquals(3, msgs.size)
        val assistant = msgs[1].jsonObject["content"]!!.jsonArray
        assertEquals("text", assistant[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("tool_use", assistant[1].jsonObject["type"]!!.jsonPrimitive.content)
        val result = msgs[2].jsonObject
        assertEquals("user", result["role"]!!.jsonPrimitive.content)
        assertEquals("toolu_1", result["content"]!!.jsonArray[0].jsonObject["tool_use_id"]!!.jsonPrimitive.content)
        val schema = body["tools"]!!.jsonArray[0].jsonObject["input_schema"]!!.jsonObject
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
    }

    @Test fun `openai assembles fragmented tool calls in index order and suppresses reasoning content`() = runBlocking {
        val body = listOf(
            """{"choices":[{"delta":{"reasoning_content":"сырые рассуждения"}}]}""",
            """{"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.summary","summary":"Проверяю репозиторий"}]}}]}""",
            """{"choices":[{"delta":{"content":"Ок"}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_b","type":"function","function":{"name":"b__two","arguments":""}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"a__one","arguments":"{\"x\""}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":":1}"}},{"index":1,"function":{"arguments":"{}"}}]}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            "[DONE]",
        ).joinToString("") { "data: $it\n\n" }
        val events = OpenAiAgentClient(mock(body), "https://api.openai.com/v1", "k").stream(request(AgentMessage.User("hi"))).toList()

        assertEquals(
            listOf(
                ProviderEvent.SummaryDelta("Проверяю репозиторий"),
                ProviderEvent.TextDelta("Ок"),
                ProviderEvent.ToolCallDelta("call_b", "b__two"),
                ProviderEvent.ToolCallDelta("call_a", "a__one"),
                ProviderEvent.ToolCallComplete(ProviderToolCall("call_a", "a__one", buildJsonObject { put("x", 1) })),
                ProviderEvent.ToolCallComplete(ProviderToolCall("call_b", "b__two", JsonObject(emptyMap()))),
                ProviderEvent.Completed("tool_calls"),
            ),
            events,
        )
        assertFalse(events.toString().contains("сырые"))
    }

    @Test fun `openai malformed arguments are flagged instead of crashing`() = runBlocking {
        val body = """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c","function":{"name":"t","arguments":"{oops"}}]}}]}""" + "\n\ndata: [DONE]\n\n"
        val call = OpenAiAgentClient(mock(body), "https://x/v1", "k").stream(request(AgentMessage.User("hi"))).toList()
            .filterIsInstance<ProviderEvent.ToolCallComplete>().single().call
        assertTrue(call.invalidArguments)
    }

    @Test fun `openai body maps tool calls and results`() {
        val call = ProviderToolCall("c1", "t", buildJsonObject { put("a", 1) })
        val body = openAiBody(request(AgentMessage.User("hi"), AgentMessage.Assistant("", listOf(call)), AgentMessage.ToolResult("c1", "t", AgentToolResult.of("ok"))))
        val msgs = body["messages"]!!.jsonArray
        assertEquals("system", msgs[0].jsonObject["role"]!!.jsonPrimitive.content)
        val tc = msgs[2].jsonObject["tool_calls"]!!.jsonArray[0].jsonObject
        assertEquals("{\"a\":1}", tc["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
        assertEquals("tool", msgs[3].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("c1", msgs[3].jsonObject["tool_call_id"]!!.jsonPrimitive.content)
    }

    @Test fun `http errors surface the provider message`() = runBlocking {
        val client = OpenAiAgentClient(mock("""{"error":{"message":"Insufficient balance"}}""", HttpStatusCode.PaymentRequired), "https://x/v1", "k")
        val e = runCatching { client.stream(request(AgentMessage.User("hi"))).toList() }.exceptionOrNull()
        assertTrue(e is ClaudeException)
        assertEquals("HTTP 402: Insufficient balance", e!!.message)
        assertTrue(isQuotaMessage(e.message!!))
    }

    @Test fun `stream larger than the limit is stopped`() = runBlocking {
        val line = "data: " + """{"choices":[{"delta":{"content":"${"x".repeat(200)}"}}]}""" + "\n\n"
        val client = OpenAiAgentClient(mock(line.repeat(20)), "https://x/v1", "k", limits = AgentLimits(maxStreamBytes = 1024))
        val e = runCatching { client.stream(request(AgentMessage.User("hi"))).toList() }.exceptionOrNull()
        assertTrue(e is AgentException)
    }

    @Test fun `fetched model ids win over unverified catalog aliases`() {
        val anthropic = DefaultProviders.first().copy(key = "k", models = listOf("claude-sonnet-4-5-20250929", "claude-sonnet-5-5"))
        val s = AppSettings(providers = listOf(anthropic) + DefaultProviders.drop(1))
        val merged = ModelCatalog.merged(anthropic)
        assertEquals(true, merged.first { it.display == "Sonnet 5.5" }.verified)
        assertEquals(false, merged.first { it.display == "Opus 5.5" }.verified)
        assertEquals(true, merged.first { it.display == "claude-sonnet-4-5-20250929" }.verified)
        assertTrue(merged.indexOfFirst { it.verified == false } > merged.indexOfLast { it.verified == true })
        assertEquals("claude-sonnet-4-5-20250929", resolveModel(s, anthropic, "claude-sonnet-4-5-20250929"))
        assertEquals("anthropic", s.providerForModel("claude-sonnet-4-5-20250929")?.id)
        // An alias the provider did not return is replaced by a real id after refresh.
        assertEquals("Sonnet 5.5", ModelCatalog.preferredModel(anthropic, "Opus 5.5"))
        assertNull(ModelCatalog.preferredModel(anthropic, "Sonnet 5.5"))
        // Before any fetch the catalog is shown unchanged.
        assertTrue(ModelCatalog.merged(DefaultProviders.first()).all { it.verified == null })
    }
}
