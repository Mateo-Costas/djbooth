package com.osgworld.djbooth.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * A rotary knob. Value is 0..1 (0.5 = 12 o'clock). Drag up to turn clockwise, down to turn
 * anticlockwise, like grabbing a real EQ/filter knob. Right-click snaps back to centre. Reads its
 * live value from {@code getter} so it tracks server state, and reports turns through {@code setter}.
 */
public class PanelKnob extends AbstractWidget {
    private static final double SWEEP_DEG = 135.0; // +/- travel from centre
    private static final double DRAG_SENS = 150.0; // pixels of drag for a full 0..1 turn

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final String tag;       // short caption (HI / MID / LOW / FLT)
    private final boolean labelLeft; // draw the caption on the left (deck A) vs right (deck B)

    public PanelKnob(int x, int y, int w, int h, String tag, boolean labelLeft,
                     DoubleSupplier getter, DoubleConsumer setter) {
        super(x, y, w, h, Component.empty());
        this.tag = tag;
        this.labelLeft = labelLeft;
        this.getter = getter;
        this.setter = setter;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && isMouseOver(mouseX, mouseY)) {
            setter.accept(0.5); // right-click resets to flat/centre
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // Turning happens on drag; a bare click leaves the value alone.
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        setter.accept(clamp01(getter.getAsDouble() - dragY / DRAG_SENS));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        int cx = x + width / 2, cy = y + height / 2;
        int r = Math.min(width, height) / 2;
        double v = clamp01(getter.getAsDouble());

        // Body.
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF15151A);
        g.renderOutline(cx - r, cy - r, 2 * r, 2 * r, isHovered() ? 0xFFFFFFFF : 0xFF3A3A44);

        // Pointer, 0 = up, full left/right = +/-135 deg.
        double ang = Math.toRadians((v - 0.5) * 2 * SWEEP_DEG);
        int px = cx + (int) Math.round(Math.sin(ang) * (r - 1));
        int py = cy - (int) Math.round(Math.cos(ang) * (r - 1));
        int col = Math.abs(v - 0.5) < 0.02 ? 0xFF25E0C0 : 0xFFF2A900; // green at centre, amber when turned
        g.fill(px - 1, py - 1, px + 1, py + 1, col);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF60606A);

        // Tiny caption beside the knob (left for deck A, right for deck B) so columns don't collide.
        if (tag != null && !tag.isEmpty()) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            float s = 0.55f; // shrink the font so labels stay tiny
            int color = isHovered() ? 0xFFFFFFFF : 0xFFFF5040; // bright red, readable on dark art
            float tw = font.width(tag) * s;
            float th = font.lineHeight * s;
            float screenX = labelLeft ? cx - r - 2 - tw : cx + r + 2;
            float screenY = cy - th / 2f;
            // Solid dark pill behind the text so it reads over the busy mixer artwork.
            g.fill(Math.round(screenX - 1), Math.round(screenY - 1),
                    Math.round(screenX + tw + 1), Math.round(screenY + th), 0xE6000000);
            g.pose().pushPose();
            g.pose().scale(s, s, 1f);
            g.drawString(font, tag, Math.round(screenX / s), Math.round(screenY / s), color, false);
            g.pose().popPose();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
