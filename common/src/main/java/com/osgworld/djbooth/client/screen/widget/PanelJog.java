package com.osgworld.djbooth.client.screen.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/**
 * A jog wheel. Dragging around its centre reports the angular delta in degrees
 * through {@code onDegrees} — the screen turns that into a scrub/nudge.
 */
public class PanelJog extends AbstractWidget {
    private final DoubleConsumer onDegrees;
    private double lastAngle;
    private double visualAngle;

    public PanelJog(int x, int y, int w, int h, DoubleConsumer onDegrees) {
        super(x, y, w, h, Component.empty());
        this.onDegrees = onDegrees;
    }

    private double angleAt(double mx, double my) {
        return PanelMath.angleAt(getX(), getY(), width, height, mx, my);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        lastAngle = angleAt(mouseX, mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        double a = angleAt(mouseX, mouseY);
        double d = PanelMath.angleDelta(lastAngle, a);
        lastAngle = a;
        visualAngle += d;
        if (Math.abs(d) > 0.01) {
            onDegrees.accept(d);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int size = Math.min(width, height);
        int cx = getX() + width / 2;
        int cy = getY() + height / 2;
        int r = size / 2;
        // The real jog art is under us; draw only a rotating marker so motion reads,
        // plus a faint ring on hover.
        if (isHovered()) {
            g.renderOutline(cx - r, cy - r, r * 2, r * 2, 0x66FFFFFF);
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) visualAngle));
        // Marker from centre outward.
        g.fill(-2, -r + 4, 2, -r / 3, 0xCC00E0FF);
        pose.popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
