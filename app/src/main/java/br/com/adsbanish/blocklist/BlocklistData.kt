package br.com.adsbanish.blocklist

object BlocklistData {

    val BLOCKED_DOMAINS: Set<String> = setOf(
        // Google Ads
        "googleadservices.com", "googlesyndication.com", "pagead2.googlesyndication.com",
        "doubleclick.net", "ad.doubleclick.net", "googleads.g.doubleclick.net",
        "adservice.google.com", "adservice.google.com.br", "googleadsserving.cn",
        "tpc.googlesyndication.com", "adwords.google.com",
        // Google Analytics / Tag Manager
        "google-analytics.com", "www.google-analytics.com", "ssl.google-analytics.com",
        "analytics.google.com", "googletagmanager.com", "googletagservices.com",
        "stats.g.doubleclick.net",
        // Facebook / Meta Ads
        "an.facebook.com", "connect.facebook.net", "static.ads-twitter.com",
        "graph.facebook.com", "pixel.facebook.com",
        // Twitter / X Ads
        "ads.twitter.com", "ads-api.twitter.com", "analytics.twitter.com",
        "syndication.twitter.com",
        // Amazon Ads
        "advertising.amazon.com", "aax.amazon-adsystem.com", "s.amazon-adsystem.com",
        "c.amazon-adsystem.com", "fls-na.amazon-adsystem.com",
        // Outbrain
        "outbrain.com", "widgets.outbrain.com", "traffic.outbrain.com", "log.outbrain.com",
        "amplify.outbrain.com",
        // Taboola
        "taboola.com", "trc.taboola.com", "cdn.taboola.com", "images.taboola.com",
        "nr-data.taboola.com",
        // Criteo
        "criteo.com", "rtax.criteo.com", "static.criteo.net", "dis.us.criteo.com",
        "sdb.criteo.com",
        // AppNexus / Xandr
        "appnexus.com", "ib.adnxs.com", "secure.adnxs.com", "cdn.adnxs.com",
        // OpenX
        "openx.net", "openx.com", "us-u.openx.net", "eu-u.openx.net",
        // Rubicon / Magnite
        "rubiconproject.com", "fastlane.rubiconproject.com", "prebid.rubiconproject.com",
        // PubMatic
        "pubmatic.com", "ads.pubmatic.com", "image6.pubmatic.com", "simage2.pubmatic.com",
        // Index Exchange
        "casalemedia.com", "indexexchange.com",
        // The Trade Desk
        "adsrvr.org", "match.adsrvr.org", "direct.adsrvr.org",
        // MediaMath
        "mathtag.com", "pixel.mathtag.com",
        // Quantcast
        "quantserve.com", "edge.quantserve.com",
        // AdRoll
        "adroll.com", "d.adroll.com", "s.adroll.com",
        // Sizmek / Emodo
        "serving-sys.com", "bs.serving-sys.com", "secure-ds.serving-sys.com",
        // Flurry (Yahoo Analytics)
        "flurry.com", "data.flurry.com",
        // Chartboost
        "chartboost.com", "live.chartboost.com",
        // Unity Ads
        "unityads.unity3d.com", "auction.unityads.unity3d.com", "config.unityads.unity3d.com",
        // ironSource
        "ironsrc.com", "gw.ironsrc.com", "mediation.ironsrc.com",
        // Vungle
        "vungle.com", "ads.vungle.com", "cdn-lb.vungle.com",
        // InMobi
        "inmobi.com", "cf.hb.aiv2.inmobi.com", "sync.inmobi.com",
        // Smaato
        "smaato.net", "soma.smaato.net",
        // Fyber
        "fyber.com", "api.fyber.com",
        // Yahoo / Oath Ads
        "ads.yahoo.com", "beap.gemini.yahoo.com", "om.yahoo.com",
        // Media.net
        "media.net", "data.media.net", "static.media.net",
        // Conversant
        "conversantmedia.com", "dt.adsafeprotected.com",
        // Yandex Ads
        "an.yandex.ru", "awaps.yandex.ru", "bs.yandex.ru",
        // Digital Turbine
        "digitalturbine.com", "ads.digitalturbine.com",
        // LiveRamp
        "liveramp.com", "idsync.rlcdn.com", "rlcdn.com",
        // Nielsen
        "nielsen.com", "secure-dcr.imrworldwide.com", "imrworldwide.com",
        // Comscore
        "comscore.com", "beacon.scorecardresearch.com", "sb.scorecardresearch.com",
        // Sharethrough
        "sharethrough.com", "btlr.sharethrough.com",
        // Teads
        "teads.tv", "a.teads.tv", "cdn.teads.tv",
        // TripleLift
        "triplelift.com", "tlx.3lift.com",
        // Other trackers
        "demdex.net", "bluekai.com", "krxd.net",
        "addthis.com", "s7.addthis.com",
        "hotjar.com", "static.hotjar.com",
        "mouseflow.com",
        "omtrdc.net", "adobedtm.com",
        "newrelic.com", "js-agent.newrelic.com",
        "mixpanel.com", "api.mixpanel.com",
        "amplitude.com", "api.amplitude.com",
        "segment.com", "api.segment.io",
        "braze.com", "sdk.iad-01.braze.com",
        "appsflyer.com", "t.appsflyer.com",
        "adjust.com", "app.adjust.com",
        "branch.io", "api2.branch.io",
    )

    fun isBlocked(domain: String): Boolean {
        var current = domain.lowercase().trimEnd('.')
        while (current.contains('.')) {
            if (BLOCKED_DOMAINS.contains(current)) return true
            current = current.substringAfter('.')
        }
        return false
    }
}
