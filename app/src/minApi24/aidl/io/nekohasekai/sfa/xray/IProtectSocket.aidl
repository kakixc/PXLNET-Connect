package io.nekohasekai.sfa.xray;

import android.os.ParcelFileDescriptor;

/**
 * Main-process endpoint for protecting an Xray-created socket from the VPN TUN.
 *
 * The implementation belongs to the active VPN owner.  It must return false
 * when the VPN has been revoked or the owner is no longer able to protect the
 * descriptor.
 */
interface IProtectSocket {
    boolean isReady();
    boolean protectSocket(in ParcelFileDescriptor socket);
}
