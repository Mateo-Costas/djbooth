package com.osgworld.djbooth.client.audio.dsp;

import java.nio.ByteBuffer;

/**
 * Hands decoded audio to the output engine through a {@link ChannelStrip}, exactly once per block.
 *
 * <p>The output engine can refuse a block. OpenAL only has a handful of buffers, and a streaming
 * decoder runs well ahead of playback, so "no free buffer" is the normal state, not an accident. When
 * that happens the player does not consume the block: it offers the same one again a moment later.
 *
 * <p>That matters because the chain is stateful. The block used to be filtered in place before being
 * offered, so every refused offer filtered it again - on top of the first pass's result - and the
 * delay lines, filters and limiter advanced once per attempt rather than once per block. The visible
 * symptoms were everything a knob does being applied several times over: a boosted band compounding
 * into a screech, trim running away, a click at each block seam from state that had moved on without
 * the audio.
 *
 * <p>So the block is never modified. It is copied, the copy is filtered, and if the engine turns the
 * copy down it is kept; when the identical block comes round again the kept result is offered
 * instead of running the chain a second time.
 */
public final class BlockRelay {

    /** Where processed audio goes. Returns false when it cannot take the block right now. */
    public interface Sink {
        boolean accept(ByteBuffer block);
    }

    private ByteBuffer scratch = ByteBuffer.allocateDirect(0);
    private ByteBuffer heapIn = ByteBuffer.allocate(0); // reused storage for refusedIn
    // The block the sink last turned down: as it arrived, and as the chain turned it.
    private ByteBuffer refusedIn;
    private ByteBuffer refusedOut;

    /**
     * Offer {@code in} to {@code sink}, filtered through {@code strip}. {@code in} is left untouched.
     *
     * @return what the sink answered; false means the same block should be offered again
     */
    public boolean relay(ByteBuffer in, ChannelStrip strip, Sink sink) {
        if (refusedOut != null) {
            if (isRetry(in)) {
                refusedOut.rewind();
                boolean taken = sink.accept(refusedOut);
                if (taken) {
                    // Nobody holds it any more: take its memory back for the next block, so a sink
                    // that refuses as a matter of course does not allocate on every block.
                    if (refusedOut.capacity() > scratch.capacity()) {
                        scratch = refusedOut;
                    }
                    forget();
                }
                return taken;
            }
            // A different block came instead (the old one was dropped): its result is stale.
            forget();
        }

        int n = in.remaining();
        ByteBuffer out = scratch(n);
        out.put(in.duplicate());
        out.flip();
        strip.processInPlace(out);

        boolean taken = sink.accept(out);
        if (!taken) {
            if (heapIn.capacity() < n) {
                heapIn = ByteBuffer.allocate(n);
            }
            heapIn.clear();
            heapIn.put(in.duplicate());
            heapIn.flip();
            refusedIn = heapIn;
            refusedOut = out;
            scratch = ByteBuffer.allocateDirect(0); // the kept block owns that memory now
        }
        return taken;
    }

    /** Drop any block being held, for a seek or a format change: it belongs to audio that is gone. */
    public void reset() {
        forget();
    }

    private boolean isRetry(ByteBuffer in) {
        return refusedIn != null && in.remaining() == refusedIn.remaining()
                && in.slice().equals(refusedIn.duplicate());
    }

    private void forget() {
        refusedIn = null;
        refusedOut = null;
    }

    private ByteBuffer scratch(int size) {
        if (scratch.capacity() < size) {
            scratch = ByteBuffer.allocateDirect(size);
        }
        scratch.clear();
        scratch.limit(size);
        return scratch;
    }
}
