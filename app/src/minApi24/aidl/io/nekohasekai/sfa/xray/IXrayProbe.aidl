package io.nekohasekai.sfa.xray;

/** Internal, bind-only probe. A negative result means no trustworthy measurement. */
interface IXrayProbe {
    long measureXhttp(String configJson, String outboundTag, int timeoutMs);
}
