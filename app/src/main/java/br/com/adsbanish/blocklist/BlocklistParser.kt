package br.com.adsbanish.blocklist

object BlocklistParser {

    fun parseHostsFile(lines: List<String>): Set<String> {
        val result = HashSet<String>(lines.size / 2)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#')) continue
            val parts = trimmed.split("\\s+".toRegex())
            if (parts.size < 2) continue
            val ip = parts[0]
            if (ip != "0.0.0.0" && ip != "127.0.0.1") continue
            val domain = parts[1].lowercase()
            if (domain == "localhost" || domain == "broadcasthost") continue
            result.add(domain)
        }
        return result
    }

    fun parseAdblockFile(lines: List<String>): Set<String> {
        val result = HashSet<String>(lines.size / 2)
        val regex = Regex("""^\|\|([a-zA-Z0-9.\-]+)\^""")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith('!') || trimmed.startsWith('#')) continue
            regex.find(trimmed)?.groupValues?.getOrNull(1)?.lowercase()?.let {
                result.add(it)
            }
        }
        return result
    }

    fun isBlocked(domain: String, blocklist: Set<String>, allowlist: Set<String>): Boolean {
        if (blocklist.isEmpty()) return false
        var current = domain.lowercase().trimEnd('.')
        // Allowlist tem prioridade absoluta
        var check = current
        while (check.contains('.')) {
            if (allowlist.contains(check)) return false
            check = check.substringAfter('.')
        }
        // Verifica blocklist percorrendo hierarquia de domínios
        while (current.contains('.')) {
            if (blocklist.contains(current)) return true
            current = current.substringAfter('.')
        }
        return false
    }
}
