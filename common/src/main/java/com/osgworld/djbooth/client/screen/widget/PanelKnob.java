package com.osgworld.djbooth.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * A rotary knob. Value is 0..1 (0.5 = 12 o'clock).
 *
 * <p>Three ways to turn it, so nobody has to guess: drag up/down, roll the mouse wheel, or hold
 * shift with either for fine steps. Double-click or right-click parks it at this knob's default.
 * While the pointer is over it (or it is being dragged) the knob shows what it is actually set to,
 * in the units the hardware prints — "+3.0 dB", "1.2 kHz" — rather than a bare 0..1 number.
 *
 * <p>Reads its live value from {@code getter} so it tracks server state, and reports turns through
 * {@code setter}.
 */
public class PanelKnob extends AbstractWidget {
    private static final double SWEEP_DEG = 135.0; // +/- travel from centre
    private static final double DRAG_SENS = 150.0; // pixels of drag for a full 0..1 turn
    private static final double WHEEL_STEP = 0.04; // one wheel notch
    private static final double FINE = 0.25;       // shift multiplier for both drag and wheel
    private static final long DOUBLE_CLICK_MS = 300;

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final String tag;       // short caption (HI / MID / LOW / COLOR)
    private final boolean labelLeft; // draw the caption on the left (deck A) vs right (deck B)
    private final double defaultValue; // where a double-click / right-click parks the knob
    private final java.util.function.DoubleFunction<String> format; // value readout, may be null

    private long lastClickMs;

    public PanelKnob(int x, int y, int w, int h, String tag, boolean labelLeft,
                     DoubleSupplier getter, DoubleConsumer setter) {
        this(x, y, w, h, tag, labelLeft, 0.5, null, getter, setter);
    }

    public PanelKnob(int x, int y, int w, int h, String tag, boolean labelLeft, double defaultValue,
                     java.util.function.DoubleFunction<String> format,
                     DoubleSupplier getter, DoubleConsumer setter) {
        super(x, y, w, h, Component.empty());
        this.tag = tag;
        this.labelLeft = labelLeft;
        this.defaultValue = clamp01(defaultValue);
        this.format = format;
        this.getter = getter;
        this.setter = setter;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static boolean fineHeld() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && isMouseOver(mouseX, mouseY)) {
            setter.accept(defaultValue); // right-click resets
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // Turning happens on drag or wheel; a bare click leaves the value alone, but a
        // double-click resets, which is the gesture people try first on a mixer knob.
        long now = net.minecraft.Util.getMillis();
        if (now - lastClickMs <= DOUBLE_CLICK_MS) {
            setter.accept(defaultValue);
            lastClickMs = 0;
        } else {
            lastClickMs = now;
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        double sens = DRAG_SENS / (fineHeld() ? FINE : 1.0);
        setter.accept(clamp01(getter.getAsDouble() - dragY / sens));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        double step = WHEEL_STEP * (fineHeld() ? FINE : 1.0);
        setter.accept(clamp01(getter.getAsDouble() + scrollY * step));
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        int cx = x + width / 2, cy = y + height / 2;
        int r = Math.min(width, height) / 2;
        double v = clamp01(getter.getAsDouble());
        boolean active = isHovered() || isFocused();

        // Body.
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF23232B);
        g.renderOutline(cx - r, cy - r, 2 * r, 2 * r, active ? 0xFFFFFFFF : 0xFF5A5A68);

        // Arc from the default position to the current one, so how far the knob is turned reads
        // at a glance instead of having to compare pointer angles.
        drawArc(g, cx, cy, r, defaultValue, v);

        // Pointer, 0 = up, full left/right = +/-135 deg.
        double ang = Math.toRadians((v - 0.5) * 2 * SWEEP_DEG);
        int px = cx + (int) Math.round(Math.sin(ang) * (r - 1));
        int py = cy - (int) Math.round(Math.cos(ang) * (r - 1));
        boolean atDefault = Math.abs(v - defaultValue) < 0.02;
        int col = atDefault ? 0xFF25E0C0 : 0xFFF2A900; // green when parked, amber when turned
        g.fill(px - 1, py - 1, px + 1, py + 1, col);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF8A8A96);

        // Caption beside the knob (left for deck A, right for deck B) so columns don't collide.
        // Hovering swaps it for the live value in hardware units.
        String caption = tag;
        if (active && format != null) {
            caption = format.apply(v);
        }
        if (caption != null && !caption.isEmpty()) {
            drawCaption(g, caption, cx, cy, r, active);
        }
    }

    /** Fill the ring between two knob positions, clockwise or anticlockwise as needed. */
    private void drawArc(GuiGraphics g, int cx, int cy, int r, double from, double to) {
        if (Math.abs(to - from) < 0.01) {
            return;
        }
        double lo = Math.min(from, to), hi = Math.max(from, to);
        int steps = Math.max(4, (int) ((hi - lo) * 48));
        for (int i = 0; i <= steps; i++) {
            double t = lo + (hi - lo) * i / steps;
            double a = Math.toRadians((t - 0.5) * 2 * SWEEP_DEG);
            int ax = cx + (int) Math.round(Math.sin(a) * (r + 1));
            int ay = cy - (int) Math.round(Math.cos(a) * (r + 1));
            g.fill(ax, ay, ax + 1, ay + 1, 0xFFF2A900);
        }
    }

    /** Tiny text on a solid pill, so it reads over the busy mixer artwork. */
    private void drawCaption(GuiGraphics g, String text, int cx, int cy, int r, boolean active) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float s = 0.55f; // shrink the font so labels stay tiny
        int color = active ? 0xFFFFFFFF : 0xFFFF5040; // bright red, readable on dark art
        float tw = font.width(text) * s;
        float th = font.lineHeight * s;
        float screenX = labelLeft ? cx - r - 2 - tw : cx + r + 2;
        float screenY = cy - th / 2f;
        g.fill(Math.round(screenX - 1), Math.round(screenY - 1),
                Math.round(screenX + tw + 1), Math.round(screenY + th), 0xE6000000);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, Math.round(screenX / s), Math.round(screenY / s), color, false);
        g.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
