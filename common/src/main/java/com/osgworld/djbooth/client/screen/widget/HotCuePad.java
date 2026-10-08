package com.osgworld.djbooth.client.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * One of the hot-cue pads under a deck.
 *
 * <p>A plain button with a bar of the cue's colour along its foot while the cue is set. The pad does
 * two different things depending on that - it sets the cue when empty and jumps to it when set - so
 * it has to say which. The colour is the one the cue is marked with on the deck's overview strip.
 */
public class HotCuePad extends Button {
    private final BooleanSupplier isSet;
    private final int colour;

    public HotCuePad(int x, int y, int w, int h, Component label, Component tip, int colour,
                     BooleanSupplier isSet, Runnable action) {
        super(x, y, w, h, label, b -> action.run(), DEFAULT_NARRATION);
        this.colour = colour;
        this.isSet = isSet;
        setTooltip(Tooltip.create(tip));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick);
        if (isSet.getAsBoolean()) {
            int x = getX(), y = getY();
            g.fill(x + 2, y + height - 4, x + width - 2, y + height - 2, 0xFF000000 | (colour & 0xFFFFFF));
        }
    }
}
