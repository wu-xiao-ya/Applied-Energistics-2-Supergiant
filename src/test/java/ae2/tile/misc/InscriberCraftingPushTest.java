package ae2.tile.misc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InscriberCraftingPushTest {
    private static final TestKey LOGIC_PRESS = new TestKey("logic_press", "press");
    private static final TestKey GOLD_INGOT = new TestKey("gold_ingot", "ingotGold");
    private static final TestKey OTHER_GOLD_INGOT = new TestKey("other_gold_ingot", "ingotGold");
    private static final TestKey PRINTED_LOGIC = new TestKey("printed_logic", null);
    private static final TestKey REDSTONE = new TestKey("redstone", "dustRedstone");
    private static final TestKey PRINTED_SILICON = new TestKey("printed_silicon", null);
    private static final TestKey SKY_STONE = new TestKey("sky_stone", "stoneSky");
    private static final TestKey EXTRA = new TestKey("extra", null);

    @Test
    void matchesInscribeRecipeWhenToolIsPreinstalledAndOnlyMiddleInputIsProvided() {
        var match = InscriberInputMatcher.match(
            List.of(roleWithOre("ingotGold")),
            List.of(input(GOLD_INGOT, 1)),
            1);

        assertNotNull(match);
        assertEquals(GOLD_INGOT, match.assignment(0));
    }

    @Test
    void matchesToollessCrushingRecipeWithOnlyMiddleInput() {
        var match = InscriberInputMatcher.match(
            List.of(roleWithOre("stoneSky")),
            List.of(input(SKY_STONE, 1)),
            1);

        assertNotNull(match);
        assertEquals(SKY_STONE, match.assignment(0));
    }

    @Test
    void matchesAllThreeConsumedPressInputsRegardlessOfInputOrder() {
        var roles = List.of(
            roleWithId("printed_logic"),
            roleWithOre("dustRedstone"),
            roleWithId("printed_silicon"));

        var match = InscriberInputMatcher.match(
            roles,
            List.of(input(PRINTED_SILICON, 1), input(PRINTED_LOGIC, 1), input(REDSTONE, 1)),
            1);

        assertNotNull(match);
        assertEquals(List.of(PRINTED_LOGIC, REDSTONE, PRINTED_SILICON), match.assignments());
    }

    @Test
    void acceptsOreDictionaryCandidateInsteadOfEncodedRepresentative() {
        var match = InscriberInputMatcher.match(
            List.of(roleWithOre("ingotGold")),
            List.of(input(OTHER_GOLD_INGOT, 1)),
            1);

        assertNotNull(match);
        assertEquals(OTHER_GOLD_INGOT, match.assignment(0));
    }

    @Test
    void validatesMergedInputAmountsForFourSixteenAndSixtyFourBatches() {
        var roles = List.of(
            roleWithId("printed_logic"),
            roleWithOre("dustRedstone"),
            roleWithId("printed_silicon"));

        for (int multiplier : List.of(4, 16, 64)) {
            var match = InscriberInputMatcher.match(
                roles,
                List.of(
                    input(PRINTED_LOGIC, multiplier),
                    input(REDSTONE, multiplier),
                    input(PRINTED_SILICON, multiplier)),
                multiplier);

            assertNotNull(match);
            assertEquals(List.of(PRINTED_LOGIC, REDSTONE, PRINTED_SILICON), match.assignments());
        }
    }

    @Test
    void canUseTheSameKeyForMultipleConsumedRoles() {
        var match = InscriberInputMatcher.match(
            List.of(roleWithOre("ingotGold"), roleWithOre("ingotGold")),
            List.of(input(GOLD_INGOT, 32)),
            16);

        assertNotNull(match);
        assertEquals(List.of(GOLD_INGOT, GOLD_INGOT), match.assignments());
    }

    @Test
    void combinesDuplicateInputCountersBeforeMatching() {
        var match = InscriberInputMatcher.match(
            List.of(roleWithOre("ingotGold")),
            List.of(input(GOLD_INGOT, 7), input(GOLD_INGOT, 9)),
            16);

        assertNotNull(match);
        assertEquals(GOLD_INGOT, match.assignment(0));
    }

    @Test
    void infersDefinitionAndMergedPushMultipliersFromActualInputs() {
        assertEquals(1, InscriberInputMatcher.inferMultiplier(List.of(input(GOLD_INGOT, 1)), 1));
        assertEquals(16, InscriberInputMatcher.inferMultiplier(
            List.of(input(PRINTED_LOGIC, 16), input(REDSTONE, 16), input(PRINTED_SILICON, 16)),
            3));
        assertEquals(0, InscriberInputMatcher.inferMultiplier(List.of(input(GOLD_INGOT, 2)), 3));
    }

    @Test
    void rejectsMissingExtraAndWrongInputs() {
        var role = List.of(roleWithOre("ingotGold"));

        assertNull(InscriberInputMatcher.match(role, List.of(), 1));
        assertNull(InscriberInputMatcher.match(role, List.of(input(GOLD_INGOT, 1), input(EXTRA, 1)), 1));
        assertNull(InscriberInputMatcher.match(role, List.of(input(REDSTONE, 1)), 1));
        assertNull(InscriberInputMatcher.match(role, List.of(input(GOLD_INGOT, 3)), 4));
    }

    @Test
    void rejectsInputsThatCannotBeAssignedToRolesUniquely() {
        var roles = List.of(roleWithOre("ingotGold"), roleWithOre("ingotGold"));

        assertNull(InscriberInputMatcher.match(
            roles,
            List.of(input(GOLD_INGOT, 1), input(OTHER_GOLD_INGOT, 1)),
            1));
    }

    @Test
    void rejectsToolIncludedInAnInscribePattern() {
        assertNull(InscriberInputMatcher.match(
            List.of(roleWithOre("ingotGold")),
            List.of(input(LOGIC_PRESS, 1), input(GOLD_INGOT, 1)),
            1));
    }

    @Test
    void limitsBatchSizeByParallelInputAndOutputCapacity() {
        assertEquals(4, InscriberPushCapacity.maxRuns(64, 4, 64, 64, 64, 64));
        assertEquals(16, InscriberPushCapacity.maxRuns(64, 64, 64, 16, 64, 64));
        assertEquals(3, InscriberPushCapacity.maxRuns(64, 64, 3, 64, 64, 64));
        assertEquals(64, InscriberPushCapacity.maxRuns(64, 64, 64, -1, 64, -1));
        assertEquals(0, InscriberPushCapacity.maxRuns(64, 64, 64, 0));
    }

    private static InscriberInputMatcher.Role<TestKey> roleWithId(String id) {
        return new InscriberInputMatcher.Role<>(key -> key.id().equals(id));
    }

    private static InscriberInputMatcher.Role<TestKey> roleWithOre(String ore) {
        return new InscriberInputMatcher.Role<>(key -> ore.equals(key.ore()));
    }

    private static InscriberInputMatcher.Input<TestKey> input(TestKey key, long amount) {
        return new InscriberInputMatcher.Input<>(key, amount);
    }

    private record TestKey(String id, String ore) {
    }
}
