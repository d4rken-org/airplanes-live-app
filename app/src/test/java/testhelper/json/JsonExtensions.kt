package testhelper.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.ByteString.Companion.encode
import okio.buffer
import okio.sink
import testhelper.BaseTest
import java.io.File


fun String.toComparableJson(): String {
    val json = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
        isLenient = true
        ignoreUnknownKeys = true
    }

    // Parse the string to a JsonElement
    val jsonElement = json.parseToJsonElement(this.trim())

    // Format it with pretty printing
    return json.encodeToString(JsonElement.serializer(), jsonElement)
}

/** [path] is relative to `src/test/resources`, e.g. `airport-flights/answered-load.json`. */
fun readTestResource(path: String): String {
    val url = BaseTest::class.java.classLoader?.getResource(path)
    return checkNotNull(url) { "Missing test resource: $path" }.readText()
}

fun String.writeToFile(file: File) = encode().let { text ->
    require(!file.exists())
    file.parentFile?.mkdirs()
    file.createNewFile()
    file.sink().buffer().use { it.write(text) }
}
