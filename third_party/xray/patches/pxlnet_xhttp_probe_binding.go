package libv2ray

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/xtls/xray-core/app/proxyman"
	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/infra/conf/serial"
	"github.com/xtls/xray-core/proxy/vless/outbound"
	"github.com/xtls/xray-core/transport/internet"
	"github.com/xtls/xray-core/transport/internet/splithttp"
)

// ProbeXhttpEndpoint runs one bounded transport-only check in the caller's
// process. The caller must register a fail-closed VPN socket protector first.
func ProbeXhttpEndpoint(configJSON, outboundTag string, timeoutMs int) (int64, error) {
	if strings.TrimSpace(outboundTag) == "" || timeoutMs < 500 || timeoutMs > 10000 {
		return -1, fmt.Errorf("invalid XHTTP probe arguments")
	}
	config, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		return -1, fmt.Errorf("invalid XHTTP configuration")
	}
	var sender *proxyman.SenderConfig
	var proxy *outbound.Config
	for _, handler := range config.Outbound {
		if handler.Tag != outboundTag {
			continue
		}
		if sender != nil {
			return -1, fmt.Errorf("duplicate XHTTP outbound")
		}
		senderMessage, senderErr := handler.SenderSettings.GetInstance()
		proxyMessage, proxyErr := handler.ProxySettings.GetInstance()
		if senderErr != nil || proxyErr != nil {
			return -1, fmt.Errorf("invalid XHTTP outbound")
		}
		var ok bool
		sender, ok = senderMessage.(*proxyman.SenderConfig)
		if !ok {
			return -1, fmt.Errorf("unsupported XHTTP sender")
		}
		proxy, ok = proxyMessage.(*outbound.Config)
		if !ok {
			return -1, fmt.Errorf("unsupported XHTTP protocol")
		}
	}
	if sender == nil || sender.StreamSettings == nil || proxy == nil || proxy.Vnext == nil || proxy.Vnext.Address == nil {
		return -1, fmt.Errorf("XHTTP outbound not found")
	}
	stream, err := internet.ToMemoryStreamConfig(sender.StreamSettings)
	if err != nil || stream.ProtocolName != "splithttp" {
		return -1, fmt.Errorf("XHTTP transport unavailable")
	}
	dest := xnet.TCPDestination(proxy.Vnext.Address.AsAddress(), xnet.Port(proxy.Vnext.Port))
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()
	duration, err := splithttp.ProbeXhttpEndpoint(ctx, dest, stream)
	if err != nil {
		return -1, fmt.Errorf("XHTTP endpoint did not respond")
	}
	if duration <= 0 {
		return -1, fmt.Errorf("XHTTP endpoint duration unavailable")
	}
	if milliseconds := duration.Milliseconds(); milliseconds > 0 {
		return milliseconds, nil
	}
	return 1, nil
}
