package io.nekohasekai.sfa.xray;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Technical product-service smoke only; it has no subscription or external VPN server. */
@RunWith(AndroidJUnit4.class)
public class PxlXrayServiceLifecycleTest {
    @Test
    public void boundCompanionReplacesListenerAndStops() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch connected = new CountDownLatch(1);
        final IXrayCore[] core = new IXrayCore[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                core[0] = IXrayCore.Stub.asInterface(binder);
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { connected.countDown(); }
        };
        Intent intent = new Intent(context, PxlXrayService.class);
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE));
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS));
            assertNotNull(core[0]);
            int firstPort = availablePort();
            int secondPort = availablePort();
            while (secondPort == firstPort) secondPort = availablePort();
            IProtectSocket protector = new IProtectSocket.Stub() {
                @Override public boolean isReady() { return true; }
                @Override public boolean protectSocket(ParcelFileDescriptor socket) { return false; }
            };
            assertTrue(core[0].startCore(config(firstPort), protector));
            assertTrue(core[0].isCoreRunning());
            assertTrue(canConnect(firstPort));
            assertTrue(core[0].startCore(config(secondPort), protector));
            assertFalse(canConnect(firstPort));
            assertTrue(canConnect(secondPort));
            core[0].stopCore();
            assertFalse(core[0].isCoreRunning());
            assertFalse(canConnect(secondPort));
        } finally {
            if (core[0] != null) core[0].stopCore();
            context.unbindService(connection);
        }
    }

    /** Losing the isolated native process must remove its listener and notify the binding. */
    @Test
    public void isolatedCompanionDeathClosesListener() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        final IXrayCore[] core = new IXrayCore[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                core[0] = IXrayCore.Stub.asInterface(binder);
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                disconnected.countDown();
            }
        };
        assertTrue(context.bindService(new Intent(context, PxlXrayService.class),
            connection, Context.BIND_AUTO_CREATE));
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS));
            assertNotNull(core[0]);
            int port = availablePort();
            IProtectSocket protector = new IProtectSocket.Stub() {
                @Override public boolean isReady() { return true; }
                @Override public boolean protectSocket(ParcelFileDescriptor socket) { return false; }
            };
            assertTrue(core[0].startCore(config(port), protector));
            assertTrue(canConnect(port));
            int remotePid = xrayProcessPid(context);
            assertTrue(remotePid > 0);
            assertNotEquals(Process.myPid(), remotePid);
            Process.killProcess(remotePid);
            assertTrue(disconnected.await(10, TimeUnit.SECONDS));
            boolean listenerClosed = false;
            for (int attempt = 0; attempt < 20; attempt++) {
                if (!canConnect(port)) { listenerClosed = true; break; }
                Thread.sleep(100);
            }
            assertTrue(listenerClosed);
        } finally {
            context.unbindService(connection);
        }
    }

    /** Requires the ignored localhost Xray server and HTTP server on the host PC. */
    @Test
    public void localXhttpCarriesHttpThroughProductCompanion() throws Exception {
        Assume.assumeTrue("Local Xray and HTTP test servers are not running",
            canConnect("10.0.2.2", 19080) && canConnect("10.0.2.2", 19082) &&
                !canConnect("10.0.2.2", 19083));
        assertLocalXhttpCarriesHttp(false);
    }

    /** Same product Binder/data path, but with a local test-only REALITY handshake. */
    @Test
    public void localRealityXhttpCarriesHttpThroughProductCompanion() throws Exception {
        Assume.assumeTrue("Local REALITY/XHTTP and HTTP test servers are not running",
            canConnect("10.0.2.2", 19080) && canConnect("10.0.2.2", 19082) &&
                canConnect("10.0.2.2", 19083));
        assertLocalXhttpCarriesHttp(true);
    }

    private void assertLocalXhttpCarriesHttp(boolean reality) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch connected = new CountDownLatch(1);
        final IXrayCore[] core = new IXrayCore[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                core[0] = IXrayCore.Stub.asInterface(binder);
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { connected.countDown(); }
        };
        assertTrue(context.bindService(new Intent(context, PxlXrayService.class),
            connection, Context.BIND_AUTO_CREATE));
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS));
            assertNotNull(core[0]);
            int port = availablePort();
            AtomicInteger protectionRequests = new AtomicInteger();
            IProtectSocket protector = new IProtectSocket.Stub() {
                @Override public boolean isReady() { return true; }
                @Override public boolean protectSocket(ParcelFileDescriptor socket) {
                    protectionRequests.incrementAndGet();
                    try { socket.close(); } catch (Exception ignored) { }
                    // Technical test only: no VPN TUN exists on this clean emulator.
                    return true;
                }
            };
            assertTrue(core[0].startCore(reality ? clientRealityConfig(port) : clientConfig(port), protector));
            try (Socket socks = new Socket("127.0.0.1", port)) {
                socks.setSoTimeout(5000);
                DataInputStream input = new DataInputStream(socks.getInputStream());
                OutputStream output = socks.getOutputStream();
                output.write(new byte[] {5, 1, 2});
                output.flush();
                assertEquals(5, input.readUnsignedByte());
                assertEquals(2, input.readUnsignedByte());
                byte[] username = "pxlnet-test".getBytes(StandardCharsets.US_ASCII);
                byte[] password = "local-only-test".getBytes(StandardCharsets.US_ASCII);
                output.write(1);
                output.write(username.length);
                output.write(username);
                output.write(password.length);
                output.write(password);
                output.flush();
                assertEquals(1, input.readUnsignedByte());
                assertEquals(0, input.readUnsignedByte());
                output.write(new byte[] {5, 1, 0, 1, 127, 0, 0, 1, 0x4a, (byte) 0x8a}); // 127.0.0.1:19082
                output.flush();
                assertEquals(5, input.readUnsignedByte());
                assertEquals(0, input.readUnsignedByte());
                input.readFully(new byte[8]);
                output.write("GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
                output.flush();
                String status = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII)).readLine();
                assertTrue(status != null && status.contains(" 200 "));
            }
            assertTrue(protectionRequests.get() > 0);
        } finally {
            if (core[0] != null) core[0].stopCore();
            context.unbindService(connection);
        }
    }

    /** A rejected protect callback must never turn into unprotected XHTTP egress. */
    @Test
    public void localXhttpDoesNotBypassDeniedProtection() throws Exception {
        Assume.assumeTrue("Local Xray and HTTP test servers are not running",
            canConnect("10.0.2.2", 19080) && canConnect("10.0.2.2", 19082) &&
                !canConnect("10.0.2.2", 19083));
        assertDeniedProtectionBlocksXhttp(false);
    }

    @Test
    public void localRealityXhttpDoesNotBypassDeniedProtection() throws Exception {
        Assume.assumeTrue("Local REALITY/XHTTP and HTTP test servers are not running",
            canConnect("10.0.2.2", 19080) && canConnect("10.0.2.2", 19082) &&
                canConnect("10.0.2.2", 19083));
        assertDeniedProtectionBlocksXhttp(true);
    }

    private void assertDeniedProtectionBlocksXhttp(boolean reality) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch connected = new CountDownLatch(1);
        final IXrayCore[] core = new IXrayCore[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                core[0] = IXrayCore.Stub.asInterface(binder);
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { connected.countDown(); }
        };
        assertTrue(context.bindService(new Intent(context, PxlXrayService.class),
            connection, Context.BIND_AUTO_CREATE));
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS));
            assertNotNull(core[0]);
            int port = availablePort();
            AtomicInteger protectionRequests = new AtomicInteger();
            IProtectSocket protector = new IProtectSocket.Stub() {
                @Override public boolean isReady() { return true; }
                @Override public boolean protectSocket(ParcelFileDescriptor socket) {
                    protectionRequests.incrementAndGet();
                    try { socket.close(); } catch (Exception ignored) { }
                    return false;
                }
            };
            assertTrue(core[0].startCore(reality ? clientRealityConfig(port) : clientConfig(port), protector));
            boolean gotHttp200 = false;
            try (Socket socks = new Socket("127.0.0.1", port)) {
                socks.setSoTimeout(2000);
                DataInputStream input = new DataInputStream(socks.getInputStream());
                OutputStream output = socks.getOutputStream();
                output.write(new byte[] {5, 1, 2});
                output.flush();
                assertEquals(5, input.readUnsignedByte());
                assertEquals(2, input.readUnsignedByte());
                byte[] username = "pxlnet-test".getBytes(StandardCharsets.US_ASCII);
                byte[] password = "local-only-test".getBytes(StandardCharsets.US_ASCII);
                output.write(1);
                output.write(username.length);
                output.write(username);
                output.write(password.length);
                output.write(password);
                output.flush();
                assertEquals(1, input.readUnsignedByte());
                assertEquals(0, input.readUnsignedByte());
                output.write(new byte[] {5, 1, 0, 1, 127, 0, 0, 1, 0x4a, (byte) 0x8a});
                output.flush();
                if (input.readUnsignedByte() == 5 && input.readUnsignedByte() == 0) {
                    input.readFully(new byte[8]);
                    output.write("GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    String status = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII)).readLine();
                    gotHttp200 = status != null && status.contains(" 200 ");
                }
            } catch (IOException expectedWhenProtectionFails) {
                // Either a SOCKS rejection or a closed/timed-out upstream is fail-closed.
            }
            assertTrue(protectionRequests.get() > 0);
            assertFalse(gotHttp200);
        } finally {
            if (core[0] != null) core[0].stopCore();
            context.unbindService(connection);
        }
    }

    private static String clientConfig(int port) {
        return "{\"inbounds\":[{\"listen\":\"127.0.0.1\",\"port\":" + port
            + ",\"protocol\":\"socks\",\"settings\":{\"auth\":\"password\","
            + "\"accounts\":[{\"user\":\"pxlnet-test\",\"pass\":\"local-only-test\"}],\"udp\":false}}],"
            + "\"outbounds\":[{\"tag\":\"pxlnet-test\",\"protocol\":\"vless\",\"settings\":{\"vnext\":[{\"address\":\"10.0.2.2\","
            + "\"port\":19080,\"users\":[{\"id\":\"11111111-1111-4111-8111-111111111111\",\"encryption\":\"none\"}]}]},"
            + "\"streamSettings\":{\"network\":\"xhttp\",\"security\":\"none\","
            + "\"xhttpSettings\":{\"path\":\"/pxlnet-local-test\",\"mode\":\"auto\"}}},"
            + "{\"tag\":\"pxlnet-reject\",\"protocol\":\"blackhole\"}],"
            + "\"routing\":{\"rules\":[{\"type\":\"field\",\"user\":[\"pxlnet-test\"],"
            + "\"outboundTag\":\"pxlnet-test\"},{\"type\":\"field\",\"network\":\"tcp,udp\","
            + "\"outboundTag\":\"pxlnet-reject\"}]}}";
    }

    private static String clientRealityConfig(int port) {
        return clientConfig(port).replace("\"security\":\"none\",",
            "\"security\":\"reality\",\"realitySettings\":{\"serverName\":\"localhost\","
                + "\"fingerprint\":\"chrome\",\"password\":\"5b5J3-FtVeyXc5vrQmI-6YTK8hoKRkRsJRD0s8YAlng\","
                + "\"shortId\":\"\"},");
    }

    private static int availablePort() throws Exception {
        try (ServerSocket candidate = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return candidate.getLocalPort();
        }
    }

    private static int xrayProcessPid(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null || manager.getRunningAppProcesses() == null) return -1;
        String expected = context.getPackageName() + ":pxlxray";
        for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
            if (expected.equals(process.processName)) return process.pid;
        }
        return -1;
    }

    private static String config(int port) {
        return "{\"inbounds\":[{\"listen\":\"127.0.0.1\",\"port\":" + port
            + ",\"protocol\":\"socks\",\"settings\":{\"auth\":\"noauth\",\"udp\":false}}],"
            + "\"outbounds\":[{\"protocol\":\"blackhole\"}]}";
    }

    private static boolean canConnect(int port) {
        return canConnect("127.0.0.1", port, 500);
    }

    private static boolean canConnect(String host, int port) {
        return canConnect(host, port, 3000);
    }

    private static boolean canConnect(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
