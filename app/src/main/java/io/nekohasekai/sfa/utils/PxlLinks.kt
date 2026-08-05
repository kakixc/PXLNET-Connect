package io.nekohasekai.sfa.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object PxlLinks {
    const val GITHUB = "https://github.com/kakixc/PXLNET-Connect"
    const val DOWNLOAD = "$GITHUB/releases/latest"
    const val TELEGRAM_BOT = "https://t.me/pxlnet_bot"
    const val TELEGRAM_CHANNEL = "https://t.me/pxlnet"
    const val SING_BOX_ANDROID = "https://github.com/SagerNet/sing-box-for-android"
    const val HAPP_ANDROID = "https://github.com/Happ-proxy/happ-android"

    fun open(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
