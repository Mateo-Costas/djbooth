package com.osgworld.djbooth.client.screen.widget;

import com.osgworld.djbooth.mixer.MixLevels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A small multi-position switch on the mixer: it shows where it is set and steps on when clicked
 * (left click forward, right click back).
 *
 * <p>It replaces vanilla buttons that were used for these. A vanilla button draws its label at full
 * size, and the switches on the panel are about thirteen pixels wide, so "ISO", "LIN" and "SHARP"
 * came out clipped to "IS", "LI" and "Ol". This one fits a text label into whatever room it has,
 * or draws a small picture of the curve it selects, which needs no room for words at all. It reads
 * its position every frame, so it follows another player's change instead of showing a label that
 * was set when the screen opened.
 */
public class PanelSwitch extends AbstractWidget {

    /** How a switch draws its current position inside its rectangle. */
    @FunctionalInterface
    public interface Face {
        void draw(GuiGraphics g, int x, int y, int w, int h, int state, boolean hovered);
    }

    private final IntSupplier state;
    private final int positions;
    private final IntConsumer onSelect;
    private final String[] names;
    private final Face face;

    /**
     * @param names     one name per position, shown in a pill while the pointer is over the switch
     * @param face      how the current position is drawn
     * @param onSelect  called with the new position when the player clicks
     */
    public PanelSwitch(int x, int y, int w, int h, Component label, String[] names, Face face,
                       IntSupplier state, IntConsumer onSelect) {
        super(x, y, w, h, label);
        this.names = names;
        this.positions = names.length;
        this.face = face;
        this.state = state;
        this.onSelect = onSelect;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (button == 0 || button == 1) {
            int step = button == 0 ? 1 : positions - 1;
            onSelect.accept(Math.floorMod(state.getAsInt() + step, positions));
            return true;
        }
        return false;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onSelect.accept(Math.floorMod(state.getAsInt() + 1, positions));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY();
        int current = Math.floorMod(state.getAsInt(), positions);
        boolean hovered = isHovered();
        // A dark window over the printed cap, so the face reads whatever the art is doing there.
        g.fill(x, y, x + width, y + height, 0xB0000000);
        face.draw(g, x, y, width, height, current, hovered);
        if (hovered) {
            g.renderOutline(x, y, width, height, 0xCCFFFFFF);
            drawName(g, names[current]);
        }
    }

    /** The current position's name in a pill just above the switch, as the knobs show their value. */
    private void drawName(GuiGraphics g, String text) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float s = 0.6f;
        float tw = font.width(text) * s;
        float th = font.lineHeight * s;
        float tx = getX() + (width - tw) / 2f;
        float ty = getY() - th - 3f;
        g.fill(Math.round(tx - 2), Math.round(ty - 1), Math.round(tx + tw + 2), Math.round(ty + th + 1),
                0xE6000000);
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, Math.round(tx / s), Math.round(ty / s), 0xFFFFFFFF, false);
        g.pose().popPose();
    }

    // --- Faces ----------------------------------------------------------------------------------

    /** A text label shrunk to fit the switch, so a long word is small rather than clipped. */
    public static Face text(String[] labels) {
        return (g, x, y, w, h, state, hovered) -> {
            String text = labels[state];
            var font = net.minecraft.client.Minecraft.getInstance().font;
            float s = fitScale(font.width(text), font.lineHeight, w, h);
            float tw = font.width(text) * s;
            float th = font.lineHeight * s;
            float tx = x + (w - tw) / 2f;
            float ty = y + (h - th) / 2f;
            g.pose().pushPose();
            g.pose().scale(s, s, 1f);
            g.drawString(font, text, Math.round(tx / s), Math.round(ty / s), 0xFFE6E6EE, false);
            g.pose().popPose();
        };
    }

    /** The scale at which a text of this size fits a w x h box, never above 0.7. */
    static float fitScale(int textWidth, int lineHeight, int w, int h) {
        float byWidth = (w - 2f) / Math.max(1, textWidth);
        float byHeight = (h - 1f) / Math.max(1, lineHeight);
        return Math.max(0.1f, Math.min(0.7f, Math.min(byWidth, byHeight)));
    }

    /** A picture of the curve the switch selects, so it needs no room for words. */
    public static Face curve(CurveShape shape, int dim) {
        return (g, x, y, w, h, state, hovered) -> {
            int[][] points = curvePoints(shape, state, w, h, dim);
            for (int[] p : points) {
                g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, p[2] == 1 ? 0xFF35E070 : 0x66B8B8C4);
            }
        };
    }

    /** The shape of a curve for one switch position: values 0..1 over 0..1. */
    @FunctionalInterface
    public interface CurveShape {
        /** Gain of the rising side at {@code x}, for the switch's {@code state}. */
        float rising(float x, int state);
    }

    /** The channel fader's three curves. */
    public static CurveShape channelFader() {
        return MixLevels::curve;
    }

    /** The crossfader's three curves: the rising side is B's, and A's is its mirror image. */
    public static CurveShape crossfader() {
        return MixLevels::crossfaderSide;
    }

    /**
     * Pixel positions for a curve inside a w x h box, one dot per column.
     *
     * <p>Each entry is {@code {column, row, bright}}. The rising side is bright; when {@code dim} is
     * 2 the mirror image (the other side of the crossfader) is added, dimmer, which is what shows a
     * cut curve keeping both sides open across the middle. Kept apart from drawing so the geometry
     * can be tested: a curve that runs off the box would draw over the art around it.
     */
    static int[][] curvePoints(CurveShape shape, int state, int w, int h, int dim) {
        int cols = Math.max(2, w - 2);
        int rows = Math.max(2, h - 2);
        int per = dim == 2 ? 2 : 1;
        int[][] out = new int[cols * per][];
        for (int i = 0; i < cols; i++) {
            float x = i / (float) (cols - 1);
            int row = 1 + Math.round((1f - MixLevels.clamp01(shape.rising(x, state))) * (rows - 1));
            out[i] = new int[]{1 + i, row, 1};
            if (per == 2) {
                int mirror = 1 + Math.round((1f - MixLevels.clamp01(shape.rising(1f - x, state))) * (rows - 1));
                out[cols + i] = new int[]{1 + i, mirror, 0};
            }
        }
        return out;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
