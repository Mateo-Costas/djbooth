package com.osgworld.djbooth.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * A draggable fader. Value is 0..1. Reads its live value from {@code getter} so it stays
 * in sync with server state, and reports changes through {@code setter}. Double-click or
 * right-click parks it back at its default position.
 */
public class PanelFader extends AbstractWidget {
    private static final long DOUBLE_CLICK_MS = 300;
    private static final double WHEEL_STEP = 0.04; // one wheel notch
    private static final double FINE = 0.25;       // shift multiplier

    private final boolean vertical;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final double defaultValue;

    private long lastClickMs;

    public PanelFader(int x, int y, int w, int h, boolean vertical,
                      DoubleSupplier getter, DoubleConsumer setter) {
        this(x, y, w, h, vertical, 0.5, getter, setter);
    }

    public PanelFader(int x, int y, int w, int h, boolean vertical, double defaultValue,
                      DoubleSupplier getter, DoubleConsumer setter) {
        super(x, y, w, h, Component.empty());
        this.vertical = vertical;
        this.defaultValue = Math.max(0.0, Math.min(1.0, defaultValue));
        this.getter = getter;
        this.setter = setter;
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private void setFromMouse(double mx, double my) {
        double v = vertical
                ? (getY() + height - my) / height
                : (mx - getX()) / width;
        setter.accept(clamp01(v));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && isMouseOver(mouseX, mouseY)) {
            setter.accept(defaultValue);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        long now = net.minecraft.Util.getMillis();
        if (now - lastClickMs <= DOUBLE_CLICK_MS) {
            setter.accept(defaultValue);
            lastClickMs = 0;
            return;
        }
        lastClickMs = now;
        setFromMouse(mouseX, mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        setFromMouse(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        double step = WHEEL_STEP
                * (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? FINE : 1.0);
        setter.accept(clamp01(getter.getAsDouble() + scrollY * step));
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        double v = clamp01(getter.getAsDouble());
        // Track.
        if (vertical) {
            int cx = x + width / 2;
            g.fill(cx - 1, y, cx + 1, y + height, 0xFF0A0A0E);
            int knobY = y + (int) ((1.0 - v) * (height - 6));
            int col = isHovered() ? 0xFFFFFFFF : 0xFFCCCCD4;
            g.fill(x, knobY, x + width, knobY + 6, col);
            g.renderOutline(x, knobY, width, 6, 0xFF000000);
        } else {
            int cy = y + height / 2;
            g.fill(x, cy - 1, x + width, cy + 1, 0xFF0A0A0E);
            int knobX = x + (int) (v * (width - 6));
            int col = isHovered() ? 0xFFFFFFFF : 0xFFCCCCD4;
            g.fill(knobX, y, knobX + 6, y + height, col);
            g.renderOutline(knobX, y, 6, height, 0xFF000000);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
