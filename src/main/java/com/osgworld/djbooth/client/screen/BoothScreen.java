package com.osgworld.djbooth.client.screen;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.blockentity.MixerBlockEntity;
import com.osgworld.djbooth.client.screen.widget.PanelButton;
import com.osgworld.djbooth.client.screen.widget.PanelFader;
import com.osgworld.djbooth.client.screen.widget.PanelJog;
import com.osgworld.djbooth.client.audio.DeckAudioManager;
import com.osgworld.djbooth.deck.PlayState;
import com.osgworld.djbooth.menu.BoothMenu;
import com.osgworld.djbooth.net.JogNudgePayload;
import com.osgworld.djbooth.net.MixerPayload;
import com.osgworld.djbooth.net.TransportPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** The combined booth GUI: two decks flanking a mixer, drawn over a panel texture. */
public class BoothScreen extends AbstractContainerScreen<BoothMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "textures/gui/booth.png");
    private static final int TEX_W = 1200;
    private static final int TEX_H = 440;
    private static final double MS_PER_DEG = 8.0; // jog sensitivity: full turn ≈ 2.9 s scrub
    private static final double JOG_BEND_PER_DEG = 0.05; // jog pitch-bend strength while playing

    /** URL input boxes, so Enter can load the right deck. */
    private final java.util.Map<EditBox, BlockPos> urlBoxes = new java.util.HashMap<>();

    public BoothScreen(BoothMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        // Scale the panel to the window while keeping the texture aspect ratio.
        float aspect = (float) TEX_W / TEX_H;
        int w = Math.min(this.width - 20, 1040);
        int h = Math.round(w / aspect);
        if (h > this.height - 40) {
            h = this.height - 40;
            w = Math.round(h * aspect);
        }
        this.imageWidth = w;
        this.imageHeight = h;

        super.init();
        urlBoxes.clear();

        if (menu.refs().deckA() != null) {
            addDeck(menu.refs().deckA(), BoothLayout.REGION_DECK_A);
        }
        if (menu.refs().deckB() != null) {
            addDeck(menu.refs().deckB(), BoothLayout.REGION_DECK_B);
        }
        if (menu.refs().mixer() != null) {
            addMixer();
        }
    }

    // --- Region/control geometry ---

    private int[] px(BoothLayout.Rect region, BoothLayout.Rect ctrl) {
        float rx = leftPos + region.x() * imageWidth;
        float ry = topPos + region.y() * imageHeight;
        float rw = region.w() * imageWidth;
        float rh = region.h() * imageHeight;
        int x = Math.round(rx + ctrl.x() * rw);
        int y = Math.round(ry + ctrl.y() * rh);
        int cw = Math.round(ctrl.w() * rw);
        int ch = Math.round(ctrl.h() * rh);
        return new int[]{x, y, cw, ch};
    }

    // --- Deck ---

    private void addDeck(BlockPos pos, BoothLayout.Rect region) {
        // PLAY / PAUSE toggle (green): one button like the real CDJ.
        int[] play = px(region, BoothLayout.DECK_PLAY);
        PanelButton playBtn = new PanelButton(play[0], play[1], play[2], play[3],
                Component.translatable("gui.djbooth.play"), 0xFF1DB954,
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    if (be == null) return;
                    int action = be.state().getPlayState() == PlayState.PLAY
                            ? TransportPayload.PAUSE : TransportPayload.PLAY;
                    PacketDistributor.sendToServer(new TransportPayload(pos, action));
                },
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && be.state().getPlayState() == PlayState.PLAY;
                });
        playBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.djbooth.play")));
        addRenderableWidget(playBtn);

        // CUE (orange): jump to cue while playing, otherwise set cue here.
        int[] cue = px(region, BoothLayout.DECK_CUE);
        PanelButton cueBtn = new PanelButton(cue[0], cue[1], cue[2], cue[3],
                Component.translatable("gui.djbooth.cue"), 0xFFF2A900,
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    if (be == null) return;
                    int action = be.state().getPlayState() == PlayState.PLAY
                            ? TransportPayload.CUE : TransportPayload.SET_CUE;
                    PacketDistributor.sendToServer(new TransportPayload(pos, action));
                },
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && be.state().getPlayState() == PlayState.CUE;
                });
        cueBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.djbooth.cue")));
        addRenderableWidget(cueBtn);

        // LOOP toggle.
        int[] loop = px(region, BoothLayout.DECK_LOOP);
        PanelButton loopBtn = new PanelButton(loop[0], loop[1], loop[2], loop[3],
                Component.translatable("gui.djbooth.loop"), 0xFFC03AA0,
                () -> PacketDistributor.sendToServer(
                        new TransportPayload(pos, TransportPayload.LOOP_TOGGLE)),
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && be.state().isLoopOn();
                });
        loopBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.djbooth.loop")));
        addRenderableWidget(loopBtn);

        // Tempo fader (vertical): 0..1 -> rate 0.5..1.5.
        int[] t = px(region, BoothLayout.DECK_TEMPO);
        PanelFader tempo = new PanelFader(t[0], t[1], t[2], t[3], true,
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    double rate = be != null ? be.state().getRate() : 1.0;
                    return rate - 0.5;
                },
                v -> PacketDistributor.sendToServer(
                        new JogNudgePayload(pos, 0.5 + v, -1L)));
        tempo.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.djbooth.tempo")));
        addRenderableWidget(tempo);

        // Jog wheel. While playing it bends the pitch like a real CDJ jog (smooth, client-local,
        // no seek). While parked it scrubs the position by seeking on the server.
        int[] j = px(region, BoothLayout.DECK_JOG);
        addRenderableWidget(new PanelJog(j[0], j[1], j[2], j[3], deg -> {
            CdjBlockEntity be = menu.deck(pos);
            if (be == null || minecraft == null || minecraft.level == null) {
                return;
            }
            if (be.state().getPlayState() == PlayState.PLAY) {
                // deg is the per-drag angle; turn it into a momentary speed multiplier.
                DeckAudioManager.nudgeBend(pos, 1.0 + deg * JOG_BEND_PER_DEG);
            } else {
                long now = minecraft.level.getGameTime() * 50L;
                long cur = be.state().positionMsAt(now);
                long target = Math.max(0, cur + Math.round(deg * MS_PER_DEG));
                PacketDistributor.sendToServer(
                        new JogNudgePayload(pos, be.state().getRate(), target));
            }
        }));

        // Track URL input over the CDJ display: paste a link, press Enter to load it.
        int[] u = px(region, BoothLayout.DECK_URLBAR);
        EditBox urlBox = new EditBox(this.font, u[0], u[1], u[2], u[3], Component.literal("URL"));
        urlBox.setMaxLength(1024);
        urlBox.setHint(Component.translatable("gui.djbooth.url_hint"));
        CdjBlockEntity deckBe = menu.deck(pos);
        if (deckBe != null) {
            urlBox.setValue(deckBe.state().getTrackUrl());
        }
        addRenderableWidget(urlBox);
        urlBoxes.put(urlBox, pos);
    }

    private void loadTrack(BlockPos pos, String url) {
        PacketDistributor.sendToServer(new com.osgworld.djbooth.net.LoadTrackPayload(pos, url.trim()));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (this.getFocused() instanceof EditBox eb && urlBoxes.containsKey(eb)) {
            // Enter loads the track; otherwise let the box handle it and swallow the key so the
            // inventory hotkey ('e') can't close the screen mid-typing.
            if (key == 257 || key == 335) {
                loadTrack(urlBoxes.get(eb), eb.getValue());
                return true;
            }
            if (eb.keyPressed(key, scan, mods) || eb.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    // --- Mixer ---

    private void addMixer() {
        addMixerFader(BoothLayout.MIX_FADER_A, true, MixerPayload.FADER_A,
                m -> m.getFaderA(), Component.translatable("gui.djbooth.fader_a"));
        addMixerFader(BoothLayout.MIX_FADER_B, true, MixerPayload.FADER_B,
                m -> m.getFaderB(), Component.translatable("gui.djbooth.fader_b"));
        addMixerFader(BoothLayout.MIX_MASTER, true, MixerPayload.MASTER,
                m -> m.getMaster(), Component.translatable("gui.djbooth.master"));
        addMixerFader(BoothLayout.MIX_XFADER, false, MixerPayload.CROSSFADER,
                m -> m.getCrossfader(), Component.translatable("gui.djbooth.crossfader"));
    }

    private void addMixerFader(BoothLayout.Rect ctrl, boolean vertical, int channel,
                               java.util.function.Function<MixerBlockEntity, Float> getter,
                               Component label) {
        BlockPos mix = menu.refs().mixer();
        int[] f = px(BoothLayout.REGION_MIXER, ctrl);
        PanelFader fader = new PanelFader(f[0], f[1], f[2], f[3], vertical,
                () -> {
                    MixerBlockEntity be = menu.mixer();
                    return be != null ? getter.apply(be) : 0.0;
                },
                v -> PacketDistributor.sendToServer(
                        new MixerPayload(mix, channel, (float) v)));
        fader.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        fader.setMessage(label);
        addRenderableWidget(fader);
    }

    // --- Rendering ---

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Container screens swallow drags for slot handling; forward them to our widgets
        // so faders and the jog wheel respond to click-and-drag.
        if (this.getFocused() != null && button == 0) {
            return this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, imageWidth, imageHeight, 0f, 0f, TEX_W, TEX_H, TEX_W, TEX_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawDeckReadout(g, menu.refs().deckA(), BoothLayout.REGION_DECK_A);
        drawDeckReadout(g, menu.refs().deckB(), BoothLayout.REGION_DECK_B);
        renderTooltip(g, mouseX, mouseY);
    }

    private void drawDeckReadout(GuiGraphics g, BlockPos pos, BoothLayout.Rect region) {
        if (pos == null || minecraft == null || minecraft.level == null) {
            return;
        }
        CdjBlockEntity be = menu.deck(pos);
        if (be == null) {
            return;
        }
        long now = minecraft.level.getGameTime() * 50L;
        long ms = be.state().positionMsAt(now);
        String readout = String.format("%02d:%02d.%01d", ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100);
        int[] scr = px(region, BoothLayout.DECK_SCREEN);
        int cx = scr[0] + scr[2] / 2;
        int y = scr[1] + scr[3] / 2 - this.font.lineHeight / 2;
        g.drawCenteredString(this.font, readout, cx, y, 0xFF00E0A0);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Only the title; no inventory label (this menu has no slots).
        g.drawString(this.font, this.title, 6, -10, 0xFFFFFFFF, false);
    }
}
