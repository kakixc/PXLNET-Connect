package io.nekohasekai.sfa.xray;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import net.pxlnet.xraybridge.libv2ray.CoreCallbackHandler;
import net.pxlnet.xraybridge.libv2ray.CoreController;
import net.pxlnet.xraybridge.libv2ray.Libv2ray;

/**
 * Private Xray process used only by the future XHTTP selector.
 *
 * It neither creates a TUN interface nor starts itself.  The VPN owner binds to
 * this service and supplies a short-lived Binder callback which delegates every
 * Xray socket to {@code VpnService.protect(fd)}.  Any missing/dead callback is
 * deliberately treated as a failed protection request, so Xray cannot bypass
 * the app VPN by accident.
 */
public final class PxlXrayService extends Service {
    /** Serializes lifecycle operations, never the native socket-protection callback. */
    private final Object operationLock = new Object();

    private volatile CoreController controller;
    private volatile IProtectSocket protector;
    private IBinder protectorBinder;
    private IBinder.DeathRecipient protectorDeathRecipient;

    private final IXrayCore.Stub binder = new IXrayCore.Stub() {
        @Override
        public boolean startCore(String configJson, IProtectSocket nextProtector) {
            if (configJson == null || configJson.trim().isEmpty() || nextProtector == null) return false;
            synchronized (operationLock) {
                stopLocked();
                if (!isProtectorReady(nextProtector)) return false;
                if (!installProtectorLocked(nextProtector)) return false;
                try {
                    Libv2ray.registerFailClosedVpnProtector(PxlXrayService.this::protectSocket);
                    CoreController nextController = Libv2ray.newCoreController(NO_OP_CALLBACKS);
                    // Publish before start: native startup can immediately ask to protect a socket.
                    controller = nextController;
                    nextController.startLoop(configJson, 0);
                    if (nextController.getIsRunning()) return true;
                } catch (Exception | LinkageError ignored) {
                    // Fall through to the shared fail-closed teardown below.
                }
                stopLocked();
                return false;
            }
        }

        @Override
        public void stopCore() {
            synchronized (operationLock) {
                stopLocked();
            }
        }

        @Override
        public boolean isCoreRunning() {
            CoreController activeController = controller;
            if (activeController == null) return false;
            try {
                return activeController.getIsRunning();
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
        }
    };

    private static final CoreCallbackHandler NO_OP_CALLBACKS = new CoreCallbackHandler() {
        @Override
        public long startup() {
            return 0;
        }

        @Override
        public long shutdown() {
            return 0;
        }

        @Override
        public long onEmitStatus(long code, String message) {
            // Core messages may include endpoint details: never emit them to app logs.
            return 0;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        synchronized (operationLock) {
            stopLocked();
        }
        super.onDestroy();
    }

    private boolean isProtectorReady(IProtectSocket candidate) {
        try {
            return candidate.isReady();
        } catch (RemoteException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean installProtectorLocked(IProtectSocket nextProtector) {
        final IBinder nextBinder = nextProtector.asBinder();
        if (nextBinder == null || !nextBinder.isBinderAlive()) return false;
        final IBinder.DeathRecipient nextDeathRecipient = () -> onProtectorDied(nextBinder);
        try {
            nextBinder.linkToDeath(nextDeathRecipient, 0);
        } catch (RemoteException ignored) {
            return false;
        }
        protector = nextProtector;
        protectorBinder = nextBinder;
        protectorDeathRecipient = nextDeathRecipient;
        return true;
    }

    private boolean protectSocket(long fd) {
        if (fd < 0 || fd > Integer.MAX_VALUE) return false;
        final IProtectSocket activeProtector = protector;
        if (activeProtector == null) return false;
        try (ParcelFileDescriptor duplicate = ParcelFileDescriptor.fromFd((int) fd)) {
            return activeProtector.protectSocket(duplicate);
        } catch (RemoteException | RuntimeException ignored) {
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void onProtectorDied(IBinder deadBinder) {
        synchronized (operationLock) {
            // unlinkToDeath may report an already-dead old owner whose callback is
            // still pending. It must not tear down a replacement VPN session.
            if (PxlXrayOwnerGate.isCurrent(deadBinder, protectorBinder)) {
                stopLocked();
            }
        }
    }

    private void stopLocked() {
        CoreController activeController = controller;
        controller = null;
        // Reject every callback before asking native code to shut down.
        protector = null;
        if (activeController != null) {
            try {
                activeController.stopLoop();
            } catch (Exception | LinkageError ignored) {
                // Best effort during teardown; the protector is still cleared below.
            }
        }
        try {
            Libv2ray.clearFailClosedVpnProtector();
        } catch (RuntimeException | LinkageError ignored) {
            // The native bridge may already have been released while this process exits.
        }
        clearProtectorLocked();
    }

    private void clearProtectorLocked() {
        if (protectorBinder != null && protectorDeathRecipient != null) {
            try {
                protectorBinder.unlinkToDeath(protectorDeathRecipient, 0);
            } catch (RuntimeException ignored) {
                // The remote binder may already be dead.
            }
        }
        protector = null;
        protectorBinder = null;
        protectorDeathRecipient = null;
    }
}
