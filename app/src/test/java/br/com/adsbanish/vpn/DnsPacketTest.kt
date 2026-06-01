package br.com.adsbanish.vpn

import org.junit.Assert.*
import org.junit.Test

class DnsPacketTest {

    // Raw DNS query for "ads.example.com" type A, built manually per RFC 1035
    private fun buildQuery(domain: String, id: Int = 0x1234, recursionDesired: Boolean = true): ByteArray {
        val labels = domain.split(".")
        val payload = mutableListOf<Byte>()

        // Header (12 bytes)
        payload += listOf(
            (id shr 8).toByte(), (id and 0xFF).toByte(),  // ID
            if (recursionDesired) 0x01.toByte() else 0x00.toByte(), 0x00.toByte(), // Flags: RD=1
            0x00.toByte(), 0x01.toByte(),                 // QDCOUNT = 1
            0x00.toByte(), 0x00.toByte(),                 // ANCOUNT = 0
            0x00.toByte(), 0x00.toByte(),                 // NSCOUNT = 0
            0x00.toByte(), 0x00.toByte()                  // ARCOUNT = 0
        )

        // QNAME
        for (label in labels) {
            payload += label.length.toByte()
            payload += label.map { it.code.toByte() }
        }
        payload += 0x00.toByte() // root label

        // QTYPE = A (1), QCLASS = IN (1)
        payload += listOf(0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x01.toByte())

        return payload.toByteArray()
    }

    // ── parse ─────────────────────────────────────────────────────────────────

    @Test
    fun `parse returns valid packet for well-formed DNS query`() {
        val data = buildQuery("ads.example.com")
        val packet = DnsPacket.parse(data, data.size)
        assertNotNull(packet)
        assertEquals("ads.example.com", packet!!.questionName)
    }

    @Test
    fun `parse extracts correct transaction ID`() {
        val data = buildQuery("example.com", id = 0xABCD)
        val packet = DnsPacket.parse(data, data.size)
        assertNotNull(packet)
        assertEquals(0xABCD, packet!!.id)
    }

    @Test
    fun `parse lowercases domain name`() {
        val data = buildQuery("ADS.EXAMPLE.COM")
        val packet = DnsPacket.parse(data, data.size)
        assertNotNull(packet)
        assertEquals("ads.example.com", packet!!.questionName)
    }

    @Test
    fun `parse returns null for payload shorter than 12 bytes`() {
        val data = ByteArray(11)
        assertNull(DnsPacket.parse(data, data.size))
    }

    @Test
    fun `parse returns null for DNS response (QR=1)`() {
        val data = buildQuery("example.com")
        // Set QR bit in flags byte[2]
        data[2] = (data[2].toInt() or 0x80).toByte()
        assertNull(DnsPacket.parse(data, data.size))
    }

    @Test
    fun `parse returns null when QDCOUNT is zero`() {
        val data = buildQuery("example.com")
        // Set QDCOUNT = 0
        data[4] = 0x00; data[5] = 0x00
        assertNull(DnsPacket.parse(data, data.size))
    }

    @Test
    fun `parse handles single-label domain`() {
        val data = buildQuery("localhost")
        val packet = DnsPacket.parse(data, data.size)
        assertNotNull(packet)
        assertEquals("localhost", packet!!.questionName)
    }

    @Test
    fun `parse handles deep subdomain`() {
        val data = buildQuery("a.b.c.d.example.com")
        val packet = DnsPacket.parse(data, data.size)
        assertNotNull(packet)
        assertEquals("a.b.c.d.example.com", packet!!.questionName)
    }

    // ── buildNxdomainResponse ─────────────────────────────────────────────────

    @Test
    fun `buildNxdomainResponse sets QR bit to 1`() {
        val query = DnsPacket.parse(buildQuery("ads.example.com"), buildQuery("ads.example.com").size)!!
        val response = DnsPacket.buildNxdomainResponse(query)
        val qr = (response[2].toInt() and 0xFF) and 0x80
        assertEquals(0x80, qr)
    }

    @Test
    fun `buildNxdomainResponse sets RCODE to 3 (NXDOMAIN)`() {
        val query = DnsPacket.parse(buildQuery("ads.example.com"), buildQuery("ads.example.com").size)!!
        val response = DnsPacket.buildNxdomainResponse(query)
        val rcode = response[3].toInt() and 0x0F
        assertEquals(3, rcode)
    }

    @Test
    fun `buildNxdomainResponse preserves transaction ID`() {
        val data = buildQuery("ads.example.com", id = 0x5A5A)
        val query = DnsPacket.parse(data, data.size)!!
        val response = DnsPacket.buildNxdomainResponse(query)
        val responseId = ((response[0].toInt() and 0xFF) shl 8) or (response[1].toInt() and 0xFF)
        assertEquals(0x5A5A, responseId)
    }

    @Test
    fun `buildNxdomainResponse sets ANCOUNT to 0`() {
        val data = buildQuery("ads.example.com")
        val query = DnsPacket.parse(data, data.size)!!
        val response = DnsPacket.buildNxdomainResponse(query)
        val ancount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        assertEquals(0, ancount)
    }

    @Test
    fun `buildNxdomainResponse sets QDCOUNT to 1`() {
        val data = buildQuery("ads.example.com")
        val query = DnsPacket.parse(data, data.size)!!
        val response = DnsPacket.buildNxdomainResponse(query)
        val qdcount = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
        assertEquals(1, qdcount)
    }

    @Test
    fun `buildNxdomainResponse preserves RD flag when set`() {
        val data = buildQuery("ads.example.com", recursionDesired = true)
        val query = DnsPacket.parse(data, data.size)!!
        val response = DnsPacket.buildNxdomainResponse(query)
        val rd = response[2].toInt() and 0x01
        assertEquals(1, rd)
    }

    @Test
    fun `buildNxdomainResponse clears RD flag when not set`() {
        val data = buildQuery("ads.example.com", recursionDesired = false)
        val query = DnsPacket.parse(data, data.size)!!
        val response = DnsPacket.buildNxdomainResponse(query)
        val rd = response[2].toInt() and 0x01
        assertEquals(0, rd)
    }
}
