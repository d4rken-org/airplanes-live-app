package eu.darken.apl.map.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class MapCreditTextTest {

    private val baseMap = "<a href=\"https://openfreemap.org\" target=\"_blank\">OpenFreeMap</a> " +
        "<a href=\"https://www.openmaptiles.org/\" target=\"_blank\">&copy; OpenMapTiles</a> " +
        "Data from <a href=\"https://www.openstreetmap.org/copyright\" target=\"_blank\">OpenStreetMap</a>"
    private val baseMapText = "OpenFreeMap © OpenMapTiles Data from OpenStreetMap"

    private val openAip = "<a href=\"https://www.openaip.net\">openAIP</a>"

    private val clicked = mutableListOf<String>()

    private fun credit(vararg attributions: String) =
        mapCreditText(attributions.toList(), linkStyles = null, onLink = { clicked += it })

    /** (linked text, URL) per link, in text order. */
    private fun AnnotatedString.links(): List<Pair<String, String>> =
        getLinkAnnotations(0, length)
            .sortedBy { it.start }
            .map { text.substring(it.start, it.end) to (it.item as LinkAnnotation.Url).url }

    @Test
    fun `base map credit keeps its three links`() {
        val result = credit(baseMap)

        result.text shouldBe baseMapText
        result.links() shouldBe listOf(
            "OpenFreeMap" to "https://openfreemap.org",
            "© OpenMapTiles" to "https://www.openmaptiles.org/",
            "OpenStreetMap" to "https://www.openstreetmap.org/copyright",
        )
    }

    @Test
    fun `overlay credit follows the base map after a separator`() {
        val result = credit(baseMap, openAip)

        result.text shouldBe "$baseMapText · openAIP"
        result.links().last() shouldBe ("openAIP" to "https://www.openaip.net")
        result.links().size shouldBe 4
    }

    @Test
    fun `duplicate and blank attributions are dropped`() {
        val result = credit("", baseMap, "   ", openAip, baseMap, " $openAip ", "\n")

        result.text shouldBe "$baseMapText · openAIP"
        result.links().size shouldBe 4
    }

    @Test
    fun `escaped characters in text and href are decoded`() {
        val result = credit("<a href=\"https://x.test/?a=1&amp;b=2&amp;c=&quot;d&quot;\">A &amp; &quot;B&quot;</a>")

        result.text shouldBe "A & \"B\""
        result.links() shouldBe listOf("A & \"B\"" to "https://x.test/?a=1&b=2&c=\"d\"")
    }

    @Test
    fun `no attributions give an empty credit`() {
        val result = credit()

        result.text shouldBe ""
        result.getLinkAnnotations(0, result.length).shouldBeEmpty()
    }

    @Test
    fun `tapping a link reports its URL`() {
        val result = credit(baseMap, openAip)
        val links = result.getLinkAnnotations(0, result.length).sortedBy { it.start }

        links.forEach { range ->
            val link = range.item as LinkAnnotation.Url
            link.linkInteractionListener!!.onClick(link)
        }

        clicked shouldBe listOf(
            "https://openfreemap.org",
            "https://www.openmaptiles.org/",
            "https://www.openstreetmap.org/copyright",
            "https://www.openaip.net",
        )
    }
}
