package com.osgworld.djbooth.client.screen;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.menu.CdjMenu;
import com.osgworld.djbooth.net.JogNudgePayload;
import com.osgworld.djbooth.net.TransportPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** GUI for a CDJ deck: transport buttons, tempo slider, live position readout. */
public class CdjScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<CdjMenu> {

    public CdjScreen(CdjMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 200;
        this.imageHeight = 120;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos + 10;
        int y = this.topPos + 20;

        addRenderableWidget(Button.builder(Component.literal("Play"),
                b -> send(TransportPayload.PLAY)).bounds(x, y, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Pause"),
                b -> send(TransportPayload.PAUSE)).bounds(x + 45, y, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cue"),
                b -> send(TransportPayload.CUE)).bounds(x + 90, y, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Set Cue"),
                b -> send(TransportPayload.SET_CUE)).bounds(x, y + 25, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Loop"),
                b -> send(TransportPayload.LOOP_TOGGLE)).bounds(x + 65, y + 25, 60, 20).build());

        addRenderableWidget(new TempoSlider(x, y + 55, 180, 20, 1.0));
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new TransportPayload(menu.pos(), action));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xF01A1A22);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        CdjBlockEntity be = menu.blockEntity();
        String readout = "--:--";
        if (be != null && this.minecraft != null && this.minecraft.level != null) {
            long now = this.minecraft.level.getGameTime() * 50L;
            long ms = be.state().positionMsAt(now);
            readout = String.format("%02d:%02d.%01d", ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100);
        }
        g.drawString(this.font, "Pos " + readout, this.leftPos + 100, this.topPos + 6, 0xFFFFFF, false);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 8, 6, 0xFFFFFF, false);
    }

    /** Tempo slider mapping 0..1 to rate 0.5..1.5; sends a JogNudge (rate only) on change. */
    private class TempoSlider extends AbstractSliderButton {
        TempoSlider(int x, int y, int w, int h, double rate) {
            super(x, y, w, h, Component.literal("Tempo"), (rate - 0.5) / 1.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format("Tempo %.2fx", 0.5 + this.value)));
        }

        @Override
        protected void applyValue() {
            double rate = 0.5 + this.value;
            PacketDistributor.sendToServer(new JogNudgePayload(menu.pos(), rate, -1L));
        }
    }
}
