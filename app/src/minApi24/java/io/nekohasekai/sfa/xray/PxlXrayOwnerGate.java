package io.nekohasekai.sfa.xray;

/** Prevents a delayed death notification from an old Binder owner closing a new core. */
final class PxlXrayOwnerGate {
    private PxlXrayOwnerGate() {}

    static boolean isCurrent(Object deadOwner, Object activeOwner) {
        return deadOwner != null && deadOwner == activeOwner;
    }
}
