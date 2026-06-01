package br.com.adsbanish.blocklist

enum class BlocklistSource(
    val displayName: String,
    val description: String,
    val url: String,
    val format: BlocklistFormat
) {
    STEVENBLACK(
        displayName = "StevenBlack",
        description = "~150k domínios — Ads + Malware + Tracking",
        url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        format = BlocklistFormat.HOSTS
    ),
    OISD_SMALL(
        displayName = "OISD Small",
        description = "~50k domínios — Ads + Tracking (leve)",
        url = "https://small.oisd.nl/",
        format = BlocklistFormat.HOSTS
    ),
    OISD_BIG(
        displayName = "OISD Big",
        description = "~300k domínios — Ads + Tracking + Malware (completo)",
        url = "https://big.oisd.nl/",
        format = BlocklistFormat.HOSTS
    ),
    PETER_LOWE(
        displayName = "Peter Lowe",
        description = "~3k domínios — Foco em Ads (minimalista)",
        url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0",
        format = BlocklistFormat.HOSTS
    ),
    ADGUARD(
        displayName = "AdGuard DNS",
        description = "~50k domínios — Curado pelo AdGuard",
        url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
        format = BlocklistFormat.ADBLOCK
    ),
    STEVENBLACK_PORN(
        displayName = "StevenBlack Adult",
        description = "~15k domínios — Conteúdo adulto/pornográfico",
        url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts",
        format = BlocklistFormat.HOSTS
    );
}

enum class BlocklistFormat {
    HOSTS,
    ADBLOCK
}
