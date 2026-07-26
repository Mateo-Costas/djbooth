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
    private Runnable onSecondary; // right-click action (optional)
    private boolean caption;      // draw the label over the hotspot (for art too small to read)
    private java.util.function.Supplier<String> captionText; // live caption, for switches

    public PanelButton(int x, int y, int w, int h, Component label, int accent,
                       Runnable onPress, BooleanSupplier lit) {
        super(x, y, w, h, label);
        this.onPress = onPress;
        this.lit = lit;
        this.accent = accent;
    }

    /** Attach a right-click action (e.g. CUE: set the cue point). */
    public PanelButton withSecondary(Runnable action) {
        this.onSecondary = action;
        return this;
    }

    /** Draw the label on top of the hotspot. Use where the panel art prints it too small to read
     *  at GUI scale, so the control is still identifiable without hovering for a tooltip. */
    public PanelButton withCaption() {
        this.caption = true;
        return this;
    }

    /** Like {@link #withCaption()}, but the text is read fresh each frame — for switches whose
     *  label is their current position (FWD / REV / SLIP REV). */
    public PanelButton withCaption(java.util.function.Supplier<String> text) {
        this.caption = true;
        this.captionText = text;
        return this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible && this.isMouseOver(mouseX, mouseY)) {
            if (button == 1 && onSecondary != null) {
                onSecondary.run();
                return true;
            }
            if (button == 0) {
                onPress.run();
                return true;
            }
        }
        return false;
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
        if (caption) {
            drawCaption(g, on);
        }
    }

    /** Centre the label inside the hotspot, shrunk to fit and backed by a pill so it reads. */
    private void drawCaption(GuiGraphics g, boolean on) {
        String text = captionText != null ? captionText.get() : getMessage().getString();
        if (text.isEmpty()) {
            return;
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float s = Math.min(0.7f, (width - 2f) / Math.max(1, font.width(text)));
        float tw = font.width(text) * s;
        float th = font.lineHeight * s;
        float tx = getX() + (width - tw) / 2f;
        float ty = getY() + (height - th) / 2f;
        g.fill(Math.round(tx - 1), Math.round(ty - 1),
                Math.round(tx + tw + 1), Math.round(ty + th), 0xCC000000);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, Math.round(tx / s), Math.round(ty / s),
                on ? 0xFFFFFFFF : 0xFFB8B8C4, false);
        g.pose().popPose();
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
