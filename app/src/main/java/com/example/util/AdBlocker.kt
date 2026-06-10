package com.example.util

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {
    private val AD_DOMAINS = setOf(
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "google-analytics.com",
        "adsterra.com",
        "exoclick.com",
        "juicyads.com",
        "trafficfactory.biz",
        "popads.net",
        "popcash.net",
        "jnbhi.com",
        "propellerads.com",
        "yandex.ru",
        "histats.com",
        "statcounter.com",
        "onclickads.net",
        "onclickperformance.com",
        "onclck.com",
        "adnx.com",
        "adnxs.com",
        "moatads.com",
        "smartadserver.com",
        "taboola.com",
        "outbrain.com",
        "mgid.com",
        "ero-advertising.com",
        "trafficjunky.com",
        "plugrush.com",
        "realsrv.com",
        "byseqekaho.com",
        "byseqekaho.com/ads",
        "coomeet.com",
        "cam8.com",
        "camsoda.com",
        "stripchat.com",
        "bongacams.com"
    )

    private val AD_KEYWORDS = listOf(
        "/ads/",
        "ads.",
        "adserver",
        "adsystem",
        "adservice",
        "popunder",
        "popover",
        "banner",
        "tracker",
        "analytics",
        "histats",
        "statcounter",
        "onclick",
        "traffic",
        "pnh.gratis",
        "pornovip.gratis/ads",
        "ad_system"
    )

    fun isAd(url: String): Boolean {
        val lower = url.lowercase()
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return false
        }
        val host = uri.host ?: return false

        // Check exact match or subdomain match
        if (AD_DOMAINS.contains(host)) return true
        for (domain in AD_DOMAINS) {
            if (host.endsWith(".$domain")) {
                return true
            }
        }

        // Check common keywords
        for (keyword in AD_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true
            }
        }

        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
