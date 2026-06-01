package br.com.adsbanish.vpn

class DnsPacket private constructor(
    val id: Int,
    val flags: Int,
    val questionName: String,
    private val rawData: ByteArray,
    private val questionEnd: Int
) {
    companion object {

        /**
         * Parseia uma query DNS (RFC 1035).
         * Retorna null se o payload for inválido ou não for uma query.
         */
        fun parse(data: ByteArray, length: Int): DnsPacket? {
            if (length < 12) return null

            val id    = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            val flags = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)

            if (flags and 0x8000 != 0) return null  // QR=1 → é resposta, não query
            val qdcount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            if (qdcount == 0) return null

            // Lê QNAME: sequência de labels terminada por byte 0x00
            val sb = StringBuilder()
            var pos = 12
            while (pos < length) {
                val labelLen = data[pos].toInt() and 0xFF
                if (labelLen == 0) { pos++; break }
                if (labelLen >= 0xC0) return null   // compressão de ponteiro não esperada em queries
                if (pos + labelLen >= length) return null
                if (sb.isNotEmpty()) sb.append('.')
                for (i in 1..labelLen) {
                    sb.append((data[pos + i].toInt() and 0xFF).toChar())
                }
                pos += labelLen + 1
            }

            if (pos + 4 > length) return null  // precisa de QTYPE + QCLASS

            return DnsPacket(
                id           = id,
                flags        = flags,
                questionName = sb.toString().lowercase(),
                rawData      = data.copyOf(length),
                questionEnd  = pos + 4
            )
        }

        /**
         * Constrói uma resposta NXDOMAIN para a query recebida.
         * Copia o header + question section e ajusta os flags de resposta.
         */
        fun buildNxdomainResponse(query: DnsPacket): ByteArray {
            val response = query.rawData.copyOfRange(0, query.questionEnd)

            // ID igual ao da query
            response[0] = (query.id shr 8).toByte()
            response[1] = (query.id and 0xFF).toByte()

            // Flags: QR=1 (resposta), preserva RD da query, RA=1, RCODE=3 (NXDOMAIN)
            val rd = if (query.flags and 0x0100 != 0) 0x01 else 0x00
            response[2] = (0x80 or rd).toByte()  // QR=1, OPCODE=0, AA=0, TC=0, RD=?
            response[3] = 0x83.toByte()           // RA=1, RCODE=3

            // Contagens: QDCOUNT=1, ANCOUNT=NSCOUNT=ARCOUNT=0
            response[4] = 0; response[5] = 1
            response[6] = 0; response[7] = 0
            response[8] = 0; response[9] = 0
            response[10] = 0; response[11] = 0

            return response
        }
    }
}
