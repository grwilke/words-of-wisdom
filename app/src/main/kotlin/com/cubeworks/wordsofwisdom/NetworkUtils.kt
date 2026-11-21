package com.cubeworks.wordsofwisdom

import android.content.Context
import android.net.ConnectivityManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class QrMatrix(
    val size: Int,
    val bits: BooleanArray,
) {
    fun isDark(x: Int, y: Int): Boolean = bits[y * size + x]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrMatrix) return false
        return size == other.size && bits.contentEquals(other.bits)
    }

    override fun hashCode(): Int = 31 * size + bits.contentHashCode()
}

data class TvJoinInfo(
    val displayAddress: String,
    val qrUrl: String,
    val qr: QrMatrix,
)

object NetworkUtils {
    /**
     * HTTPS bridge on GitHub Pages (trailing slash avoids a 301 redirect that can strip #hash on
     * mobile QR scanners) that triggers Android 17 Chrome's Local Network Access prompt cleanly
     * before redirecting straight to the TV's local IP.
     */
    const val JOIN_BRIDGE_URL = "https://grwilke.github.io/words-of-wisdom/join/"

    fun currentLanAddress(context: Context): Inet4Address? =
        chooseBestIpv4(activeNetworkAddresses(context)) ?: chooseBestIpv4(interfaceAddresses())

    private fun activeNetworkAddresses(context: Context): List<InetAddress> = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val net = cm.activeNetwork ?: return emptyList()
        cm.getLinkProperties(net)?.linkAddresses.orEmpty().map { it.address }
    }.getOrDefault(emptyList())

    private fun interfaceAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
    }.getOrDefault(emptyList())

    fun chooseBestIpv4(addresses: List<InetAddress>): Inet4Address? {
        val v4 = addresses.filterIsInstance<Inet4Address>().filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        return v4.firstOrNull { it.isSiteLocalAddress } ?: v4.firstOrNull()
    }

    fun buildJoinInfo(address: Inet4Address, port: Int, useBridge: Boolean = true): TvJoinInfo {
        val hostPort = "${address.hostAddress}:$port"
        val display = "http://$hostPort"
        val qrUrl = if (useBridge) "$JOIN_BRIDGE_URL#$hostPort" else display
        return TvJoinInfo(
            displayAddress = display,
            qrUrl = qrUrl,
            qr = encodeQr(qrUrl),
        )
    }

    fun encodeQr(text: String): QrMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = matrix.width
        return QrMatrix(size, BooleanArray(size * size) { matrix[it % size, it / size] })
    }
}
