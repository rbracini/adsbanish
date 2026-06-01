package br.com.adsbanish.blocklist

import org.junit.Assert.*
import org.junit.Test

class BlocklistParserTest {

    // ── parseHostsFile ────────────────────────────────────────────────────────

    @Test
    fun `parseHostsFile extracts domain from 0_0_0_0 lines`() {
        val lines = listOf("0.0.0.0 ads.example.com")
        val result = BlocklistParser.parseHostsFile(lines)
        assertTrue(result.contains("ads.example.com"))
    }

    @Test
    fun `parseHostsFile extracts domain from 127_0_0_1 lines`() {
        val lines = listOf("127.0.0.1 tracker.example.com")
        val result = BlocklistParser.parseHostsFile(lines)
        assertTrue(result.contains("tracker.example.com"))
    }

    @Test
    fun `parseHostsFile ignores comment lines`() {
        val lines = listOf("# this is a comment", "0.0.0.0 ads.example.com")
        val result = BlocklistParser.parseHostsFile(lines)
        assertFalse(result.contains("# this is a comment"))
        assertTrue(result.contains("ads.example.com"))
    }

    @Test
    fun `parseHostsFile ignores blank lines`() {
        val lines = listOf("", "   ", "0.0.0.0 ads.example.com")
        val result = BlocklistParser.parseHostsFile(lines)
        assertEquals(1, result.size)
    }

    @Test
    fun `parseHostsFile ignores lines with other IPs`() {
        val lines = listOf("192.168.1.1 some.domain.com", "0.0.0.0 ads.example.com")
        val result = BlocklistParser.parseHostsFile(lines)
        assertFalse(result.contains("some.domain.com"))
        assertTrue(result.contains("ads.example.com"))
    }

    @Test
    fun `parseHostsFile ignores localhost and broadcasthost`() {
        val lines = listOf(
            "0.0.0.0 localhost",
            "0.0.0.0 broadcasthost",
            "0.0.0.0 ads.example.com"
        )
        val result = BlocklistParser.parseHostsFile(lines)
        assertFalse(result.contains("localhost"))
        assertFalse(result.contains("broadcasthost"))
        assertEquals(1, result.size)
    }

    @Test
    fun `parseHostsFile lowercases domains`() {
        val lines = listOf("0.0.0.0 ADS.EXAMPLE.COM")
        val result = BlocklistParser.parseHostsFile(lines)
        assertTrue(result.contains("ads.example.com"))
        assertFalse(result.contains("ADS.EXAMPLE.COM"))
    }

    @Test
    fun `parseHostsFile handles multiple spaces between ip and domain`() {
        val lines = listOf("0.0.0.0    ads.example.com   # inline comment")
        val result = BlocklistParser.parseHostsFile(lines)
        assertTrue(result.contains("ads.example.com"))
    }

    @Test
    fun `parseHostsFile returns empty set for empty input`() {
        val result = BlocklistParser.parseHostsFile(emptyList())
        assertTrue(result.isEmpty())
    }

    // ── parseAdblockFile ──────────────────────────────────────────────────────

    @Test
    fun `parseAdblockFile extracts domain from pipe-pipe format`() {
        val lines = listOf("||ads.example.com^")
        val result = BlocklistParser.parseAdblockFile(lines)
        assertTrue(result.contains("ads.example.com"))
    }

    @Test
    fun `parseAdblockFile ignores exclamation comment lines`() {
        val lines = listOf("! This is a comment", "||ads.example.com^")
        val result = BlocklistParser.parseAdblockFile(lines)
        assertEquals(1, result.size)
        assertTrue(result.contains("ads.example.com"))
    }

    @Test
    fun `parseAdblockFile ignores hash comment lines`() {
        val lines = listOf("# comment", "||tracker.example.com^")
        val result = BlocklistParser.parseAdblockFile(lines)
        assertEquals(1, result.size)
    }

    @Test
    fun `parseAdblockFile ignores lines without pipe-pipe pattern`() {
        val lines = listOf("ads.example.com", "||valid.example.com^")
        val result = BlocklistParser.parseAdblockFile(lines)
        assertEquals(1, result.size)
        assertTrue(result.contains("valid.example.com"))
    }

    @Test
    fun `parseAdblockFile lowercases domains`() {
        val lines = listOf("||ADS.EXAMPLE.COM^")
        val result = BlocklistParser.parseAdblockFile(lines)
        assertTrue(result.contains("ads.example.com"))
    }

    @Test
    fun `parseAdblockFile returns empty set for empty input`() {
        val result = BlocklistParser.parseAdblockFile(emptyList())
        assertTrue(result.isEmpty())
    }

    // ── isBlocked ─────────────────────────────────────────────────────────────

    @Test
    fun `isBlocked returns true for exact domain match`() {
        val blocklist = setOf("ads.example.com")
        assertTrue(BlocklistParser.isBlocked("ads.example.com", blocklist, emptySet()))
    }

    @Test
    fun `isBlocked returns true for subdomain of blocked domain`() {
        val blocklist = setOf("example.com")
        assertTrue(BlocklistParser.isBlocked("sub.example.com", blocklist, emptySet()))
    }

    @Test
    fun `isBlocked returns true for deep subdomain of blocked domain`() {
        val blocklist = setOf("ads.com")
        assertTrue(BlocklistParser.isBlocked("deep.sub.ads.com", blocklist, emptySet()))
    }

    @Test
    fun `isBlocked returns false for domain not in blocklist`() {
        val blocklist = setOf("ads.example.com")
        assertFalse(BlocklistParser.isBlocked("safe.example.com", blocklist, emptySet()))
    }

    @Test
    fun `isBlocked returns false for empty blocklist`() {
        assertFalse(BlocklistParser.isBlocked("ads.example.com", emptySet(), emptySet()))
    }

    @Test
    fun `isBlocked is case insensitive`() {
        val blocklist = setOf("ads.example.com")
        assertTrue(BlocklistParser.isBlocked("ADS.EXAMPLE.COM", blocklist, emptySet()))
    }

    @Test
    fun `isBlocked strips trailing dot from domain`() {
        val blocklist = setOf("ads.example.com")
        assertTrue(BlocklistParser.isBlocked("ads.example.com.", blocklist, emptySet()))
    }

    @Test
    fun `isBlocked allowlist takes priority over blocklist`() {
        val blocklist = setOf("youtube.com")
        val allowlist = setOf("youtube.com")
        assertFalse(BlocklistParser.isBlocked("youtube.com", blocklist, allowlist))
    }

    @Test
    fun `isBlocked allowlist protects subdomains`() {
        val blocklist = setOf("googleapis.com")
        val allowlist = setOf("googleapis.com")
        assertFalse(BlocklistParser.isBlocked("fonts.googleapis.com", blocklist, allowlist))
    }

    @Test
    fun `isBlocked blocks ad subdomain when parent is not allowlisted`() {
        val blocklist = setOf("pagead2.googlesyndication.com")
        val allowlist = setOf("google.com")
        assertTrue(BlocklistParser.isBlocked("pagead2.googlesyndication.com", blocklist, allowlist))
    }
}
