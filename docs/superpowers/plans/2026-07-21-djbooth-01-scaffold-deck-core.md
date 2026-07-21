# DJ Booth — Plan 01: Scaffold + Deck Core

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a loadable NeoForge 1.21.1 mod `djbooth` with two placeable blocks (CDJ deck, mixer), their block entities holding transport/mix state, an openable CDJ GUI with working transport (play/cue/pause/loop), tempo, and jog nudge — with client-simulated deck position but **no audio yet**.

**Architecture:** Server-authoritative `CdjBlockEntity` holds transport state (playState, startEpochMs, offsetMs, rate, cue/loop). GUI sends C2S payloads; server mutates BE + marks it dirty; BE syncs to clients via update tag + a live `DeckStatePayload`. Client computes expected playback position from BE state each frame (foundation for audio in Plan 02).

**Tech Stack:** Java 21, NeoForge 21.1.235, NeoGradle, MC 1.21.1. GameTest for BE-state logic; `runClient` manual checklist for GUI.

---

## Notes for the engineer (read once)

- Minecraft mods can't be TDD'd like a plain library — the game must bootstrap. We therefore test **pure logic** (position math, state transitions) as plain JUnit where possible, test BE behavior with **NeoForge GameTest**, and verify GUI/rendering manually via `./gradlew runClient` using the checklists provided.
- All Gradle commands are run from `C:\Users\Mateo\djbooth`. On Windows use `./gradlew` in Git Bash or `gradlew.bat` in PowerShell.
- Registry/API names are for NeoForge **21.1.x**. If a symbol differs in 21.1.235's MDK, prefer the MDK's version and adjust — do not invent APIs.
- Package root: `com.osgworld.djbooth`. Mod id constant lives in `DJBooth.MODID = "djbooth"`.

---

## File structure (locked here)

```
djbooth/
├─ build.gradle, settings.gradle, gradle.properties, gradlew(.bat)
├─ src/main/java/com/osgworld/djbooth/
│  ├─ DJBooth.java                         mod entry, MODID, bus wiring
│  ├─ registry/
│  │  ├─ ModBlocks.java                    DeferredRegister blocks
│  │  ├─ ModItems.java                     block items
│  │  ├─ ModBlockEntities.java             BE types
│  │  ├─ ModMenus.java                     menu types
│  │  ├─ ModPayloads.java                  payload registration
│  │  └─ ModCreativeTabs.java              creative tab
│  ├─ block/
│  │  ├─ CdjBlock.java                     deck block (horizontal facing)
│  │  └─ MixerBlock.java                   mixer block (horizontal facing)
│  ├─ blockentity/
│  │  ├─ CdjBlockEntity.java               deck transport state
│  │  └─ MixerBlockEntity.java             mix state (stub in this plan)
│  ├─ deck/
│  │  ├─ PlayState.java                    enum STOP/CUE/PLAY/PAUSE
│  │  └─ DeckState.java                    plain value object + position math
│  ├─ menu/
│  │  └─ CdjMenu.java                      AbstractContainerMenu for CDJ
│  ├─ client/
│  │  ├─ DJBoothClient.java                client init (menu screens)
│  │  └─ screen/CdjScreen.java             CDJ GUI screen + widgets
│  └─ net/
│     ├─ TransportPayload.java             C2S transport actions
│     ├─ JogNudgePayload.java              C2S jog nudge
│     └─ DeckStatePayload.java             S2C live deck state
├─ src/main/resources/
│  ├─ META-INF/neoforge.mods.toml
│  ├─ pack.mcmeta
│  └─ assets/djbooth/...                   models, blockstates, lang, textures
└─ src/test/java/com/osgworld/djbooth/
   ├─ DeckStateTest.java                   JUnit: position math + transitions
   └─ CdjGameTests.java                    GameTest: place block, mutate BE
```

---

## Task 1: Gradle project + NeoForge MDK boots

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- Create: `src/main/resources/META-INF/neoforge.mods.toml`, `src/main/resources/pack.mcmeta`
- Create: `src/main/java/com/osgworld/djbooth/DJBooth.java`

- [ ] **Step 1: Fetch the NeoForge 1.21.1 MDK**

