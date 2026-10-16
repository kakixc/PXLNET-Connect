package io.nekohasekai.sfa.xray;

import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import java.util.concurrent.atomic.AtomicBoolean;
import net.pxlnet.xraybridge.libv2ray.Libv2ray;

/**
 * Isolated, bind-only XHTTP endpoint probe. It never establishes a second TUN
 * and cannot stop or replace the live :pxlxray companion process.
 */
public final class PxlXrayProbeService extends VpnService {
    private final Object operationLock = new Object();
    private final AtomicBoolean active = new AtomicBoolean(false);

    private final IXrayProbe.Stub binder = new IXrayProbe.Stub() {
        @Override
        public long measureXhttp(String configJson, String outboundTag, int timeoutMs) {
            if (configJson == null || outboundTag == null || configJson.length() > 262144 ||
                timeoutMs < 500 || timeoutMs > 10000) return -1;
            synchronized (operationLock) {
                // A prepared app can protect a socket without establishing a TUN.
                // If consent was revoked, never attempt an unprotected request.
                if (VpnService.prepare(PxlXrayProbeService.this) != null) return -1;
                try {
                    active.set(true);
                    Libv2ray.registerFailClosedVpnProtector(PxlXrayProbeService.this::protectSocket);
                    long elapsed = Libv2ray.probeXhttpEndpoint(configJson, outboundTag, timeoutMs);
                    return elapsed > 0 ? elapsed : -1;
                } catch (Exception | LinkageError ignored) {
                    // Native details may contain server data. The UI only gets no result.
                    return -1;
                } finally {
                    active.set(false);
                    try {
                        Libv2ray.clearFailClosedVpnProtector();
                    } catch (RuntimeException | LinkageError ignored) {
                        // This process is independent of the live VPN companion.
                    }
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        synchronized (operationLock) {
            active.set(false);
            try {
                Libv2ray.clearFailClosedVpnProtector();
            } catch (RuntimeException | LinkageError ignored) {
                // Teardown remains fail closed.
            }
        }
        super.onDestroy();
    }

    private boolean protectSocket(long fd) {
        if (!active.get() || fd < 0 || fd > Integer.MAX_VALUE ||
            VpnService.prepare(this) != null) return false;
        try {
            return protect((int) fd);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
