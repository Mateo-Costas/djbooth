package com.osgworld.djbooth.booth;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoothRefsTest {

    private static BlockPos at(int x, int z) {
        return new BlockPos(x, 64, z);
    }

    @Test
    void aLoneMixerAndTwoDecksFormABooth() {
        BoothRefs r = BoothRefs.choose(at(0, 0), List.of(at(0, 0)), List.of(at(-2, 0), at(2, 0)));
        assertEquals(at(0, 0), r.mixer());
        assertEquals(at(-2, 0), r.deckA());
        assertEquals(at(2, 0), r.deckB());
    }

    @Test
    void theNearestMixerWinsNotTheFirstFound() {
        // Two booths side by side. Anchoring on the east one must not pick up the west mixer, which
        // is the one a scan in coordinate order would meet first.
        BlockPos westMixer = at(-5, 0);
        BlockPos eastMixer = at(4, 0);
        BoothRefs r = BoothRefs.choose(at(4, 1), List.of(westMixer, eastMixer), List.of());
        assertEquals(eastMixer, r.mixer());
    }

    @Test
    void withMoreThanTwoDecksTheClosestTwoAreUsed() {
        BlockPos near1 = at(1, 0), near2 = at(-1, 0), far = at(6, 0);
        BoothRefs r = BoothRefs.choose(at(0, 0), List.of(), List.of(far, near1, near2));
        assertEquals(near2, r.deckA());
        assertEquals(near1, r.deckB());
    }

    @Test
    void decksKeepTheirSideWhicheverBlockWasClicked() {
        List<BlockPos> decks = List.of(at(-2, 0), at(2, 0));
        BoothRefs fromWest = BoothRefs.choose(at(-2, 0), List.of(), decks);
        BoothRefs fromEast = BoothRefs.choose(at(2, 0), List.of(), decks);
        assertEquals(fromWest.deckA(), fromEast.deckA());
        assertEquals(fromWest.deckB(), fromEast.deckB());
    }

    @Test
    void missingPartsAreNull() {
        BoothRefs none = BoothRefs.choose(at(0, 0), List.of(), List.of());
        assertNull(none.mixer());
        assertNull(none.deckA());
        assertNull(none.deckB());

        BoothRefs one = BoothRefs.choose(at(0, 0), List.of(), List.of(at(1, 1)));
        assertEquals(at(1, 1), one.deckA());
        assertNull(one.deckB());
    }
}