Run:
```bash
cd /c/Users/Mateo/djbooth
curl -L -o mdk.zip "https://github.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle/archive/refs/heads/main.zip"
unzip -o mdk.zip -d mdk-tmp
cp -r mdk-tmp/*/. .
rm -rf mdk-tmp mdk.zip
```
Expected: `build.gradle`, `gradlew`, `gradle/` now present. (If the URL 404s, get the MDK from https://neoforged.net/ "Get Started" → 1.21.1 MDK zip, and unzip into the project root.)

- [ ] **Step 2: Pin versions in `gradle.properties`**

Set/confirm:
```properties
minecraft_version=1.21.1
neo_version=21.1.235
mod_id=djbooth
mod_name=DJ Booth
mod_group_id=com.osgworld.djbooth
mod_version=0.1.0
```

- [ ] **Step 3: Replace example mod class with `DJBooth.java`**

Delete the MDK's example mod class(es) under `src/main/java`. Create `src/main/java/com/osgworld/djbooth/DJBooth.java`:
```java
package com.osgworld.djbooth;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import com.osgworld.djbooth.registry.*;

@Mod(DJBooth.MODID)
public final class DJBooth {
    public static final String MODID = "djbooth";

    public DJBooth(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(ModPayloads::register);
    }
}
```

- [ ] **Step 4: Point `neoforge.mods.toml` at the mod class**

Edit `src/main/resources/META-INF/neoforge.mods.toml`: set `modId="djbooth"`, `displayName="DJ Booth"`, version `${mod_version}`, and add soft-dep entries:
```toml
[[dependencies.djbooth]]
modId="neoforge"
type="required"
versionRange="[21.1.0,)"
ordering="NONE"
side="BOTH"

[[dependencies.djbooth]]
modId="watermedia"
type="optional"
versionRange="*"
ordering="AFTER"
side="BOTH"

[[dependencies.djbooth]]
modId="minedmx"
type="optional"
versionRange="*"
ordering="AFTER"
side="BOTH"
```

- [ ] **Step 5: Create empty registry classes so it compiles**

Create each of `registry/ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java`, `ModMenus.java`, `ModPayloads.java`, `ModCreativeTabs.java` with a `public static void register(IEventBus bus) {}` (ModPayloads instead: `public static void register(RegisterPayloadHandlersEvent e) {}`). Fill in later tasks.

- [ ] **Step 6: Build to verify the toolchain**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. First run downloads NeoForge — may take several minutes.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore: scaffold NeoForge 1.21.1 djbooth mod (boots + builds)"
```

---

## Task 2: DeckState value object + position math (pure JUnit)

This is the one piece that is cleanly unit-testable; the deck's whole feel depends on it, so it's isolated from Minecraft.

**Files:**
- Create: `src/main/java/com/osgworld/djbooth/deck/PlayState.java`
- Create: `src/main/java/com/osgworld/djbooth/deck/DeckState.java`
- Test: `src/test/java/com/osgworld/djbooth/DeckStateTest.java`

- [ ] **Step 1: Write the failing test**

`DeckStateTest.java`:
```java
package com.osgworld.djbooth;

import com.osgworld.djbooth.deck.DeckState;
import com.osgworld.djbooth.deck.PlayState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeckStateTest {

    @Test
    void stoppedDeckReportsCuePosition() {
        DeckState s = new DeckState();
        s.setPlayState(PlayState.CUE);
        s.setCuePointMs(4000);
        assertEquals(4000, s.positionMsAt(999999));
    }

    @Test
    void playingDeckAdvancesWithTimeAndRate() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, /*now*/1000);   // starts at offset 0
        assertEquals(0, s.positionMsAt(1000));
        assertEquals(500, s.positionMsAt(1500));      // rate 1.0
        s.setRate(2.0);
        s.press(PlayState.PLAY, 2000);                // re-anchor at current pos
        assertEquals(1000 + 200, s.positionMsAt(2100)); // (2100-2000)*2 + 1000
    }

    @Test
    void pauseFreezesPosition() {
        DeckState s = new DeckState();
        s.press(PlayState.PLAY, 0);
        long p = s.positionMsAt(3000);
        s.press(PlayState.PAUSE, 3000);
        assertEquals(p, s.positionMsAt(9999));
    }

    @Test
    void loopWrapsWithinRegion() {
        DeckState s = new DeckState();
        s.setLoop(1000, 2000, true);
        s.press(PlayState.PLAY, 0);
        // at t=2500 raw pos would be 2500; wrapped into [1000,2000): 1000 + (2500-1000)%1000 = 1500
        assertEquals(1500, s.positionMsAt(2500));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.osgworld.djbooth.DeckStateTest"`
Expected: FAIL — `DeckState` / `PlayState` do not exist.

- [ ] **Step 3: Implement `PlayState` and `DeckState`**

`PlayState.java`:
```java
package com.osgworld.djbooth.deck;

public enum PlayState { STOP, CUE, PLAY, PAUSE }
```

`DeckState.java`:
```java
package com.osgworld.djbooth.deck;

/** Pure transport model. No Minecraft dependencies. Milliseconds throughout. */
public final class DeckState {
    private PlayState playState = PlayState.STOP;
    private long startEpochMs = 0;   // when PLAY began
    private long offsetMs = 0;       // position at startEpochMs (or frozen pos)
    private double rate = 1.0;       // tempo multiplier
    private long cuePointMs = 0;
    private boolean loopOn = false;
    private long loopInMs = 0, loopOutMs = 0;
    private String trackUrl = "";

    public PlayState getPlayState() { return playState; }
    public double getRate() { return rate; }
    public void setRate(double r) { this.rate = Math.max(0.01, r); }
    public long getCuePointMs() { return cuePointMs; }
    public void setCuePointMs(long ms) { this.cuePointMs = Math.max(0, ms); }
    public String getTrackUrl() { return trackUrl; }
    public void setTrackUrl(String u) { this.trackUrl = u == null ? "" : u; }

    public void setPlayState(PlayState s) { this.playState = s; }

    public void setLoop(long inMs, long outMs, boolean on) {
        this.loopInMs = Math.min(inMs, outMs);
        this.loopOutMs = Math.max(inMs, outMs);
        this.loopOn = on && this.loopOutMs > this.loopInMs;
    }

    /** Transition; re-anchors position math to `now`. */
    public void press(PlayState next, long now) {
        long current = positionMsAt(now);
        switch (next) {
            case PLAY -> { offsetMs = current; startEpochMs = now; }
            case PAUSE -> { offsetMs = current; }
            case CUE -> { offsetMs = cuePointMs; }
            case STOP -> { offsetMs = 0; }
        }
        playState = next;
    }

    /** Expected playback position at wall-clock `nowMs`. */
    public long positionMsAt(long nowMs) {
        long raw = (playState == PlayState.PLAY)
                ? offsetMs + Math.round((nowMs - startEpochMs) * rate)
                : offsetMs;
        if (loopOn && raw >= loopOutMs) {
            long span = loopOutMs - loopInMs;
            raw = loopInMs + Math.floorMod(raw - loopInMs, span);
        }
        return Math.max(0, raw);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.osgworld.djbooth.DeckStateTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/osgworld/djbooth/deck src/test/java/com/osgworld/djbooth/DeckStateTest.java
git commit -m "feat: DeckState transport model with position/rate/loop math + tests"
```

---

## Task 3: Register CDJ + Mixer blocks and block items

**Files:**
- Create: `block/CdjBlock.java`, `block/MixerBlock.java`
- Modify: `registry/ModBlocks.java`, `registry/ModItems.java`, `registry/ModCreativeTabs.java`
- Create: blockstate/model/lang assets under `src/main/resources/assets/djbooth/`

- [ ] **Step 1: Implement `CdjBlock` (horizontal facing, has BE)**

`CdjBlock.java`:
```java
package com.osgworld.djbooth.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import com.osgworld.djbooth.blockentity.CdjBlockEntity;

public class CdjBlock extends BaseEntityBlock {
    public static final MapCodec<CdjBlock> CODEC = simpleCodec(CdjBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CdjBlock(Properties p) { super(p); registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return new CdjBlockEntity(pos, st);
    }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    // useWithoutItem override (open GUI) added in Task 7.
}
```

`MixerBlock.java`: identical shape but `new MixerBlockEntity(...)` and its own `CODEC`.

- [ ] **Step 2: Register blocks + items in `ModBlocks` / `ModItems`**

`ModBlocks.java`:
```java
package com.osgworld.djbooth.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.block.*;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DJBooth.MODID);

    public static final DeferredBlock<CdjBlock> CDJ = BLOCKS.register("cdj",
        () -> new CdjBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).noOcclusion()));
    public static final DeferredBlock<MixerBlock> MIXER = BLOCKS.register("mixer",
        () -> new MixerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).noOcclusion()));

    public static void register(IEventBus bus) { BLOCKS.register(bus); }
}
```

`ModItems.java`: create `DeferredRegister.Items ITEMS`, register `cdj`/`mixer` block items via `ITEMS.registerSimpleBlockItem(ModBlocks.CDJ)` etc., call `ITEMS.register(bus)`.

- [ ] **Step 3: Creative tab**

`ModCreativeTabs.java`: `DeferredRegister<CreativeModeTab>` on `Registries.CREATIVE_MODE_TAB`, register tab `"djbooth"` with icon `ModBlocks.CDJ`, `displayItems` adding cdj + mixer.

- [ ] **Step 4: Assets — blockstate, model, item model, lang**

Create (functional placeholders; polish later):
- `assets/djbooth/blockstates/cdj.json` — a `variants` block keyed by `facing=` mapping to `djbooth:block/cdj` with `y` rotation.
- `assets/djbooth/models/block/cdj.json` — `{ "parent": "block/cube_all", "textures": { "all": "djbooth:block/cdj" } }` (placeholder texture; a 16×16 dark-grey PNG at `assets/djbooth/textures/block/cdj.png`).
- `assets/djbooth/models/item/cdj.json` — `{ "parent": "djbooth:block/cdj" }`.
- Same four for `mixer`.
- `assets/djbooth/lang/en_us.json` and `es_es.json` with `block.djbooth.cdj`, `block.djbooth.mixer`, `itemGroup.djbooth`.

- [ ] **Step 5: Verify in dev client**

Run: `./gradlew runClient`
Manual check: creative menu shows "DJ Booth" tab with CDJ + Mixer; both place, show facing rotation, break, drop item.
Expected: no crash, blocks behave.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: register CDJ + Mixer blocks, items, creative tab, placeholder assets"
```

---

## Task 4: Block entities + NBT persistence + client sync

**Files:**
- Create: `blockentity/CdjBlockEntity.java`, `blockentity/MixerBlockEntity.java`
- Modify: `registry/ModBlockEntities.java`

- [ ] **Step 1: `CdjBlockEntity` wrapping a `DeckState`**

`CdjBlockEntity.java`:
```java
package com.osgworld.djbooth.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.osgworld.djbooth.deck.DeckState;
import com.osgworld.djbooth.deck.PlayState;
import com.osgworld.djbooth.registry.ModBlockEntities;

public class CdjBlockEntity extends BlockEntity {
    private final DeckState state = new DeckState();

    public CdjBlockEntity(BlockPos pos, BlockState st) { super(ModBlockEntities.CDJ.get(), pos, st); }

    public DeckState state() { return state; }

    /** Server-side mutation entry point; persists + syncs. */
    public void applyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.putString("Url", state.getTrackUrl());
        tag.putString("Play", state.getPlayState().name());
        tag.putDouble("Rate", state.getRate());
        tag.putLong("Cue", state.getCuePointMs());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        state.setTrackUrl(tag.getString("Url"));
        state.setPlayState(PlayState.valueOf(tag.getString("Play").isEmpty() ? "STOP" : tag.getString("Play")));
        state.setRate(tag.getDouble("Rate") == 0 ? 1.0 : tag.getDouble("Rate"));
        state.setCuePointMs(tag.getLong("Cue"));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        CompoundTag t = new CompoundTag(); saveAdditional(t, r); return t;
    }
    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
```
`MixerBlockEntity.java`: minimal stub for this plan — extends `BlockEntity`, holds `float crossfader=0.5f, faderA=1, faderB=1, master=1;` with save/load. Full mix logic lands in Plan 03.

- [ ] **Step 2: Register BE types**

`ModBlockEntities.java`:
```java
package com.osgworld.djbooth.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.blockentity.*;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BE =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DJBooth.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CdjBlockEntity>> CDJ =
        BE.register("cdj", () -> BlockEntityType.Builder.of(CdjBlockEntity::new, ModBlocks.CDJ.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MixerBlockEntity>> MIXER =
        BE.register("mixer", () -> BlockEntityType.Builder.of(MixerBlockEntity::new, ModBlocks.MIXER.get()).build(null));

    public static void register(IEventBus bus) { BE.register(bus); }
}
```

- [ ] **Step 3: GameTest — BE persists a mutation across save/load**

`src/test/java/com/osgworld/djbooth/CdjGameTests.java`:
```java
package com.osgworld.djbooth;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.deck.PlayState;
import com.osgworld.djbooth.registry.ModBlocks;

@GameTestHolder(DJBooth.MODID)
public class CdjGameTests {
    @GameTest
    public void cdjStoresRate(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, ModBlocks.CDJ.get());
        CdjBlockEntity be = (CdjBlockEntity) h.getBlockEntity(p);
        be.state().setRate(1.25);
        be.applyAndSync();
        h.assertTrue(Math.abs(be.state().getRate() - 1.25) < 1e-6, "rate not stored");
        h.succeed();
    }
}
```
Enable GameTest run: add the `runGameTestServer`/`enabledGameTestNamespaces` config per the MDK (`build.gradle` runs block).

- [ ] **Step 4: Run the GameTest**

Run: `./gradlew runGameTestServer`
Expected: `cdjStoresRate` passes (green in log).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: CDJ/Mixer block entities with NBT persistence + client sync + gametest"
```

---

## Task 5: Networking — payload types + registration

**Files:**
- Create: `net/TransportPayload.java`, `net/JogNudgePayload.java`, `net/DeckStatePayload.java`
- Modify: `registry/ModPayloads.java`

- [ ] **Step 1: `TransportPayload` (C2S: pos + action)**

`TransportPayload.java`:
```java
package com.osgworld.djbooth.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.osgworld.djbooth.DJBooth;

public record TransportPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int PLAY=0, PAUSE=1, CUE=2, SET_CUE=3, LOOP_TOGGLE=4;
    public static final Type<TransportPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "transport"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransportPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, TransportPayload::pos,
        ByteBufCodecs.VAR_INT, TransportPayload::action,
        TransportPayload::new);
    @Override public Type<TransportPayload> type() { return TYPE; }
}
```

- [ ] **Step 2: `JogNudgePayload` (C2S: pos + deltaMs or rateDelta)** and **`DeckStatePayload` (S2C: pos + playState ordinal + rate + offsetMs + startEpochMs + url)**

Follow the same record+Type+StreamCodec pattern. `DeckStatePayload` fields: `BlockPos pos, int playState, double rate, long offsetMs, long startEpochMs, String url`. `JogNudgePayload`: `BlockPos pos, double rateDelta, long scrubToMs` (scrubToMs = -1 when only nudging).

- [ ] **Step 3: Register + handlers**

`ModPayloads.java`:
```java
package com.osgworld.djbooth.registry;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import com.osgworld.djbooth.net.*;
import com.osgworld.djbooth.net.handler.*;

public final class ModPayloads {
    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToServer(TransportPayload.TYPE, TransportPayload.CODEC, ServerTransportHandler::handle);
        r.playToServer(JogNudgePayload.TYPE, JogNudgePayload.CODEC, ServerJogHandler::handle);
        r.playToClient(DeckStatePayload.TYPE, DeckStatePayload.CODEC, ClientDeckStateHandler::handle);
    }
}
```

- [ ] **Step 4: Server handlers mutate the BE**

Create `net/handler/ServerTransportHandler.java`:
```java
package com.osgworld.djbooth.net.handler;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;
import com.osgworld.djbooth.net.TransportPayload;
import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.deck.PlayState;

public final class ServerTransportHandler {
    public static void handle(TransportPayload m, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!sp.level().isLoaded(m.pos())) return;
            if (sp.distanceToSqr(m.pos().getCenter()) > 64) return; // range guard
            if (!(sp.level().getBlockEntity(m.pos()) instanceof CdjBlockEntity be)) return;
            long now = sp.level().getGameTime() * 50L; // ms
            switch (m.action()) {
                case TransportPayload.PLAY  -> be.state().press(PlayState.PLAY, now);
                case TransportPayload.PAUSE -> be.state().press(PlayState.PAUSE, now);
                case TransportPayload.CUE   -> be.state().press(PlayState.CUE, now);
                case TransportPayload.SET_CUE -> be.state().setCuePointMs(be.state().positionMsAt(now));
                default -> {}
            }
            be.applyAndSync();
        });
    }
}
```
Create `ServerJogHandler` (applies rateDelta/scrub, range-guarded, same shape) and `ClientDeckStateHandler` (writes incoming state into the client BE's `DeckState`).

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: transport/jog/deckstate payloads + server range-guarded handlers"
```

---

## Task 6: CDJ Menu + Screen with working transport

**Files:**
- Create: `menu/CdjMenu.java`, `client/screen/CdjScreen.java`, `client/DJBoothClient.java`
- Modify: `registry/ModMenus.java`, `block/CdjBlock.java` (open GUI on use)

- [ ] **Step 1: `CdjMenu`**

`CdjMenu.java`: extends `AbstractContainerMenu`. Constructor `(int id, Inventory inv, BlockPos pos)`; client ctor decodes pos from `RegistryFriendlyByteBuf`. Holds a reference to the `CdjBlockEntity` (resolved via `inv.player.level().getBlockEntity(pos)`). `stillValid` = player within 8 blocks. No item slots. Register `MenuType` via `IMenuTypeExtension.create((id,inv,buf)-> new CdjMenu(id, inv, buf.readBlockPos()))`.

- [ ] **Step 2: Register menu in `ModMenus`**

```java
public static final DeferredHolder<MenuType<?>, MenuType<CdjMenu>> CDJ =
    MENUS.register("cdj", () -> IMenuTypeExtension.create(
        (id, inv, buf) -> new CdjMenu(id, inv, buf.readBlockPos())));
```
(DeferredRegister on `Registries.MENU`.)

- [ ] **Step 3: Open GUI from the block**

In `CdjBlock`, override `useWithoutItem`:
```java
@Override protected InteractionResult useWithoutItem(BlockState st, Level lvl, BlockPos pos,
        Player player, BlockHitResult hit) {
    if (!lvl.isClientSide && player instanceof ServerPlayer sp
            && lvl.getBlockEntity(pos) instanceof CdjBlockEntity be) {
        sp.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new CdjMenu(id, inv, pos),
            Component.translatable("block.djbooth.cdj")), buf -> buf.writeBlockPos(pos));
    }
    return InteractionResult.sSUCCESS;
}
```
(Confirm the exact `InteractionResult` constant name in 21.1.235; use its `SUCCESS`.)

- [ ] **Step 4: `CdjScreen` with buttons + jog + tempo**

`CdjScreen.java` extends `AbstractContainerScreen<CdjMenu>`. Widgets:
- Play, Cue, (Set-Cue), Loop buttons → each sends `TransportPayload` via `PacketDistributor.sendToServer(new TransportPayload(pos, action))`.
- Tempo slider (`AbstractSliderButton`) → sends `JogNudgePayload` with the new absolute rate encoded as rateDelta from current, or add a `SetRatePayload` if cleaner.
- Jog wheel: a custom widget; drag angle delta → `JogNudgePayload(pos, rateDelta, -1)` while held; release resets rate toward 1.0. Scratch fidelity is intentionally coarse (see spec §1).
- Position readout: reads client BE `DeckState.positionMsAt(clientNowMs)` each frame, renders `mm:ss`.

Register the screen in `DJBoothClient` (`@EventBusSubscriber(Dist.CLIENT)` on `RegisterMenuScreensEvent` → `event.register(ModMenus.CDJ.get(), CdjScreen::new)`).

- [ ] **Step 5: Manual verification in dev client**

Run: `./gradlew runClient`
Checklist:
1. Right-click CDJ → screen opens.
2. Press Play → position readout counts up; Pause → freezes; Cue → jumps to cue; Set-Cue → new cue.
3. Tempo slider changes count-up speed.
4. Jog drag nudges the readout.
5. Reopen screen / relog → state persists (from BE).
Expected: all pass, no crash. (No audio yet — that's Plan 02.)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: CDJ menu + screen with working transport, tempo, jog (no audio yet)"
```

