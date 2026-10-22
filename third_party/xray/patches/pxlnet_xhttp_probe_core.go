package splithttp

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"

	"github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/transport/internet"
	"github.com/xtls/xray-core/transport/internet/reality"
	"github.com/xtls/xray-core/transport/internet/tls"
)

// ProbeXhttpEndpoint measures the response from Xray's XHTTP endpoint itself.
// Unlike internet.Dial, it waits for an HTTP response. It does not validate
// VLESS credentials or fetch a third-party website.
func ProbeXhttpEndpoint(ctx context.Context, dest net.Destination, stream *internet.MemoryStreamConfig) (time.Duration, error) {
	transport, ok := stream.ProtocolSettings.(*Config)
	if !ok || transport.XPaddingObfsMode {
		return 0, fmt.Errorf("unsupported XHTTP probe settings")
	}
	client, ok := createHTTPClient(dest, stream).(*DefaultDialerClient)
	if !ok {
		return 0, fmt.Errorf("direct XHTTP client unavailable")
	}
	defer client.Close()
	defer client.client.CloseIdleConnections()

	requestURL := url.URL{Scheme: "http", Host: transport.Host, Path: transport.GetNormalizedPath(), RawQuery: transport.GetNormalizedQuery()}
	if tlsConfig := tls.ConfigFromStreamSettings(stream); tlsConfig != nil {
		requestURL.Scheme = "https"
		if requestURL.Host == "" {
			requestURL.Host = tlsConfig.ServerName
		}
	}
	if realityConfig := reality.ConfigFromStreamSettings(stream); realityConfig != nil {
		requestURL.Scheme = "https"
		if requestURL.Host == "" {
			requestURL.Host = realityConfig.ServerName
		}
	}
	if requestURL.Host == "" {
		requestURL.Host = dest.Address.String()
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodOptions, requestURL.String(), nil)
	if err != nil {
		return 0, err
	}
	start := time.Now()
	response, err := client.client.Do(req)
	if err != nil {
		return 0, err
	}
	defer response.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 1024))
	if response.StatusCode != http.StatusOK || response.Header.Get("X-Padding") == "" {
		return 0, fmt.Errorf("XHTTP endpoint did not confirm the probe")
	}
	return time.Since(start), nil
}
