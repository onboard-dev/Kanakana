package me.zssu.ime.ime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProofreadRemoteTest {
    private fun base(url: String) = ProofreadRemote.checkOllamaUrl(url).base

    @Test fun acceptsATailscaleAddress() {
        assertEquals("http://100.64.0.10:11434", base("http://100.64.0.10:11434"))
    }

    @Test fun fillsInSchemeAndDefaultPort() {
        assertEquals("http://192.168.0.5:11434", base("192.168.0.5"))
        assertEquals("http://nas:11434", base("nas"))
        assertEquals("http://localhost:8080", base("http://localhost:8080/"))
    }

    @Test fun dropsAnyPathSoOnlyOurEndpointsAreCalled() {
        assertEquals("http://10.0.0.2:11434", base("http://10.0.0.2:11434/api/chat?x=1"))
    }

    @Test fun refusesPlainHttpToPublicAddresses() {
        assertNull(base("http://example.com"))
        assertNull(base("http://8.8.8.8:11434"))
        assertNull(base("http://100.128.0.1:11434")) // just outside Tailscale's 100.64.0.0/10
        assertNull(base("http://172.32.0.1"))
        assertNotNull(base("https://ollama.example.com"))
    }

    @Test fun rejectsEmptyAndUnsupportedSchemes() {
        assertNull(base(""))
        assertNull(base("ftp://192.168.0.1"))
        assertNotNull(ProofreadRemote.checkOllamaUrl("ftp://192.168.0.1").problem)
    }

    @Test fun privateRanges() {
        listOf("10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "100.64.0.1", "100.127.255.255", "127.0.0.1", "169.254.1.1")
            .forEach { assertTrue(it, ProofreadRemote.isPrivateHost(it)) }
        listOf("1.1.1.1", "172.15.0.1", "172.32.0.1", "100.63.0.1", "192.169.0.1", "example.com")
            .forEach { assertFalse(it, ProofreadRemote.isPrivateHost(it)) }
    }

    @Test fun openRouterBodyIsAnOpenAiChatRequest() {
        val json = JSONObject(ProofreadRemote.openRouterBody("openai/gpt-4o-mini", "SYS", "こんにちわ"))
        assertEquals("openai/gpt-4o-mini", json.getString("model"))
        assertEquals(false, json.getBoolean("stream"))
        val messages = json.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("SYS", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("こんにちわ", messages.getJSONObject(1).getString("content"))
    }

    @Test fun ollamaBodyNamesTheModelAndDisablesStreaming() {
        val json = JSONObject(ProofreadRemote.ollamaBody("qwen3:8b", "SYS", "text"))
        assertEquals("qwen3:8b", json.getString("model"))
        assertEquals(false, json.getBoolean("stream"))
        assertEquals(0, json.getJSONObject("options").getInt("temperature"))
        assertEquals(false, json.getBoolean("think"))
    }

    @Test fun parsesOpenRouterReply() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"こんにちは"}}]}"""
        assertEquals("こんにちは", ProofreadRemote.parseOpenRouter(body))
    }

    @Test fun openRouterErrorBodySurfacesTheMessage() {
        try {
            ProofreadRemote.parseOpenRouter("""{"error":{"message":"No credits","code":402}}""")
            fail()
        } catch (e: ProofreadRemoteException) {
            assertTrue(e.message!!.contains("No credits"))
        }
    }

    @Test fun emptyOrBrokenRepliesAreErrorsNotBlankResults() {
        listOf("""{"choices":[]}""", """{"choices":[{"message":{"content":"  "}}]}""", "not json").forEach {
            try { ProofreadRemote.parseOpenRouter(it); fail(it) } catch (_: ProofreadRemoteException) {}
        }
        try { ProofreadRemote.parseOllama("""{"message":{"content":""}}"""); fail() } catch (_: ProofreadRemoteException) {}
    }

    @Test fun thoughtOnlyReplyGetsASpecificError() {
        try {
            ProofreadRemote.parseOllama("""{"message":{"content":"","thinking":"考え中"}}""")
            fail()
        } catch (e: ProofreadRemoteException) {
            assertTrue(e.message!!.contains("推論"))
        }
    }

    @Test fun listingWithABadAddressFailsBeforeAnyNetworkCall() {
        try {
            ProofreadRemote.listOllamaModels("http://example.com", ProofreadRemote.Call())
            fail()
        } catch (e: ProofreadRemoteException) {
            assertTrue(e.message!!.contains("https"))
        }
    }

    @Test fun parsesOllamaReplyAndModelList() {
        assertEquals("直した", ProofreadRemote.parseOllama("""{"message":{"role":"assistant","content":"直した"},"done":true}"""))
        assertEquals(listOf("qwen3:8b", "gemma3:4b"),
            ProofreadRemote.parseOllamaModels("""{"models":[{"name":"qwen3:8b"},{"name":"gemma3:4b"}]}"""))
    }

    @Test fun readinessNeedsTheRightFieldsPerEngine() {
        fun cfg(engine: ProofreadEngine, key: String = "", rm: String = "", url: String = "", om: String = "") =
            ProofreadRemote.Config(engine, key, rm, url, om)
        assertTrue(cfg(ProofreadEngine.LOCAL).ready())
        assertFalse(cfg(ProofreadEngine.OPENROUTER, key = "k").ready())
        assertTrue(cfg(ProofreadEngine.OPENROUTER, key = "k", rm = "m").ready())
        assertFalse(cfg(ProofreadEngine.OLLAMA, url = "http://192.168.0.1", om = "").ready())
        assertFalse(cfg(ProofreadEngine.OLLAMA, url = "http://example.com", om = "m").ready())
        assertTrue(cfg(ProofreadEngine.OLLAMA, url = "100.64.0.10:11434", om = "qwen3:8b").ready())
    }
}
