package app.gamenative.wow.addons.provider

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

object Fixtures {
    /** Reads `src/test/resources/addons/<name>`, replacing `{{BASE}}` with [base] (no trailing slash). */
    fun load(name: String, base: String = ""): String {
        val stream = Fixtures::class.java.getResourceAsStream("/addons/$name") ?: error("missing fixture $name")
        return stream.bufferedReader().use { it.readText() }.replace("{{BASE}}", base)
    }

    fun json(body: String, code: Int = 200): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    fun MockWebServer.base(): String = url("/").toString().trimEnd('/')
}
