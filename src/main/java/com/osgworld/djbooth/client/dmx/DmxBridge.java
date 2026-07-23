package com.osgworld.djbooth.client.dmx;

import com.osgworld.djbooth.DJBooth;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Soft, one-way bridge to MineDMX. MineDMX's client listens on UDP {@value #PORT}
 * (the ArtNet port) for a JSON control frame shaped like
 * {@code {"fixtures":[{"id":..,"pan":..,"tilt":..,"r":..,"g":..,"b":..,"intensity":..}]}}
 * and drives its light fixtures from it (see UDPClientReceiver / processJsonPayload).
 *
 * <p>We never link against MineDMX classes: if MineDMX is absent the datagram just goes
 * nowhere. This keeps the dependency fully optional — DJ Booth works with or without it,
 * and cannot break MineDMX (we only send, never touch its state).
 *
 * <p>This is the raw transport for Plan 04. Mapping deck beat/energy to fixture values is
 * layered on top later; nothing calls this yet, so it is dormant by default.
 */
public final class DmxBridge {
    private DmxBridge() {}

    /** MineDMX UDP listen port (ArtNet 0x1936). */
    public static final int PORT = 6454;

    private static volatile boolean enabled = false;
    private static DatagramSocket socket;
    private static InetAddress loopback;

    /** One light fixture's target state, all channels 0..255. */
    public record Fixture(int id, int pan, int tilt, int r, int g, int b, int intensity) {}

    public static boolean isEnabled() {
        return enabled;
    }

    /** Turn the bridge on/off. Opens the socket lazily on first enable. */
    public static synchronized void setEnabled(boolean on) {
        enabled = on;
        if (on && socket == null) {
            try {
                socket = new DatagramSocket();
                loopback = InetAddress.getByName("127.0.0.1");
            } catch (Exception e) {
                DJBooth.LOGGER.warn("DMX bridge: could not open UDP socket, disabling", e);
                enabled = false;
            }
        }
    }

    /** Send one control frame to MineDMX. No-op when disabled or on send failure. */
    public static void send(List<Fixture> fixtures) {
        if (!enabled || socket == null || fixtures.isEmpty()) {
            return;
        }
        byte[] data = buildJson(fixtures).getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(data, data.length, loopback, PORT));
        } catch (Exception e) {
            // Transient; MineDMX may not be listening. Stay quiet to avoid log spam.
        }
    }

    private static String buildJson(List<Fixture> fixtures) {
        StringBuilder sb = new StringBuilder(64 + fixtures.size() * 96);
        sb.append("{\"fixtures\":[");
        for (int i = 0; i < fixtures.size(); i++) {
            Fixture f = fixtures.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(f.id())
              .append(",\"pan\":").append(f.pan())
              .append(",\"tilt\":").append(f.tilt())
              .append(",\"r\":").append(f.r())
              .append(",\"g\":").append(f.g())
              .append(",\"b\":").append(f.b())
              .append(",\"intensity\":").append(f.intensity())
              .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }
}
