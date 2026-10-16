package io.nekohasekai.sfa

internal object PxlProcessRole {
    fun isXraySidecar(processName: String?, packageName: String): Boolean =
        processName == "$packageName:pxlxray"

    fun isXrayProbe(processName: String?, packageName: String): Boolean =
        processName == "$packageName:pxlxrayprobe"
}
