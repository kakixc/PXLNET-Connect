package io.nekohasekai.sfa.utils

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.bg.TileService

object PxlQuickTile {
    enum class Result {
        Added,
        AlreadyAdded,
        Rejected,
        OpenQuickSettings,
    }

    fun requestAdd(context: Context, callback: (Result) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            callback(Result.OpenQuickSettings)
            return
        }
        val manager = context.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            callback(Result.Rejected)
            return
        }
        manager.requestAddTileService(
            ComponentName(context, TileService::class.java),
            context.getString(R.string.pxlnet_tile_label),
            Icon.createWithResource(context, R.drawable.ic_qs_pxlnet),
            context.mainExecutor,
        ) { result ->
            callback(
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> Result.Added
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> Result.AlreadyAdded
                    else -> Result.Rejected
                },
            )
        }
    }
}
