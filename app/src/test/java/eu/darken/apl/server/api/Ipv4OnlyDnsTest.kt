package eu.darken.apl.server.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import okhttp3.Dns
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.net.InetAddress

class Ipv4OnlyDnsTest : BaseTest() {

    private fun dnsOf(vararg addresses: String) = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            addresses.map { InetAddress.getByName(it) }
    }

    @Test
    fun `only the IPv4 addresses are offered`() {
        val dns = Ipv4OnlyDns(dnsOf("2001:db8::1", "203.0.113.7", "2001:db8::2", "203.0.113.8"))

        dns.lookup("api.example") shouldBe listOf(
            InetAddress.getByName("203.0.113.7"),
            InetAddress.getByName("203.0.113.8"),
        )
    }

    @Test
    fun `a host with no IPv4 address fails instead of falling back`() {
        val dns = Ipv4OnlyDns(dnsOf("2001:db8::1"))

        shouldThrow<NoIpv4AddressException> { dns.lookup("v6only.example") }
    }

    @Test
    fun `an empty answer stays a failure`() {
        val dns = Ipv4OnlyDns(dnsOf())

        shouldThrow<NoIpv4AddressException> { dns.lookup("nothing.example") }
    }
}
