package com.osgworld.djbooth.client.dmx;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone DMX smoke test. {@code /djbooth dmxtest} enables {@link DmxBridge} and, for a
 * few seconds, sweeps a rainbow across fixture ids 1..8. Its only job is to prove the UDP
 * path actually reaches MineDMX before we invest in real beat→light mapping (Plan 04).
 *
 * <p>Registered on the game event bus from client setup (see DJBoothClient).
 */
public final class DmxDemo {
    private DmxDemo() {}

    private static final int FIXTURES = 8;
    private static final int DURATION_TICKS = 120; // ~6 s at 20 tps
    private static int ticksLeft = 0;
    private static int phase = 0;

    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("djbooth")
                .then(Commands.literal("dmxtest").executes(ctx -> {
                    DmxBridge.setEnabled(true);
                    ticksLeft = DURATION_TICKS;
                    phase = 0;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "DJ Booth: sending DMX test to fixtures 1-" + FIXTURES
                                    + " on udp/" + DmxBridge.PORT + " for ~6s"), false);
                    return 1;
                })));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (ticksLeft <= 0) {
            return;
        }
        ticksLeft--;
        phase++;

        List<DmxBridge.Fixture> frame = new ArrayList<>(FIXTURES);
        for (int i = 0; i < FIXTURES; i++) {
            // Hue marches along the fixtures and advances each tick.
            float hue = ((phase * 4 + i * (360 / FIXTURES)) % 360) / 360.0f;
            int[] rgb = hsvToRgb(hue);
            frame.add(new DmxBridge.Fixture(
                    i + 1,          // id (MineDMX fixtures numbered from 1)
                    128, 128,       // pan/tilt centered
                    rgb[0], rgb[1], rgb[2],
                    255));          // full intensity
        }
        DmxBridge.send(frame);

        if (ticksLeft == 0) {
            DmxBridge.setEnabled(false);
        }
    }

    /** Minimal HSV(full S/V) -> RGB 0..255. */
    private static int[] hsvToRgb(float h) {
        float r = 0, g = 0, b = 0;
        int seg = (int) (h * 6) % 6;
        float f = h * 6 - (float) Math.floor(h * 6);
        float q = 1 - f;
        switch (seg) {
            case 0 -> { r = 1; g = f; }
            case 1 -> { r = q; g = 1; }
            case 2 -> { g = 1; b = f; }
            case 3 -> { g = q; b = 1; }
            case 4 -> { r = f; b = 1; }
            default -> { r = 1; b = q; }
        }
        return new int[]{Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)};
    }
}
