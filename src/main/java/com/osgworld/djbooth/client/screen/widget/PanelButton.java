package com.osgworld.djbooth.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** A flat panel button. Lights up when its {@code lit} condition is true (e.g. deck playing). */
public class PanelButton extends AbstractWidget {
    private final Runnable onPress;
    private final BooleanSupplier lit;
    private final int accent;

    public PanelButton(int x, int y, int w, int h, Component label, int accent,
                       Runnable onPress, BooleanSupplier lit) {
        super(x, y, w, h, label);
        this.onPress = onPress;
        this.lit = lit;
        this.accent = accent;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Invisible hotspot over the real artwork: only draw feedback when lit or hovered.
        int x = getX(), y = getY();
        boolean on = lit.getAsBoolean();
        if (on) {
            // Coloured glow (accent with alpha) + bright ring.
            int glow = (accent & 0x00FFFFFF) | 0x66000000;
            g.fill(x, y, x + width, y + height, glow);
            g.renderOutline(x, y, width, height, 0xFFFFFFFF);
        } else if (isHovered()) {
            g.fill(x, y, x + width, y + height, 0x33FFFFFF);
            g.renderOutline(x, y, width, height, 0x88FFFFFF);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
