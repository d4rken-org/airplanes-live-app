package eu.darken.apl.feeder.ui.actions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.net.InetAddress

class FeederAddressValidationTest : BaseTest() {

    private val noDns: (String) -> InetAddress? = { error("No lookup expected for $it") }
    private val unresolvable: (String) -> InetAddress? = { null }

    private fun ipv4(vararg bytes: Int) = InetAddress.getByAddress(bytes.map { it.toByte() }.toByteArray())

    @Test
    fun `a blank address clears it`() {
        validateFeederAddress("", noDns) shouldBe null
        validateFeederAddress("  ", noDns) shouldBe null
    }

    @Test
    fun `an IPv4 address needs no lookup`() {
        validateFeederAddress("192.168.0.42", noDns) shouldBe "192.168.0.42"
    }

    @Test
    fun `a host name with a dot needs no lookup`() {
        validateFeederAddress("myfeeder.example.com", noDns) shouldBe "myfeeder.example.com"
        validateFeederAddress("feeder.local", noDns) shouldBe "feeder.local"
    }

    @Test
    fun `a host name with a dot is kept even when it does not resolve here`() {
        validateFeederAddress("my-feeder.example.com", unresolvable) shouldBe "my-feeder.example.com"
    }

    @Test
    fun `a name without a dot is kept when it resolves to IPv4`() {
        validateFeederAddress("feeder") { ipv4(192, 168, 0, 42) } shouldBe "feeder"
    }

    @Test
    fun `a name without a dot that does not resolve is invalid`() {
        shouldThrow<InvalidFeederAddressException> { validateFeederAddress("feeder", unresolvable) }
    }

    @Test
    fun `a name that only resolves to IPv6 is invalid`() {
        val ipv6 = InetAddress.getByAddress(ByteArray(16).also { it[15] = 1 })
        shouldThrow<InvalidFeederAddressException> { validateFeederAddress("feeder") { ipv6 } }
    }

    @Test
    fun `addresses that are neither IPv4 nor host names are invalid`() {
        listOf(
            "not a host",
            "192.168.0.256",
            "192.168.010.1",
            "-feeder.example.com",
            "feeder-.example.com",
            "feeder.example.c0m",
            "${"a".repeat(64)}.example.com",
        ).forEach { address ->
            shouldThrow<InvalidFeederAddressException> { validateFeederAddress(address, unresolvable) }
        }
    }
}
