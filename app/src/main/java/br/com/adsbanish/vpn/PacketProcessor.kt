package br.com.adsbanish.vpn

import android.net.VpnService
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class PacketProcessor(
    private val vpnService: VpnService,
    private val tunFd: FileDescriptor,
    private val isBlocked: (String) -> Boolean
) {
    private val upstreamDns = InetAddress.getByName("1.1.1.1")
    private val dnsPort = 53

    suspend fun run() = withContext(Dispatchers.IO) {
        val input  = FileInputStream(tunFd)
        val output = FileOutputStream(tunFd)
        val buffer = ByteArray(32767)

        while (isActive) {
            val length = try { input.read(buffer) } catch (e: Exception) { break }
            if (length <= 0) continue
            processIpPacket(ByteBuffer.wrap(buffer, 0, length), length, output)
        }
    }

    private fun processIpPacket(packet: ByteBuffer, length: Int, output: FileOutputStream) {
        val firstByte      = packet.get(0).toInt() and 0xFF
        val ipVersion      = firstByte shr 4
        if (ipVersion != 4) {
            Log.v("DNS-RAW", "pacote IPv${ipVersion} ignorado (${length}b)")
            forwardRaw(packet.array(), length, output); return
        }

        val ipHeaderLength = (firstByte and 0x0F) * 4
        val protocol       = packet.get(9).toInt() and 0xFF
        val protoName = when(protocol) { 6 -> "TCP" 17 -> "UDP" 1 -> "ICMP" else -> "proto$protocol" }

        val udpOffset = ipHeaderLength
        val dstPort   = (packet.get(udpOffset + 2).toInt() and 0xFF shl 8) or
                        (packet.get(udpOffset + 3).toInt() and 0xFF)
        val srcPort   = (packet.get(udpOffset).toInt()     and 0xFF shl 8) or
                        (packet.get(udpOffset + 1).toInt() and 0xFF)

        Log.v("DNS-RAW", "$protoName src=$srcPort dst=$dstPort len=$length")

        if (protocol != 17) { forwardRaw(packet.array(), length, output); return }
        if (dstPort != dnsPort) { forwardRaw(packet.array(), length, output); return }

        val udpPayloadOffset = udpOffset + 8
        val udpPayloadLength = length - udpPayloadOffset
        if (udpPayloadLength <= 0) return

        val dnsData   = packet.array().copyOfRange(udpPayloadOffset, udpPayloadOffset + udpPayloadLength)
        val dnsPacket = DnsPacket.parse(dnsData, udpPayloadLength) ?: return

        val domain = dnsPacket.questionName
        if (isBlocked(domain)) {
            Log.w("DNS-BLOCK", "BLOQUEADO: $domain")
            val nxResp = DnsPacket.buildNxdomainResponse(dnsPacket)
            writeUdpResponse(packet.array().copyOfRange(0, ipHeaderLength), dnsPort, srcPort, nxResp, output)
        } else {
            Log.d("DNS-ALLOW", "permitido: $domain")
            forwardDnsQuery(domain, dnsData, udpPayloadLength, srcPort, packet.array(), ipHeaderLength, output)
        }
    }

    private fun forwardDnsQuery(
        domain: String, dnsPayload: ByteArray, length: Int, originalSrcPort: Int,
        ipHeader: ByteArray, ipHeaderLength: Int, output: FileOutputStream
    ) {
        try {
            val socket = DatagramSocket()
            vpnService.protect(socket)
            socket.soTimeout = 3000
            socket.send(DatagramPacket(dnsPayload, length, upstreamDns, dnsPort))
            val buf     = ByteArray(4096)
            val receive = DatagramPacket(buf, buf.size)
            socket.receive(receive)
            socket.close()
            writeUdpResponse(ipHeader.copyOfRange(0, ipHeaderLength), dnsPort, originalSrcPort, buf.copyOf(receive.length), output)
        } catch (e: Exception) {
            Log.e("DNS-ALLOW", "ERRO ao encaminhar $domain para upstream: ${e.message}")
        }
    }

    private fun writeUdpResponse(
        ipHeader: ByteArray, udpSrcPort: Int, udpDstPort: Int,
        payload: ByteArray, output: FileOutputStream
    ) {
        val udpLength   = 8 + payload.size
        val totalLength = ipHeader.size + udpLength
        val response    = ByteBuffer.allocate(totalLength)
        val modifiedIp  = ipHeader.copyOf()

        System.arraycopy(ipHeader, 16, modifiedIp, 12, 4)
        System.arraycopy(ipHeader, 12, modifiedIp, 16, 4)
        modifiedIp[2] = (totalLength shr 8).toByte()
        modifiedIp[3] = (totalLength and 0xFF).toByte()
        modifiedIp[10] = 0; modifiedIp[11] = 0
        val cs = checksum(modifiedIp, 0, ipHeader.size)
        modifiedIp[10] = (cs shr 8).toByte()
        modifiedIp[11] = (cs and 0xFF).toByte()

        response.put(modifiedIp)
        response.putShort(udpSrcPort.toShort())
        response.putShort(udpDstPort.toShort())
        response.putShort(udpLength.toShort())
        response.putShort(0)
        response.put(payload)

        output.write(response.array(), 0, totalLength)
    }

    private fun forwardRaw(data: ByteArray, length: Int, output: FileOutputStream) {
        try { output.write(data, 0, length) } catch (_: Exception) {}
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0; var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF); i += 2
        }
        if ((length and 1) != 0) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
