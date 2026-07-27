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
import com.osgworld.djbooth.net.HotCuePayload;
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
import dev.architectury.networking.NetworkManager;

/** The combined booth GUI: two decks flanking a mixer, drawn over a panel texture. */
public class BoothScreen extends AbstractContainerScreen<BoothMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "textures/gui/booth.png");
    private static final int TEX_W = 1200;
    private static final int TEX_H = 440;
    private static final double MS_PER_DEG = 8.0; // jog sensitivity: full turn ≈ 2.9 s scrub
    private static final double JOG_BEND_PER_DEG = 0.05; // jog pitch-bend strength while playing
    private static final long JOG_SEND_MS = 60; // min gap between jog scrub packets

    private static final int BOTTOM_STRIP = 108; // reserved height below panel: search + perf pads + guide

    /** URL input boxes, so Enter can load the right deck. */
    private final java.util.Map<EditBox, BlockPos> urlBoxes = new java.util.HashMap<>();

    /** Recently loaded tracks (query text or URL), newest first, shared across the session. */
    private static final java.util.List<String> RECENTS = new java.util.ArrayList<>();
    private static final int MAX_RECENTS = 6;

    // Tempo range per deck (client-only feel, like the CDJ TEMPO RANGE button): ±6/±10/±16/WIDE.
    private static final double[] TEMPO_RANGES = {0.06, 0.10, 0.16, 0.60};
    private static final String[] TEMPO_RANGE_NAMES = {"±6", "±10", "±16", "WIDE"};
    private final java.util.Map<BlockPos, Integer> tempoRangeIdx = new java.util.HashMap<>();

    // Tap-tempo per deck: recent tap times -> a measured BPM shown on the deck readout.
    private static final long TAP_RESET_MS = 2000; // gap that starts a fresh count
    private final java.util.Map<BlockPos, java.util.ArrayDeque<Long>> taps = new java.util.HashMap<>();
    private final java.util.Map<BlockPos, Double> bpm = new java.util.HashMap<>();

    // Continuous controls coalesce their packets. A knob drag fires an event per frame, and every
    // one of those reaches the server as a full mixer sync broadcast to everyone nearby, so at
    // 144 fps a single fader would out-pace the tick rate several times over. Values are held here
    // and flushed at most once a tick, with the last one always sent so nothing is left stale.
    private static final long CONTROL_SEND_MS = 50;
    private final java.util.Map<Integer, Float> pendingControl = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> controlLastSent = new java.util.HashMap<>();
    private final java.util.Map<BlockPos, Double> pendingTempo = new java.util.HashMap<>();
    private final java.util.Map<BlockPos, Long> tempoLastSent = new java.util.HashMap<>();

    // Jog scrub accumulator per deck: leftover degrees, and when we last sent a scrub packet.
    private final java.util.Map<BlockPos, Double> jogPending = new java.util.HashMap<>();
    private final java.util.Map<BlockPos, Long> jogLastSent = new java.util.HashMap<>();

    /**
     * Queue a value for a continuous mixer control, sending at most once a tick.
     *
     * <p>Discrete controls — buttons, switches — send straight away and don't come through here;
     * it's only the ones you drag that can flood.
     */
    private void sendControl(BlockPos mixer, int channel, float value) {
        long now = net.minecraft.Util.getMillis();
        long last = controlLastSent.getOrDefault(channel, 0L);
        if (now - last >= CONTROL_SEND_MS) {
            controlLastSent.put(channel, now);
            pendingControl.remove(channel);
            NetworkManager.sendToServer(new MixerPayload(mixer, channel, value));
        } else {
            pendingControl.put(channel, value);
        }
    }

    /** The tempo fader floods exactly like the mixer knobs, so it is coalesced per deck too. */
    private void sendTempo(BlockPos pos, double rate) {
        long now = net.minecraft.Util.getMillis();
        if (now - tempoLastSent.getOrDefault(pos, 0L) >= CONTROL_SEND_MS) {
            tempoLastSent.put(pos, now);
            pendingTempo.remove(pos);
            NetworkManager.sendToServer(new JogNudgePayload(pos, rate, -1L));
        } else {
            pendingTempo.put(pos, rate);
        }
    }

    /** Send whatever the throttle held back, so a control never ends up stale on the server. */
    private void flushControls() {
        long now = net.minecraft.Util.getMillis();
        var tempoIt = pendingTempo.entrySet().iterator();
        while (tempoIt.hasNext()) {
            var e = tempoIt.next();
            if (now - tempoLastSent.getOrDefault(e.getKey(), 0L) >= CONTROL_SEND_MS) {
                tempoLastSent.put(e.getKey(), now);
                NetworkManager.sendToServer(new JogNudgePayload(e.getKey(), e.getValue(), -1L));
                tempoIt.remove();
            }
        }
        if (pendingControl.isEmpty() || menu.refs().mixer() == null) {
            return;
        }
        BlockPos mixer = menu.refs().mixer();
        var it = pendingControl.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (now - controlLastSent.getOrDefault(e.getKey(), 0L) >= CONTROL_SEND_MS) {
                controlLastSent.put(e.getKey(), now);
                NetworkManager.sendToServer(new MixerPayload(mixer, e.getKey(), e.getValue()));
                it.remove();
            }
        }
    }

    private double tempoRangeFor(BlockPos pos) {
        return TEMPO_RANGES[tempoRangeIdx.getOrDefault(pos, 2)]; // default ±16
    }

    public BoothScreen(BoothMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        // Scale the panel to the window, keeping aspect and reserving a strip below the panel
        // for the search boxes + recent chips (so they never sit on top of the CDJ artwork).
        float aspect = (float) TEX_W / TEX_H;
        int w = Math.min(this.width - 20, 1040);
        int h = Math.round(w / aspect);
        if (h > this.height - 40 - BOTTOM_STRIP) {
            h = this.height - 40 - BOTTOM_STRIP;
            w = Math.round(h * aspect);
        }
        this.imageWidth = w;
        this.imageHeight = h;

        super.init();
        // Shift the panel up so the reserved strip fits below it on screen.
        this.topPos = Math.max(10, (this.height - BOTTOM_STRIP - imageHeight) / 2);
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
        addSearchBar();
    }

    /** URL box, hot-cue / loop / beat-jump rows per deck, and recent chips, below the panel. */
    private void addSearchBar() {
        int stripY = topPos + imageHeight + 4;
        int gap = 8;
        int half = (imageWidth - gap) / 2;
        int rightX = leftPos + half + gap;

        if (menu.refs().deckA() != null) {
            addUrlBox(menu.refs().deckA(), leftPos, stripY, half);
            addPerfRows(menu.refs().deckA(), leftPos, half, stripY + 18);
        }
        if (menu.refs().deckB() != null) {
            addUrlBox(menu.refs().deckB(), rightX, stripY, half);
            addPerfRows(menu.refs().deckB(), rightX, half, stripY + 18);
        }

        // Recent tracks: quick chips to reload the last songs onto the focused (or A) deck.
        int chipY = stripY + 18 + 3 * 15 + 2;
        int chipW = Math.min(150, (imageWidth - 5 * 4) / Math.max(1, RECENTS.size() + 1));
        int cx = leftPos;
        for (String recent : RECENTS) {
            String label = recent.length() > 22 ? recent.substring(0, 21) + "…" : recent;
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                            Component.literal(label), b -> submitTrack(targetDeck(), recent))
                    .bounds(cx, chipY, chipW, 13)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(recent)))
                    .build());
            cx += chipW + 4;
            if (cx + chipW > leftPos + imageWidth) {
                break;
            }
        }
    }

    /** Two rows of performance controls for a deck: hot cues + beat jump, then the loop tools. */
    private void addPerfRows(BlockPos pos, int x0, int width, int y) {
        // Row 1: hot cues 1..4 then beat-jump back/forward.
        int n = com.osgworld.djbooth.deck.DeckState.HOT_CUES + 2;
        int bw = (width - (n - 1) * 2) / n;
        int x = x0;
        for (int i = 0; i < com.osgworld.djbooth.deck.DeckState.HOT_CUES; i++) {
            final int idx = i;
            addRenderableWidget(perfButton(String.valueOf(i + 1),
                    Component.translatable("gui.soundsystem_dj.hotcue", i + 1), x, y, bw, () -> {
                        CdjBlockEntity be = menu.deck(pos);
                        int action = (be != null && be.state().hasHotCue(idx))
                                ? HotCuePayload.JUMP : HotCuePayload.SET;
                        NetworkManager.sendToServer(new HotCuePayload(pos, idx, action));
                    }));
            x += bw + 2;
        }
        addRenderableWidget(perfButton("◀", Component.translatable("gui.soundsystem_dj.jump_back"),
                x, y, bw, () -> NetworkManager.sendToServer(
                        new TransportPayload(pos, TransportPayload.JUMP_BACK))));
        x += bw + 2;
        addRenderableWidget(perfButton("▶", Component.translatable("gui.soundsystem_dj.jump_fwd"),
                x, y, bw, () -> NetworkManager.sendToServer(
                        new TransportPayload(pos, TransportPayload.JUMP_FWD))));

        // Row 2: loop IN / OUT / EXIT / halve / double.
        int y2 = y + 15;
        String[] labels = {"IN", "OUT", "EXIT", "½", "2×"};
        int[] actions = {TransportPayload.LOOP_IN, TransportPayload.LOOP_OUT,
                TransportPayload.LOOP_EXIT, TransportPayload.LOOP_HALVE, TransportPayload.LOOP_DOUBLE};
        String[] keys = {"loop_in", "loop_out", "loop_exit", "loop_halve", "loop_double"};
        int lw = (width - 4 * 2) / 5;
        int lx = x0;
        for (int i = 0; i < labels.length; i++) {
            final int action = actions[i];
            addRenderableWidget(perfButton(labels[i], Component.translatable("gui.soundsystem_dj." + keys[i]),
                    lx, y2, lw, () -> NetworkManager.sendToServer(new TransportPayload(pos, action))));
            lx += lw + 2;
        }

        // Row 3: tempo RANGE selector + TAP tempo (client-side feel/measure).
        int y3 = y + 30;
        int hw = (width - 2) / 2;
        int idx0 = tempoRangeIdx.getOrDefault(pos, 2);
        net.minecraft.client.gui.components.Button rangeBtn = net.minecraft.client.gui.components.Button.builder(
                Component.literal("RANGE " + TEMPO_RANGE_NAMES[idx0]), b -> {
                    int idx = (tempoRangeIdx.getOrDefault(pos, 2) + 1) % TEMPO_RANGES.length;
                    tempoRangeIdx.put(pos, idx);
                    b.setMessage(Component.literal("RANGE " + TEMPO_RANGE_NAMES[idx]));
                })
                .bounds(x0, y3, hw, 13)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.soundsystem_dj.tempo_range")))
                .build();
        addRenderableWidget(rangeBtn);
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("TAP BPM"), b -> tapTempo(pos))
                .bounds(x0 + hw + 2, y3, hw, 13)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.soundsystem_dj.tap")))
                .build());
    }

    private net.minecraft.client.gui.components.Button perfButton(
            String label, Component tip, int x, int y, int w, Runnable action) {
        return net.minecraft.client.gui.components.Button.builder(
                        Component.literal(label), b -> action.run())
                .bounds(x, y, w, 13)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(tip))
                .build();
    }

    private void addUrlBox(BlockPos pos, int x, int y, int width) {
        EditBox box = new EditBox(this.font, x, y, width, 16, Component.literal("URL"));
        box.setMaxLength(1024);
        box.setHint(Component.translatable("gui.soundsystem_dj.url_hint"));
        CdjBlockEntity be = menu.deck(pos);
        if (be != null) {
            box.setValue(be.state().getTrackUrl());
        }
        addRenderableWidget(box);
        urlBoxes.put(box, pos);
    }

    /** Deck whose box is focused, else deck A, else deck B. */
    private BlockPos targetDeck() {
        if (this.getFocused() instanceof EditBox eb && urlBoxes.containsKey(eb)) {
            return urlBoxes.get(eb);
        }
        return menu.refs().deckA() != null ? menu.refs().deckA() : menu.refs().deckB();
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
                Component.translatable("gui.soundsystem_dj.play"), 0xFF1DB954,
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    if (be == null) return;
                    int action = be.state().getPlayState() == PlayState.PLAY
                            ? TransportPayload.PAUSE : TransportPayload.PLAY;
                    NetworkManager.sendToServer(new TransportPayload(pos, action));
                },
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && be.state().getPlayState() == PlayState.PLAY;
                });
        playBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.soundsystem_dj.play")));
        addRenderableWidget(playBtn);

        // CUE (orange): left-click cues/previews, right-click sets the cue point here.
        int[] cue = px(region, BoothLayout.DECK_CUE);
        PanelButton cueBtn = new PanelButton(cue[0], cue[1], cue[2], cue[3],
                Component.translatable("gui.soundsystem_dj.cue"), 0xFFF2A900,
                () -> NetworkManager.sendToServer(new TransportPayload(pos, TransportPayload.CUE)),
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && be.state().getPlayState() == PlayState.CUE;
                }).withSecondary(
                () -> NetworkManager.sendToServer(new TransportPayload(pos, TransportPayload.SET_CUE)));
        cueBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.soundsystem_dj.cue")));
        addRenderableWidget(cueBtn);

        // LOOP toggle.
        int[] loop = px(region, BoothLayout.DECK_LOOP);
        PanelButton loopBtn = new PanelButton(loop[0], loop[1], loop[2], loop[3],
                Component.translatable("gui.soundsystem_dj.loop"), 0xFFC03AA0,
                () -> NetworkManager.sendToServer(
                        new TransportPayload(pos, TransportPayload.LOOP_TOGGLE)),
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && be.state().isLoopOn();
                });
        loopBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.soundsystem_dj.loop")));
        addRenderableWidget(loopBtn);

        addDeckCdjControls(pos, region);

        // Tempo fader (vertical): centre = 100%, full throw = +/- the selected range. The travel is
        // inverted like a real Pioneer pitch fader — pushing it up slows the track down (-%), pulling
        // it down speeds it up (+%).
        int[] t = px(region, BoothLayout.DECK_TEMPO);
        PanelFader tempo = new PanelFader(t[0], t[1], t[2], t[3], true, 0.5,
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    double rate = be != null ? be.state().getRate() : 1.0;
                    return 0.5 - (rate - 1.0) / (2 * tempoRangeFor(pos)); // rate -> fader 0..1
                },
                v -> sendTempo(pos, 1.0 - (v - 0.5) * 2 * tempoRangeFor(pos)));
        tempo.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.soundsystem_dj.tempo")));
        addRenderableWidget(tempo);

        // Jog wheel. It scrubs the position in both directions like a vinyl-mode CDJ jog; while
        // playing it also bends the pitch so forward nudges still feel smooth. Scrub packets are
        // throttled and the leftover angle is carried over, so slow turns aren't lost to rounding.
        int[] j = px(region, BoothLayout.DECK_JOG);
        addRenderableWidget(new PanelJog(j[0], j[1], j[2], j[3], deg -> {
            CdjBlockEntity be = menu.deck(pos);
            if (be == null || minecraft == null || minecraft.level == null) {
                return;
            }
            boolean playing = be.state().getPlayState() == PlayState.PLAY;
            if (playing) {
                // deg is the per-drag angle; turn it into a momentary speed multiplier. Clamped so a
                // hard backwards flick slows down instead of asking for a negative playback rate.
                double factor = Math.max(0.25, Math.min(2.0, 1.0 + deg * JOG_BEND_PER_DEG));
                DeckAudioManager.nudgeBend(pos, factor);
            }
            jogPending.merge(pos, deg, Double::sum);
            long now = net.minecraft.Util.getMillis();
            if (now - jogLastSent.getOrDefault(pos, 0L) < JOG_SEND_MS) {
                return;
            }
            double pending = jogPending.getOrDefault(pos, 0.0);
            long deltaMs = Math.round(pending * MS_PER_DEG);
            if (deltaMs == 0) {
                return; // keep accumulating; a tiny turn shouldn't be rounded away to nothing
            }
            jogPending.put(pos, pending - deltaMs / MS_PER_DEG);
            jogLastSent.put(pos, now);
            long clock = minecraft.level.getGameTime() * 50L;
            long target = Math.max(0, be.state().positionMsAt(clock) + deltaMs);
            NetworkManager.sendToServer(new JogNudgePayload(pos, be.state().getRate(), target));
        }));
    }

    /** The CDJ-3000 controls that aren't transport: direction, slip/quantize, jog mode, tempo
     *  and the memory-cue tools. Each one sits where the panel prints it. */
    private void addDeckCdjControls(BlockPos pos, BoothLayout.Rect region) {
        // DIRECTION steps FWD -> REV -> SLIP REV, so its caption has to say where it is.
        deckButton(pos, region, BoothLayout.DECK_DIRECTION, "gui.soundsystem_dj.direction",
                TransportPayload.DIRECTION, 0xFFFF3B30,
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && be.state().isReverse();
                },
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    int d = be != null ? be.state().getDirection() : DeckStateDir.FWD;
                    return switch (d) {
                        case DeckStateDir.REV -> "REV";
                        case DeckStateDir.SLIP_REV -> "SLIP REV";
                        default -> "FWD";
                    };
                });

        deckToggle(pos, region, BoothLayout.DECK_SLIP, "SLIP", "gui.soundsystem_dj.slip",
                TransportPayload.SLIP, 0xFFFF8A1F, s -> s.isSlip());
        deckToggle(pos, region, BoothLayout.DECK_QUANTIZE, "QUANT", "gui.soundsystem_dj.quantize",
                TransportPayload.QUANTIZE, 0xFF2A7BFF, s -> s.isQuantize());

        // JOG MODE: VINYL means grabbing the platter stops it, CDJ means the jog only bends.
        deckButton(pos, region, BoothLayout.DECK_JOGMODE, "gui.soundsystem_dj.jog_mode",
                TransportPayload.JOG_MODE, 0xFF2A7BFF,
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && be.state().getJogMode() == DeckStateDir.JOG_VINYL;
                },
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && be.state().getJogMode() == DeckStateDir.JOG_VINYL
                            ? "VINYL" : "CDJ";
                });

        deckToggle(pos, region, BoothLayout.DECK_MASTER_TEMPO, "M.TEMPO",
                "gui.soundsystem_dj.master_tempo", TransportPayload.MASTER_TEMPO, 0xFFFF3B30,
                s -> s.isMasterTempo());
        simpleDeckButton(pos, region, BoothLayout.DECK_TEMPO_RESET, "RESET",
                "gui.soundsystem_dj.tempo_reset", TransportPayload.TEMPO_RESET);
        simpleDeckButton(pos, region, BoothLayout.DECK_BEAT_SYNC, "SYNC",
                "gui.soundsystem_dj.beat_sync", TransportPayload.BEAT_SYNC);
        deckToggle(pos, region, BoothLayout.DECK_KEY_SYNC, "KEY", "gui.soundsystem_dj.key_sync",
                TransportPayload.KEY_SYNC, 0xFF9B5DE5, st -> st.getKeyShift() != 0);
        simpleDeckButton(pos, region, BoothLayout.DECK_TRACK_START, "|◀ TRACK",
                "gui.soundsystem_dj.track_start", TransportPayload.TRACK_START);
        simpleDeckButton(pos, region, BoothLayout.DECK_SEARCH_BACK, "◀◀",
                "gui.soundsystem_dj.search_back", TransportPayload.SEARCH_BACK);
        simpleDeckButton(pos, region, BoothLayout.DECK_SEARCH_FWD, "▶▶",
                "gui.soundsystem_dj.search_fwd", TransportPayload.SEARCH_FWD);
        simpleDeckButton(pos, region, BoothLayout.DECK_CALL_PREV, "◀",
                "gui.soundsystem_dj.call_prev", TransportPayload.CALL_PREV);
        simpleDeckButton(pos, region, BoothLayout.DECK_CALL_NEXT, "▶",
                "gui.soundsystem_dj.call_next", TransportPayload.CALL_NEXT);
        simpleDeckButton(pos, region, BoothLayout.DECK_MEM_DELETE, "DEL",
                "gui.soundsystem_dj.memory_delete", TransportPayload.MEMORY_DELETE);
        simpleDeckButton(pos, region, BoothLayout.DECK_MEMORY, "MEMORY",
                "gui.soundsystem_dj.memory", TransportPayload.MEMORY);
    }

    /** Mirrors DeckState's switch positions without importing them into every lambda. */
    private static final class DeckStateDir {
        static final int FWD = com.osgworld.djbooth.deck.DeckState.DIR_FWD;
        static final int REV = com.osgworld.djbooth.deck.DeckState.DIR_REV;
        static final int SLIP_REV = com.osgworld.djbooth.deck.DeckState.DIR_SLIP_REV;
        static final int JOG_VINYL = com.osgworld.djbooth.deck.DeckState.JOG_VINYL;
    }

    /** A deck button whose caption changes with the state behind it. */
    private void deckButton(BlockPos pos, BoothLayout.Rect region, BoothLayout.Rect ctrl,
                            String tipKey, int action, int accent,
                            java.util.function.BooleanSupplier lit,
                            java.util.function.Supplier<String> caption) {
        int[] k = px(region, ctrl);
        PanelButton b = new PanelButton(k[0], k[1], k[2], k[3],
                Component.literal(caption.get()), accent,
                () -> NetworkManager.sendToServer(new TransportPayload(pos, action)),
                lit).withCaption(caption);
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(tipKey)));
        addRenderableWidget(b);
    }

    /** A deck button that lights while some flag on the deck is set. */
    private void deckToggle(BlockPos pos, BoothLayout.Rect region, BoothLayout.Rect ctrl,
                            String label, String tipKey, int action, int accent,
                            java.util.function.Predicate<com.osgworld.djbooth.deck.DeckState> lit) {
        int[] k = px(region, ctrl);
        PanelButton b = new PanelButton(k[0], k[1], k[2], k[3],
                Component.literal(label), accent,
                () -> NetworkManager.sendToServer(new TransportPayload(pos, action)),
                () -> {
                    CdjBlockEntity be = menu.deck(pos);
                    return be != null && lit.test(be.state());
                }).withCaption();
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(tipKey)));
        addRenderableWidget(b);
    }

    /** A deck button that just fires an action. */
    private void simpleDeckButton(BlockPos pos, BoothLayout.Rect region, BoothLayout.Rect ctrl,
                                  String label, String tipKey, int action) {
        int[] k = px(region, ctrl);
        PanelButton b = new PanelButton(k[0], k[1], k[2], k[3],
                Component.literal(label), 0xFF25E0C0,
                () -> NetworkManager.sendToServer(new TransportPayload(pos, action)),
                () -> false).withCaption();
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(tipKey)));
        addRenderableWidget(b);
    }

    /** Register a beat tap; average the recent intervals into a BPM for this deck. */
    private void tapTempo(BlockPos pos) {
        long now = System.currentTimeMillis();
        java.util.ArrayDeque<Long> q = taps.computeIfAbsent(pos, k -> new java.util.ArrayDeque<>());
        if (!q.isEmpty() && now - q.peekLast() > TAP_RESET_MS) {
            q.clear();
        }
        q.addLast(now);
        while (q.size() > 8) {
            q.removeFirst();
        }
        if (q.size() >= 2) {
            long span = q.peekLast() - q.peekFirst();
            double avgInterval = (double) span / (q.size() - 1);
            if (avgInterval > 0) {
                double measured = 60000.0 / avgInterval;
                bpm.put(pos, measured);
                // The deck needs the tempo too: QUANTIZE snaps to it and BEAT SYNC matches it.
                NetworkManager.sendToServer(
                        com.osgworld.djbooth.net.DeckBpmPayload.tempoOnly(pos, (float) measured));
            }
        }
    }

    /** A pasted link loads directly; anything else is a YouTube search for the top hit. */
    private void submitTrack(BlockPos pos, String input) {
        String q = input.trim();
        if (q.isEmpty()) {
            return;
        }
        rememberRecent(q);
        // Ask the database what this track's tempo and key are, if the player configured a key.
        // Independent of loading the audio: a lookup miss must never stop a track from playing.
        com.osgworld.djbooth.client.audio.TrackLookup.lookup(q, info ->
                NetworkManager.sendToServer(new com.osgworld.djbooth.net.DeckBpmPayload(
                        pos, (float) info.bpm(),
                        info.key() == null ? -1 : info.key().root(),
                        info.key() != null && info.key().minor())));
        if (q.startsWith("http://") || q.startsWith("https://")) {
            loadTrack(pos, q);
        } else {
            DeckAudioManager.searchTop(q, url -> loadTrack(pos, url));
        }
    }

    private void rememberRecent(String q) {
        RECENTS.remove(q);
        RECENTS.add(0, q);
        while (RECENTS.size() > MAX_RECENTS) {
            RECENTS.remove(RECENTS.size() - 1);
        }
        this.rebuildWidgets(); // refresh the recent chips
    }

    private void loadTrack(BlockPos pos, String url) {
        NetworkManager.sendToServer(new com.osgworld.djbooth.net.LoadTrackPayload(pos, url.trim()));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (this.getFocused() instanceof EditBox eb && urlBoxes.containsKey(eb)) {
            // Enter loads the track; otherwise let the box handle it and swallow the key so the
            // inventory hotkey ('e') can't close the screen mid-typing.
            if (key == 257 || key == 335) {
                submitTrack(urlBoxes.get(eb), eb.getValue());
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
                m -> m.getFaderA(), Component.translatable("gui.soundsystem_dj.fader_a"));
        addMixerFader(BoothLayout.MIX_FADER_B, true, MixerPayload.FADER_B,
                m -> m.getFaderB(), Component.translatable("gui.soundsystem_dj.fader_b"));
        addMixerFader(BoothLayout.MIX_MASTER, true, MixerPayload.MASTER,
                m -> m.getMaster(), Component.translatable("gui.soundsystem_dj.master"));
        addMixerFader(BoothLayout.MIX_XFADER, false, MixerPayload.CROSSFADER,
                m -> m.getCrossfader(), Component.translatable("gui.soundsystem_dj.crossfader"));

        // Channel A EQ + COLOR filter (labels to the left). Band gains, flat at centre.
        addMixerKnob(BoothLayout.MIX_HI_A, "HI", true, MixerPayload.EQ_HI_A, 0.5, this::eqDb,
                m -> m.getEqHiA(), Component.translatable("gui.soundsystem_dj.eq_hi"));
        addMixerKnob(BoothLayout.MIX_MID_A, "MID", true, MixerPayload.EQ_MID_A, 0.5, this::eqDb,
                m -> m.getEqMidA(), Component.translatable("gui.soundsystem_dj.eq_mid"));
        addMixerKnob(BoothLayout.MIX_LOW_A, "LOW", true, MixerPayload.EQ_LOW_A, 0.5, this::eqDb,
                m -> m.getEqLowA(), Component.translatable("gui.soundsystem_dj.eq_low"));
        addMixerKnob(BoothLayout.MIX_FILTER_A, "FLT", true, MixerPayload.FILTER_A, 0.5, this::colorReadout,
                m -> m.getFilterA(), Component.translatable("gui.soundsystem_dj.filter"));
        // Channel B EQ + colour filter (labels to the right).
        addMixerKnob(BoothLayout.MIX_HI_B, "HI", false, MixerPayload.EQ_HI_B, 0.5, this::eqDb,
                m -> m.getEqHiB(), Component.translatable("gui.soundsystem_dj.eq_hi"));
        addMixerKnob(BoothLayout.MIX_MID_B, "MID", false, MixerPayload.EQ_MID_B, 0.5, this::eqDb,
                m -> m.getEqMidB(), Component.translatable("gui.soundsystem_dj.eq_mid"));
        addMixerKnob(BoothLayout.MIX_LOW_B, "LOW", false, MixerPayload.EQ_LOW_B, 0.5, this::eqDb,
                m -> m.getEqLowB(), Component.translatable("gui.soundsystem_dj.eq_low"));
        addMixerKnob(BoothLayout.MIX_FILTER_B, "FLT", false, MixerPayload.FILTER_B, 0.5, this::colorReadout,
                m -> m.getFilterB(), Component.translatable("gui.soundsystem_dj.filter"));

        // Channel trim, in the spot the real mixer puts GAIN: top of each strip, unity at centre.
        addMixerKnob(BoothLayout.MIX_GAIN_A, "GAIN", true, MixerPayload.GAIN_A, 0.5, BoothScreen::trimDb,
                m -> m.getGainA(), Component.translatable("gui.soundsystem_dj.gain"));
        addMixerKnob(BoothLayout.MIX_GAIN_B, "GAIN", false, MixerPayload.GAIN_B, 0.5, BoothScreen::trimDb,
                m -> m.getGainB(), Component.translatable("gui.soundsystem_dj.gain"));

        // Echo (Beat FX) per channel, off at rest.
        addMixerKnob(BoothLayout.MIX_ECHO_A, "FX", true, MixerPayload.FX_ECHO_A, 0.0, v -> Math.round(v * 100) + "%",
                m -> m.getEchoA(), Component.translatable("gui.soundsystem_dj.echo"));
        addMixerKnob(BoothLayout.MIX_ECHO_B, "FX", false, MixerPayload.FX_ECHO_B, 0.0, v -> Math.round(v * 100) + "%",
                m -> m.getEchoB(), Component.translatable("gui.soundsystem_dj.echo"));

        // SOUND COLOR FX: six mode buttons plus the PARAMETER knob, driving both COLOR knobs.
        addColorModeButtons();
        addMixerKnob(BoothLayout.MIX_COLOR_PARAM, "PARAM", true, MixerPayload.COLOR_PARAM, 0.5,
                v -> Math.round(v * 100) + "%",
                m -> m.getColorParam(), Component.translatable("gui.soundsystem_dj.color_param"));

        addBeatFxPanel();

        // CROSS FADER ASSIGN under each channel fader: A / THRU / B, like the hardware switch.
        addMixerCycle(BoothLayout.MIX_XF_ASSIGN_A, MixerPayload.XF_ASSIGN_A,
                MixerBlockEntity::getXfAssignA, "gui.soundsystem_dj.xf_assign");
        addMixerCycle(BoothLayout.MIX_XF_ASSIGN_B, MixerPayload.XF_ASSIGN_B,
                MixerBlockEntity::getXfAssignB, "gui.soundsystem_dj.xf_assign");

        // Global switches: EQ curve (isolator/EQ) and channel fader curve.
        addMixerToggle(BoothLayout.MIX_ISOLATOR, MixerPayload.ISOLATOR,
                MixerBlockEntity::isIsolator, "ISO", "EQ", "gui.soundsystem_dj.eq_curve");
        addMixerCycle(BoothLayout.MIX_FADERCURVE, MixerPayload.FADER_CURVE,
                MixerBlockEntity::getChFaderCurve, MixerBlockEntity.CURVE_NAMES,
                "gui.soundsystem_dj.fader_curve");
        addMixerCycle(BoothLayout.MIX_XFCURVE, MixerPayload.CROSSFADER_CURVE,
                MixerBlockEntity::getCrossFaderCurve, MixerBlockEntity.CURVE_NAMES,
                "gui.soundsystem_dj.xfader_curve");

        // Master section: BALANCE, BOOTH MONITOR, and a headphone CUE per channel.
        addMixerKnob(BoothLayout.MIX_BALANCE, "BAL", true, MixerPayload.BALANCE, 0.5,
                BoothScreen::balanceReadout,
                m -> m.getBalance(), Component.translatable("gui.soundsystem_dj.balance"));
        addMixerKnob(BoothLayout.MIX_BOOTH, "BOOTH", true, MixerPayload.BOOTH, 1.0,
                v -> Math.round(v * 100) + "%",
                m -> m.getBooth(), Component.translatable("gui.soundsystem_dj.booth"));
        addCueButton(BoothLayout.MIX_CUE_A, MixerPayload.CUE_A, MixerBlockEntity::isCueA);
        addCueButton(BoothLayout.MIX_CUE_B, MixerPayload.CUE_B, MixerBlockEntity::isCueB);
    }

    /** Knob readouts in the units the panel prints, so a glance says what the control is doing. */
    private String eqDb(double v) {
        MixerBlockEntity be = menu.mixer();
        boolean iso = be != null && be.isIsolator();
        if (v <= 0.001) {
            return iso ? "KILL" : "-26 dB";
        }
        double db = v >= 0.5 ? (v - 0.5) / 0.5 * 6.0 : (v / 0.5 - 1.0) * (iso ? 60.0 : 26.0);
        return String.format("%+.1f dB", db);
    }

    private static String trimDb(double v) {
        if (v <= 0.001) {
            return "-∞";
        }
        return String.format("%+.1f dB", 20.0 * Math.log10(v * 2.0));
    }

    /** COLOR knob: which way it is turned and how far, named after the current mode's two sides. */
    private String colorReadout(double v) {
        MixerBlockEntity be = menu.mixer();
        int mode = be != null ? be.getColorMode() : com.osgworld.djbooth.mixer.ColorFxModes.FILTER;
        double off = v - 0.5;
        if (Math.abs(off) < 0.03) {
            return "OFF";
        }
        String side = switch (mode) {
            case com.osgworld.djbooth.mixer.ColorFxModes.FILTER -> off < 0 ? "LPF" : "HPF";
            case com.osgworld.djbooth.mixer.ColorFxModes.SWEEP -> off < 0 ? "GATE" : "BPF";
            case com.osgworld.djbooth.mixer.ColorFxModes.CRUSH -> off < 0 ? "DRIVE" : "CRUSH";
            case com.osgworld.djbooth.mixer.ColorFxModes.NOISE -> off < 0 ? "LO" : "HI";
            default -> off < 0 ? "LOW" : "HIGH";
        };
        return side + " " + Math.round(Math.abs(off) * 200) + "%";
    }

    /** A channel's CUE button: previews that deck for whoever is stood at the booth. */
    private void addCueButton(BoothLayout.Rect ctrl, int channel,
                              java.util.function.Predicate<MixerBlockEntity> state) {
        BlockPos mix = menu.refs().mixer();
        int[] k = px(BoothLayout.REGION_MIXER, ctrl);
        PanelButton b = new PanelButton(k[0], k[1], k[2], k[3],
                Component.literal("CUE"), 0xFFFF8A1F,
                () -> {
                    MixerBlockEntity be = menu.mixer();
                    boolean next = be == null || !state.test(be);
                    NetworkManager.sendToServer(new MixerPayload(mix, channel, next ? 1f : 0f));
                },
                () -> {
                    MixerBlockEntity be = menu.mixer();
                    return be != null && state.test(be);
                });
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.soundsystem_dj.cue_channel")));
        addRenderableWidget(b);
    }

    private static String balanceReadout(double v) {
        int off = (int) Math.round((v - 0.5) * 200);
        if (off == 0) {
            return "CENTRE";
        }
        return (off < 0 ? "L" : "R") + Math.abs(off);
    }

    /** The BEAT FX panel: pick an effect, a beat fraction and a channel, set how much of it you
     *  want, tap the tempo, and switch it in. Laid out where the hardware puts each group. */
    private void addBeatFxPanel() {
        BlockPos mix = menu.refs().mixer();

        // Effect selector: the fourteen types the selector knob steps through, as a grid.
        gridButtons(BoothLayout.FX_TYPES, com.osgworld.djbooth.mixer.BeatFxTypes.NAMES.length, 5,
                i -> com.osgworld.djbooth.mixer.BeatFxTypes.NAMES[i],
                i -> Component.translatable(com.osgworld.djbooth.mixer.BeatFxTypes.tipKey(i)),
                i -> NetworkManager.sendToServer(new MixerPayload(mix, MixerPayload.BEATFX_TYPE, i)),
                i -> {
                    MixerBlockEntity be = menu.mixer();
                    return be != null && be.getBeatFxType() == i;
                });

        // Beat fraction: 1/16 .. 4, exactly the buttons on the panel.
        gridButtons(BoothLayout.FX_BEATS,
                com.osgworld.djbooth.mixer.BeatFxTypes.BEAT_NAMES.length, 4,
                i -> com.osgworld.djbooth.mixer.BeatFxTypes.BEAT_NAMES[i],
                i -> Component.translatable("gui.soundsystem_dj.beat_fraction"),
                i -> NetworkManager.sendToServer(new MixerPayload(mix, MixerPayload.BEATFX_BEAT, i)),
                i -> {
                    MixerBlockEntity be = menu.mixer();
                    return be != null && be.getBeatFxBeat() == i;
                });

        // FX FREQUENCY: mute a band out of the effect send, like the three lit buttons.
        int[] bandBits = {com.osgworld.djbooth.mixer.BeatFxTypes.BAND_LOW,
                com.osgworld.djbooth.mixer.BeatFxTypes.BAND_MID,
                com.osgworld.djbooth.mixer.BeatFxTypes.BAND_HI};
        String[] bandNames = {"LOW", "MID", "HI"};
        gridButtons(BoothLayout.FX_FREQ, 3, 3,
                i -> bandNames[i],
                i -> Component.translatable("gui.soundsystem_dj.fx_freq"),
                i -> {
                    MixerBlockEntity be = menu.mixer();
                    int bands = be != null ? be.getBeatFxBands()
                            : com.osgworld.djbooth.mixer.BeatFxTypes.BANDS_ALL;
                    NetworkManager.sendToServer(
                            new MixerPayload(mix, MixerPayload.BEATFX_BANDS, bands ^ bandBits[i]));
                },
                i -> {
                    MixerBlockEntity be = menu.mixer();
                    return be != null && (be.getBeatFxBands() & bandBits[i]) != 0;
                });

        // Which channel the effect is patched across.
        gridButtons(BoothLayout.FX_CHANNEL,
                com.osgworld.djbooth.mixer.BeatFxTypes.CHANNEL_NAMES.length, 3,
                i -> com.osgworld.djbooth.mixer.BeatFxTypes.CHANNEL_NAMES[i],
                i -> Component.translatable("gui.soundsystem_dj.fx_channel"),
                i -> NetworkManager.sendToServer(
                        new MixerPayload(mix, MixerPayload.BEATFX_CHANNEL, i)),
                i -> {
                    MixerBlockEntity be = menu.mixer();
                    return be != null && be.getBeatFxChannel() == i;
                });

        addMixerKnob(BoothLayout.FX_DEPTH, "DEPTH", true, MixerPayload.BEATFX_DEPTH, 0.5,
                v -> Math.round(v * 100) + "%",
                m -> m.getBeatFxDepth(), Component.translatable("gui.soundsystem_dj.fx_depth"));

        // TAP sets the BPM the beat fractions are measured against.
        int[] tap = px(BoothLayout.REGION_MIXER, BoothLayout.FX_TAP);
        PanelButton tapBtn = new PanelButton(tap[0], tap[1], tap[2], tap[3],
                Component.literal("TAP"), 0xFF25E0C0,
                () -> {
                    Double measured = tapBeatFx();
                    if (measured != null) {
                        NetworkManager.sendToServer(
                                new MixerPayload(mix, MixerPayload.BPM, measured.floatValue()));
                    }
                },
                () -> false).withCaption();
        tapBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.soundsystem_dj.fx_tap")));
        addRenderableWidget(tapBtn);

        // The big ON/OFF at the bottom of the panel.
        int[] onoff = px(BoothLayout.REGION_MIXER, BoothLayout.FX_ONOFF);
        PanelButton onBtn = new PanelButton(onoff[0], onoff[1], onoff[2], onoff[3],
                Component.literal("ON/OFF"), 0xFF2A7BFF,
                () -> {
                    MixerBlockEntity be = menu.mixer();
                    boolean next = be == null || !be.isBeatFxOn();
                    NetworkManager.sendToServer(
                            new MixerPayload(mix, MixerPayload.BEATFX_ON, next ? 1f : 0f));
                },
                () -> {
                    MixerBlockEntity be = menu.mixer();
                    return be != null && be.isBeatFxOn();
                }).withCaption();
        onBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.soundsystem_dj.fx_onoff")));
        addRenderableWidget(onBtn);
    }

    /** Lay {@code count} lit-when-selected buttons into a box, wrapping every {@code cols}. */
    private void gridButtons(BoothLayout.Rect box, int count, int cols,
                             java.util.function.IntFunction<String> label,
                             java.util.function.IntFunction<Component> tip,
                             java.util.function.IntConsumer onPress,
                             java.util.function.IntPredicate lit) {
        int[] b = px(BoothLayout.REGION_MIXER, box);
        int rows = (count + cols - 1) / cols;
        int gap = 1;
        int bw = (b[2] - (cols - 1) * gap) / cols;
        int bh = (b[3] - (rows - 1) * gap) / rows;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            PanelButton btn = new PanelButton(
                    b[0] + (i % cols) * (bw + gap), b[1] + (i / cols) * (bh + gap), bw, bh,
                    Component.literal(label.apply(i)), 0xFF25E0C0,
                    () -> onPress.accept(idx), () -> lit.test(idx)).withCaption();
            btn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tip.apply(i)));
            addRenderableWidget(btn);
        }
    }

    // Tap history for the mixer's BEAT FX tempo (separate from the per-deck tap readouts).
    private final java.util.ArrayDeque<Long> fxTaps = new java.util.ArrayDeque<>();

    /** Register a tap on the BEAT FX TAP button; returns the measured BPM once there are enough. */
    private Double tapBeatFx() {
        long now = System.currentTimeMillis();
        if (!fxTaps.isEmpty() && now - fxTaps.peekLast() > TAP_RESET_MS) {
            fxTaps.clear();
        }
        fxTaps.addLast(now);
        while (fxTaps.size() > 8) {
            fxTaps.removeFirst();
        }
        if (fxTaps.size() < 2) {
            return null;
        }
        double avg = (double) (fxTaps.peekLast() - fxTaps.peekFirst()) / (fxTaps.size() - 1);
        return avg > 0 ? 60000.0 / avg : null;
    }

    /** The six SOUND COLOR FX buttons, laid out two columns by three rows like the panel.
     *  The selected one stays lit so it's obvious which effect the COLOR knobs are driving. */
    private void addColorModeButtons() {
        BlockPos mix = menu.refs().mixer();
        int[] box = px(BoothLayout.REGION_MIXER, BoothLayout.MIX_COLOR_MODES);
        int cols = 2, rows = 3, gap = 1;
        int bw = (box[2] - gap) / cols;
        int bh = (box[3] - (rows - 1) * gap) / rows;
        for (int i = 0; i < com.osgworld.djbooth.mixer.ColorFxModes.MODES; i++) {
            final int mode = i;
            int bx = box[0] + (i % cols) * (bw + gap);
            int by = box[1] + (i / cols) * (bh + gap);
            PanelButton b = new PanelButton(bx, by, bw, bh,
                    Component.literal(com.osgworld.djbooth.mixer.ColorFxModes.NAMES[mode]),
                    0xFF25E0C0,
                    () -> NetworkManager.sendToServer(
                            new MixerPayload(mix, MixerPayload.COLOR_MODE, mode)),
                    () -> {
                        MixerBlockEntity be = menu.mixer();
                        return be != null && be.getColorMode() == mode;
                    }).withCaption();
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable(com.osgworld.djbooth.mixer.ColorFxModes.tipKey(mode))));
            addRenderableWidget(b);
        }
    }

    /** Labels for the CROSS FADER ASSIGN positions, in switch order. */
    private static final String[] XF_ASSIGN_LABELS = {"A", "THRU", "B"};

    private void addMixerCycle(BoothLayout.Rect ctrl, int channel,
                               java.util.function.ToIntFunction<MixerBlockEntity> state,
                               String tipKey) {
        addMixerCycle(ctrl, channel, state, XF_ASSIGN_LABELS, tipKey);
    }

    /** A multi-position switch that steps through its positions on click, synced to the server. */
    private void addMixerCycle(BoothLayout.Rect ctrl, int channel,
                               java.util.function.ToIntFunction<MixerBlockEntity> state,
                               String[] labels, String tipKey) {
        BlockPos mix = menu.refs().mixer();
        int[] k = px(BoothLayout.REGION_MIXER, ctrl);
        java.util.function.Supplier<Integer> cur = () -> {
            MixerBlockEntity be = menu.mixer();
            return be != null ? state.applyAsInt(be) : 0;
        };
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal(labels[cur.get()]), b -> {
                            int next = (cur.get() + 1) % labels.length;
                            NetworkManager.sendToServer(new MixerPayload(mix, channel, next));
                            b.setMessage(Component.literal(labels[next]));
                        })
                .bounds(k[0], k[1], k[2], k[3])
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable(tipKey)))
                .build());
    }

    /** A two-state switch on the mixer (isolator/EQ, sharp/linear fader), synced to the server. */
    private void addMixerToggle(BoothLayout.Rect ctrl, int channel,
                                java.util.function.Predicate<MixerBlockEntity> state,
                                String onLabel, String offLabel, String tipKey) {
        BlockPos mix = menu.refs().mixer();
        int[] k = px(BoothLayout.REGION_MIXER, ctrl);
        java.util.function.Supplier<Boolean> cur = () -> {
            MixerBlockEntity be = menu.mixer();
            return be != null && state.test(be);
        };
        net.minecraft.client.gui.components.Button btn =
                net.minecraft.client.gui.components.Button.builder(
                        Component.literal(cur.get() ? onLabel : offLabel), b -> {
                            boolean next = !cur.get();
                            NetworkManager.sendToServer(
                                    new MixerPayload(mix, channel, next ? 1f : 0f));
                            b.setMessage(Component.literal(next ? onLabel : offLabel));
                        })
                        .bounds(k[0], k[1], k[2], k[3])
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.translatable(tipKey)))
                        .build();
        addRenderableWidget(btn);
    }

    private void addMixerKnob(BoothLayout.Rect ctrl, String tag, boolean labelLeft, int channel,
                              double defaultValue,
                              java.util.function.Function<MixerBlockEntity, Float> getter,
                              Component label) {
        addMixerKnob(ctrl, tag, labelLeft, channel, defaultValue, null, getter, label);
    }

    private void addMixerKnob(BoothLayout.Rect ctrl, String tag, boolean labelLeft, int channel,
                              double defaultValue,
                              java.util.function.DoubleFunction<String> format,
                              java.util.function.Function<MixerBlockEntity, Float> getter,
                              Component label) {
        BlockPos mix = menu.refs().mixer();
        int[] k = px(BoothLayout.REGION_MIXER, ctrl);
        com.osgworld.djbooth.client.screen.widget.PanelKnob knob =
                new com.osgworld.djbooth.client.screen.widget.PanelKnob(k[0], k[1], k[2], k[3], tag, labelLeft,
                        defaultValue, format,
                        () -> {
                            MixerBlockEntity be = menu.mixer();
                            return be != null ? getter.apply(be) : defaultValue;
                        },
                        v -> sendControl(mix, channel, (float) v));
        knob.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        addRenderableWidget(knob);
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
                v -> sendControl(mix, channel, (float) v));
        fader.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        fader.setMessage(label);
        addRenderableWidget(fader);
    }

    // --- Rendering ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // Needle search: click on a CDJ screen to jump to that point in the track.
        if (button == 0) {
            if (trySeekOnScreen(menu.refs().deckA(), BoothLayout.REGION_DECK_A, mouseX, mouseY)
                    || trySeekOnScreen(menu.refs().deckB(), BoothLayout.REGION_DECK_B, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private boolean trySeekOnScreen(BlockPos pos, BoothLayout.Rect region, double mx, double my) {
        if (pos == null) {
            return false;
        }
        // A couple of pixels of slack: the strip is only a few pixels tall and the mouse is not.
        DeckScreenLayout lay = screenLayout(region);
        if (!lay.overviewHit(mx, my, 2)) {
            return false;
        }
        long dur = DeckAudioManager.durationMs(pos);
        if (dur <= 0) {
            return false;
        }
        CdjBlockEntity be = menu.deck(pos);
        if (be == null) {
            return false;
        }
        double frac = lay.overviewFraction(mx);
        long target = Math.round(frac * dur);
        NetworkManager.sendToServer(new JogNudgePayload(pos, be.state().getRate(), target));
        return true;
    }

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
        flushControls();
        drawLevelMeters(g);
        drawGuide(g);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Channel level meters, fed by the actual peak level of each deck's audio. */
    private void drawLevelMeters(GuiGraphics g) {
        drawMeter(g, BoothLayout.MIX_METER_A, menu.refs().deckA());
        drawMeter(g, BoothLayout.MIX_METER_B, menu.refs().deckB());
    }

    /** A column of LED segments, green up to about -6 dB, amber above it, red at the top. */
    private void drawMeter(GuiGraphics g, BoothLayout.Rect ctrl, BlockPos deck) {
        if (deck == null) {
            return;
        }
        float peak = DeckAudioManager.peakLevel(deck);
        int[] m = px(BoothLayout.REGION_MIXER, ctrl);
        int segments = 12;
        int segH = Math.max(1, m[3] / segments);
        // Meters are read in dB, so a linear bar would spend most of its travel doing nothing.
        double db = peak > 1e-4 ? 20.0 * Math.log10(peak) : -60.0;
        int lit = (int) Math.round((db + 30.0) / 30.0 * segments); // -30 dB .. 0 dB across the strip
        for (int i = 0; i < segments; i++) {
            int y = m[1] + m[3] - (i + 1) * segH;
            boolean on = i < lit;
            int colour = i >= segments - 2 ? 0xFFFF2A2A       // last two segments: clipping
                    : i >= segments - 5 ? 0xFFFFC02A          // approaching it
                    : 0xFF2ADF6A;
            g.fill(m[0], y, m[0] + m[2], y + segH - 1, on ? colour : 0x33101014);
        }
    }

    /** Two-line "how to use" hint below the panel (off the artwork). */
    private void drawGuide(GuiGraphics g) {
        int cx = leftPos + imageWidth / 2;
        int y = topPos + imageHeight + BOTTOM_STRIP - 2 * (this.font.lineHeight + 1);
        g.drawCenteredString(this.font, Component.translatable("gui.soundsystem_dj.guide1"), cx, y, 0xFF1DB954);
        g.drawCenteredString(this.font, Component.translatable("gui.soundsystem_dj.guide2"),
                cx, y + this.font.lineHeight + 1, 0xFFB8B8C0);
        // GetSongBPM's terms require a visible credit wherever their data is used, so it shows
        // whenever the lookup is switched on — and takes no space when it isn't.
        if (com.osgworld.djbooth.DJBoothConfig.lookupEnabled()) {
            g.drawCenteredString(this.font, Component.translatable("gui.soundsystem_dj.credit_getsongbpm"),
                    cx, y - this.font.lineHeight - 1, 0xFF7A7A84);
        }
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
        long dur = DeckAudioManager.durationMs(pos);
        boolean playing = be.state().getPlayState() == PlayState.PLAY;

        int[] scr = px(region, BoothLayout.DECK_SCREEN);
        int x0 = scr[0], y0 = scr[1], sw = scr[2], sh = scr[3];
        var state = be.state();

        // The panel art has a printed screen bezel under here, but the readout used to be drawn
        // straight onto it: thin coloured bars over photographed plastic, hard to read at any GUI
        // scale. Give it an actual screen to sit on first.
        drawScreenPanel(g, x0, y0, sw, sh);

        var lay = DeckScreenLayout.of(x0, y0, sw, sh, this.font.lineHeight);
        drawZoomedWave(g, lay.waveX(), lay.waveY(), lay.waveW(), lay.waveH(),
                ms, state.getBpm(), playing);
        drawOverview(g, lay.overviewX(), lay.overviewY(), lay.overviewW(), lay.overviewH(),
                ms, dur, state);
        drawScreenHeader(g, lay.headerX(), lay.headerY(), lay.headerW(), ms, dur, state, pos);
    }

    /**
     * The strip along the bottom of the screen that a click seeks on.
     *
     * <p>Only this strip, not the whole screen. The zoomed wave above it is centred on the
     * playhead, so a click there maps to a moment a few seconds away, not to a fraction of the
     * track - and a stray click while reaching for a knob would jump the deck mid-mix. A CDJ keeps
     * needle search on its own strip for the same reason.
     */
    private DeckScreenLayout screenLayout(BoothLayout.Rect region) {
        int[] scr = px(region, BoothLayout.DECK_SCREEN);
        return DeckScreenLayout.of(scr[0], scr[1], scr[2], scr[3], this.font.lineHeight);
    }

    /** The screen itself: a black inset with a bezel, so everything drawn on it reads. */
    private void drawScreenPanel(GuiGraphics g, int x0, int y0, int sw, int sh) {
        g.fill(x0, y0, x0 + sw, y0 + sh, 0xFF07070B);              // near black, not pure
        g.renderOutline(x0, y0, sw, sh, 0xFF2A2A34);               // bezel
        g.fill(x0 + 1, y0 + 1, x0 + sw - 1, y0 + 2, 0x14FFFFFF);   // faint top glint, reads as glass
    }

    /**
     * The zoomed waveform: a few seconds of track scrolling under a fixed playhead in the middle,
     * the way a CDJ shows it. Played time is on the left, what is coming is on the right.
     *
     * <p>The old version scrolled the wave but coloured it by bar index against overall track
     * progress, so the boundary between "played" and "not played" sat wherever the track happened
     * to be as a fraction, with no relation to the audio underneath it. With the playhead fixed in
     * the centre, the split is where it belongs and stays there.
     *
     * <p>The shape is still procedural: WaterMedia hands over decoded PCM, not an analysed track,
     * so there is nothing to draw the real envelope from. It is seeded by absolute position, so a
     * given moment of a track always looks the same and scrubbing back shows the same hills again.
     */
    private void drawZoomedWave(GuiGraphics g, int x, int y, int w, int h,
                                long ms, double bpm, boolean playing) {
        int mid = y + h / 2;
        int half = Math.max(2, h / 2 - 1);
        int centre = x + w / 2;
        double msPerPx = ZOOM_SECONDS * 1000.0 / w;

        // Beat grid behind the wave, if the deck knows a tempo. Lining the wave up against these
        // is how beatmatching by eye works.
        if (bpm > 0) {
            double beatMs = 60000.0 / bpm;
            long firstBeat = (long) Math.floor((ms - (w / 2.0) * msPerPx) / beatMs);
            for (long b = firstBeat; ; b++) {
                double t = b * beatMs;
                int bx = centre + (int) Math.round((t - ms) / msPerPx);
                if (bx > x + w) {
                    break;
                }
                if (bx >= x && b >= 0) {
                    boolean bar = b % 4 == 0; // downbeats brighter, so bars are countable
                    g.fill(bx, y, bx + 1, y + h, bar ? 0x40FFFFFF : 0x1AFFFFFF);
                }
            }
        }

        for (int i = 0; i < w; i++) {
            double t = ms + (i - w / 2.0) * msPerPx;
            if (t < 0) {
                continue;
            }
            boolean played = i < w / 2;
            // Three bands stacked, as a real player colours a waveform: bass wide and blue, mids
            // orange over it, highs a bright cap. Reading the bass line alone tells you where the
            // drop is without listening.
            float low = band(t, 37L, 900);
            float mid1 = band(t, 91L, 320);
            float high = band(t, 173L, 110);
            int lowA = 1 + Math.round(low * half);
            int midA = 1 + Math.round(mid1 * half * 0.72f);
            int hiA = 1 + Math.round(high * half * 0.42f);
            int dim = played ? 0x80 : 0xFF;
            g.fill(x + i, mid - lowA, x + i + 1, mid + lowA, argb(dim, 0x2E, 0x6C, 0xE0));
            g.fill(x + i, mid - midA, x + i + 1, mid + midA, argb(dim, 0xF2, 0x9A, 0x2E));
            g.fill(x + i, mid - hiA, x + i + 1, mid + hiA, argb(dim, 0xF0, 0xF4, 0xFF));
        }

        // Playhead last, over everything, so it is never lost in a loud passage.
        int head = playing ? 0xFFFF3B30 : 0xFF9AA0AA;
        g.fill(centre, y, centre + 1, y + h, head);
    }

    /**
     * The whole track at a glance along the bottom, with the cue and hot cues marked and the loop
     * shaded — the strip a CDJ puts under the wave, and the one you touch to jump.
     */
    private void drawOverview(GuiGraphics g, int x, int y, int w, int h,
                              long ms, long dur, com.osgworld.djbooth.deck.DeckState state) {
        g.fill(x, y, x + w, y + h, 0xFF14141C);
        if (dur <= 0) {
            return;
        }
        for (int i = 0; i < w; i++) {
            double t = (double) i / w * dur;
            int amp = 1 + Math.round(band(t, 37L, 900) * (h - 2));
            boolean played = t <= ms;
            g.fill(x + i, y + h - amp, x + i + 1, y + h,
                    played ? 0xFF2E6CE0 : 0x66505A72);
        }
        if (state.isLoopOn() && state.getLoopOutMs() > state.getLoopInMs()) {
            int a = x + (int) (w * clamp01d((double) state.getLoopInMs() / dur));
            int b = x + (int) (w * clamp01d((double) state.getLoopOutMs() / dur));
            g.fill(a, y, Math.max(a + 1, b), y + h, 0x3325E0C0);
        }
        long cue = state.getCuePointMs();
        if (cue >= 0) {
            markOverview(g, x, y, w, h, cue, dur, 0xFFFF8A1F);
        }
        for (int i = 0; i < 4; i++) {
            if (state.hasHotCue(i)) {
                markOverview(g, x, y, w, h, state.getHotCue(i), dur, HOT_CUE_COLOURS[i]);
            }
        }
        int px = x + (int) (w * clamp01d((double) ms / dur));
        g.fill(px, y - 1, px + 1, y + h + 1, 0xFFFFFFFF);
    }

    private void markOverview(GuiGraphics g, int x, int y, int w, int h,
                              long at, long dur, int colour) {
        int mx = x + (int) (w * clamp01d((double) at / dur));
        g.fill(mx, y, mx + 1, y + h, colour);
    }

    /** Time on the left, tempo and key on the right, on the black rather than on the artwork. */
    private void drawScreenHeader(GuiGraphics g, int x, int y, int w, long ms, long dur,
                                  com.osgworld.djbooth.deck.DeckState state, BlockPos pos) {
        g.drawString(this.font, fmtTime(ms), x, y, 0xFF00E0A0, false);
        if (dur > 0) {
            String remaining = "-" + fmtTime(Math.max(0, dur - ms));
            int rw = this.font.width(remaining);
            g.drawString(this.font, remaining, x + w / 2 - rw / 2, y, 0xFFE0A000, false);
        }
        double deckBpm = state.getBpm();
        Double tapped = bpm.get(pos);
        double shown = deckBpm > 0 ? deckBpm : (tapped != null ? tapped : 0);
        StringBuilder right = new StringBuilder();
        if (shown > 0) {
            right.append(String.format("%.1f", shown));
        }
        if (state.getKey() != null) {
            var sounding = state.soundingKey();
            if (right.length() > 0) {
                right.append("  ");
            }
            right.append(sounding.camelot());
            if (state.getKeyShift() != 0) {
                right.append(String.format("%+d", state.getKeyShift()));
            }
        }
        if (right.length() > 0) {
            String s = right.toString();
            g.drawString(this.font, s, x + w - this.font.width(s), y, 0xFF35E070, false);
        }
    }

    /** Seconds of track across the zoomed view. Roughly what a CDJ shows at a usable zoom. */
    private static final double ZOOM_SECONDS = 6.0;

    private static final int[] HOT_CUE_COLOURS =
            {0xFF25E0C0, 0xFFF2A900, 0xFFC03AA0, 0xFF2A7BFF};

    private static double clamp01d(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static int argb(int a, int r, int gg, int b) {
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    /**
     * A repeatable pseudo-envelope for one band at a moment in the track.
     *
     * <p>Hashed from the position rather than accumulated, so the same moment always draws the same
     * shape: scrub back and the hills you saw are still there, which is the whole point of looking
     * at a waveform. {@code slotMs} sets how fast that band changes - bass moves slowly, highs
     * flicker.
     */
    private static float band(double atMs, long salt, int slotMs) {
        long slot = (long) Math.floor(atMs / slotMs);
        long h = slot * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        float a = ((h >>> 40) & 0xFFFF) / 65535f;
        // Interpolate between neighbouring slots so the envelope undulates instead of stepping.
        long h2 = (slot + 1) * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h2 ^= h2 >>> 29;
        h2 *= 0xBF58476D1CE4E5B9L;
        h2 ^= h2 >>> 32;
        float b = ((h2 >>> 40) & 0xFFFF) / 65535f;
        double f = (atMs / slotMs) - slot;
        double smooth = f * f * (3 - 2 * f);
        return (float) (0.18 + 0.82 * (a + (b - a) * smooth));
    }

    private static String fmtTime(long ms) {
        return String.format("%d:%02d", ms / 60000, (ms / 1000) % 60);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Only the title; no inventory label (this menu has no slots).
        g.drawString(this.font, this.title, 6, -10, 0xFFFFFFFF, false);
    }
}
