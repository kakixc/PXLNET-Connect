package io.nekohasekai.sfa.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.PxlDnsProfiles
import io.nekohasekai.sfa.utils.PxlDnsTileMode
import io.nekohasekai.sfa.utils.PxlRestartMode
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BootReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
            }

            else -> return
        }
        val pending = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (Settings.startedByUser) {
                    CrashReportManager.refresh()
                    if (CrashReportManager.unreadCount.value > 0) {
                        Settings.startedByUser = false
                        PxlDnsProfiles.setRestartMode(context, null)
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        when (PxlDnsProfiles.restartMode(context)) {
                            PxlRestartMode.DNS_ONLY -> StartServiceCoordinator.startDnsOnly(
                                StartServiceCoordinator.Source.BOOT, PxlDnsProfiles.restartDnsProfileId(context))
                            PxlRestartMode.DNS_AND_VPN -> StartServiceCoordinator.start(
                                StartServiceCoordinator.Source.BOOT, PxlDnsTileMode.DNS_AND_VPN, restoreMode = true)
                            PxlRestartMode.VPN -> StartServiceCoordinator.start(
                                StartServiceCoordinator.Source.BOOT, restoreMode = true)
                            null -> BoxService.start()
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