---

## Self-review notes (already reconciled)
- Spec §3 deck fields ↔ `DeckState`/`CdjBlockEntity` NBT keys: url, playState, rate, cue covered; loop/offset live in `DeckState` (synced via `DeckStatePayload`), persisted subset in NBT — acceptable for v1 (loop is a live performance value).
- Spec §5 payload system ↔ Task 5 uses `RegisterPayloadHandlersEvent`/`PayloadRegistrar`/`StreamCodec`. ✔
- Spec §6 GUI ↔ Task 6 (jog/tempo/transport/readout). Faders/crossfader/DMX panel belong to the Mixer screen → **Plan 03**. ✔
- Audio (`AudioBackend`/WaterMedia) intentionally deferred → **Plan 02**. Position math built here is its input.
- Method names checked: `press`, `positionMsAt`, `applyAndSync`, `state()` consistent across tasks.

## What this plan deliberately does NOT do (later plans)
- **Plan 02 — Audio:** `AudioBackend` iface + `WaterMediaBackend`, client player per deck, drift-seek to `positionMsAt`, nudge/scrub → real sound. Soft-dep gating.
- **Plan 03 — Mixer + linking:** Mixer GUI (faders/crossfader/master), deck binding by BlockPos, mixing math → per-deck volume.
- **Plan 04 — DMX:** inspect MineDMX jar, `DmxBridge`/`MineDmxBridge`, DMX-map panel, BPM-tap light pulse.
- **Plan 05 — FX + polish:** 1–2 FX, SFX pack, real models/textures, datagen, recipes, degradation tests, singleplayer sign-off.
