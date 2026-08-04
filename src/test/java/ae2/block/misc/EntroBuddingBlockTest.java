package ae2.block.misc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntroBuddingBlockTest {
    @Test
    void fullyEntroizedStageIsStable() {
        assertFalse(EntroBuddingBlock.Stage.FULLY.canDecay());
        assertFalse(EntroBuddingBlock.canDecay(EntroBuddingBlock.Stage.FULLY, false));
    }

    @Test
    void registeredFullyEntroizedBlockIsStableEvenIfStageMetadataIsRedirected() {
        assertFalse(EntroBuddingBlock.canDecay(EntroBuddingBlock.Stage.MOSTLY, true));
    }

    @Test
    void partiallyEntroizedStagesStillDecay() {
        assertTrue(EntroBuddingBlock.Stage.MOSTLY.canDecay());
        assertTrue(EntroBuddingBlock.Stage.HALF.canDecay());
        assertTrue(EntroBuddingBlock.Stage.HARDLY.canDecay());
        assertTrue(EntroBuddingBlock.canDecay(EntroBuddingBlock.Stage.MOSTLY, false));
    }
}
