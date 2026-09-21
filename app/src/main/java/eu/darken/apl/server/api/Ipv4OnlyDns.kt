package eu.darken.apl.server.api

import okhttp3.Dns
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * The lookup resolved, but nothing it returned was usable over IPv4.
 *
 * Distinct from the [UnknownHostException] an offline device produces, which carries no such
 * information: this one only happens once a resolver has answered.
 */
class NoIpv4AddressException(hostname: String) : UnknownHostException("No IPv4 address for $hostname")

/**
 * An IPv4-pinned request failed to resolve or to connect. Raised only for that request and only
 * for those two failures, so nothing can read an IPv4 verdict out of a connection that broke
 * while the server already had the request.
 */
class Ipv4UnreachableException(cause: IOException) : IOException(cause)

/**
 * Resolves to IPv4 addresses only.
 *
 * A host with no IPv4 address fails the lookup instead of falling back, because a caller asking for
 * this has a reason the connection must not be made over IPv6, and a silent fallback would defeat it.
 */
class Ipv4OnlyDns(private val delegate: Dns = Dns.SYSTEM) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = delegate.lookup(hostname).filterIsInstance<Inet4Address>()
        if (resolved.isEmpty()) throw NoIpv4AddressException(hostname)
        return resolved
    }
}
