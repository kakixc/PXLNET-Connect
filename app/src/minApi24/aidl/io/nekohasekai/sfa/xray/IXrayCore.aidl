package io.nekohasekai.sfa.xray;

import io.nekohasekai.sfa.xray.IProtectSocket;

/** Private, in-app control surface for the XHTTP sidecar process. */
interface IXrayCore {
    /** Returns false when no live VPN protector is available or Xray rejects the configuration. */
    boolean startCore(String configJson, IProtectSocket protector);
    void stopCore();
    boolean isCoreRunning();
}
