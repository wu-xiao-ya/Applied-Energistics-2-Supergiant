package ae2.block.misc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntroBuddingBlockTest {
    @Test
    void fullyEntroizedStageIsStable() {
        assertFalse(EntroBuddingBlock.Stage.FULLY.canDecay());
    }

    @Test
    void partiallyEntroizedStagesStillDecay() {
        assertTrue(EntroBuddingBlock.Stage.MOSTLY.canDecay());
        assertTrue(EntroBuddingBlock.Stage.HALF.canDecay());
        assertTrue(EntroBuddingBlock.Stage.HARDLY.canDecay());
    }
}
