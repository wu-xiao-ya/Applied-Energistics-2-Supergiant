package ae2.tile.misc;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.recipes.handlers.InscriberProcessType;
import ae2.recipes.handlers.InscriberRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class InscriberCraftingPush {
    private InscriberCraftingPush() {
    }

    @Nullable
    static Plan plan(AEProcessingPattern pattern, KeyCounter[] inputs, State state, int parallelLimit,
                     int providedInputMultiplier, int maxMultiplier) {
        if (maxMultiplier <= 0 || state.smash() || pattern.getOutputs().size() != 1) {
            return null;
        }

        List<InscriberInputMatcher.Input<AEKey>> actualInputs = collectInputs(inputs);
        if (actualInputs == null) {
            return null;
        }

        Plan result = null;
        for (InscriberRecipe recipe : InscriberRecipes.getRecipes()) {
            if (!matchesOutput(pattern, recipe)) {
                continue;
            }

            Plan candidate = planRecipe(pattern, recipe, actualInputs, state, parallelLimit, providedInputMultiplier,
                maxMultiplier);
            if (candidate == null) {
                continue;
            }
            if (result != null) {
                return null;
            }
            result = candidate;
        }

        Plan namePressPlan = planNamePressRecipe(pattern, actualInputs, state, parallelLimit,
            providedInputMultiplier, maxMultiplier);
        if (namePressPlan != null) {
            if (result != null) {
                return null;
            }
            result = namePressPlan;
        }
        return result;
    }

    @Nullable
    private static Plan planRecipe(AEProcessingPattern pattern, InscriberRecipe recipe,
                                   List<InscriberInputMatcher.Input<AEKey>> actualInputs,
                                   State state, int parallelLimit, int providedInputMultiplier, int maxMultiplier) {
        if (recipe.getProcessType() == InscriberProcessType.PRESS) {
            if (!hasExactPatternInputAmount(pattern, 3)) {
                return null;
            }
            return planPressRecipe(recipe, actualInputs, state, parallelLimit, providedInputMultiplier, maxMultiplier);
        }
        if (!hasExactPatternInputAmount(pattern, 1)) {
            return null;
        }
        return planInscribeRecipe(recipe, actualInputs, state, parallelLimit, providedInputMultiplier, maxMultiplier);
    }

    @Nullable
    private static Plan planNamePressRecipe(AEProcessingPattern pattern,
                                            List<InscriberInputMatcher.Input<AEKey>> actualInputs,
                                            State state, int parallelLimit, int providedInputMultiplier,
                                            int maxMultiplier) {
        if (!InscriberRecipes.isNamePress(state.top()) && !InscriberRecipes.isNamePress(state.bottom())) {
            return null;
        }
        if (!hasExactPatternInputAmount(pattern, 1)) {
            return null;
        }

        int inputMultiplier = resolveInputMultiplier(actualInputs, 1, providedInputMultiplier);
        if (inputMultiplier <= 0) {
            return null;
        }

        var roles = List.of(new InscriberInputMatcher.Role<AEKey>(
            key -> key instanceof AEItemKey && canInsertInto(state.middle(), key)));
        InscriberInputMatcher.Match<AEKey> match = InscriberInputMatcher.match(roles, actualInputs, inputMultiplier);
        if (match == null) {
            return null;
        }

        ItemStack middleStack = toSingleItemStack(match.assignment(0));
        InscriberRecipe recipe = InscriberRecipes.findRecipe(middleStack, state.top(), state.bottom(), true);
        if (recipe == null || !matchesOutput(pattern, recipe)) {
            return null;
        }

        return finishPlan(recipe, state, parallelLimit, maxMultiplier,
            existingSlotPlan(state.top()),
            consumedSlotPlan(state.middle(), middleStack),
            existingSlotPlan(state.bottom()));
    }

    @Nullable
    private static Plan planInscribeRecipe(InscriberRecipe recipe,
                                           List<InscriberInputMatcher.Input<AEKey>> actualInputs,
                                           State state, int parallelLimit, int providedInputMultiplier,
                                           int maxMultiplier) {
        boolean toolsMatch = optionalIngredientMatches(recipe.getTopOptional(), state.top())
            && optionalIngredientMatches(recipe.getBottomOptional(), state.bottom());
        if (!toolsMatch) {
            toolsMatch = optionalIngredientMatches(recipe.getTopOptional(), state.bottom())
                && optionalIngredientMatches(recipe.getBottomOptional(), state.top());
        }
        if (!toolsMatch) {
            return null;
        }

        int inputMultiplier = resolveInputMultiplier(actualInputs, 1, providedInputMultiplier);
        if (inputMultiplier <= 0) {
            return null;
        }

        var roles = List.of(new InscriberInputMatcher.Role<AEKey>(
            key -> ingredientMatches(recipe.getMiddleInput(), key) && canInsertInto(state.middle(), key)));
        InscriberInputMatcher.Match<AEKey> match = InscriberInputMatcher.match(roles, actualInputs, inputMultiplier);
        if (match == null) {
            return null;
        }

        SlotPlan top = existingSlotPlan(state.top());
        SlotPlan middle = consumedSlotPlan(state.middle(), toSingleItemStack(match.assignment(0)));
        SlotPlan bottom = existingSlotPlan(state.bottom());
        return finishPlan(recipe, state, parallelLimit, maxMultiplier, top, middle, bottom);
    }

    @Nullable
    private static Plan planPressRecipe(InscriberRecipe recipe,
                                        List<InscriberInputMatcher.Input<AEKey>> actualInputs,
                                        State state, int parallelLimit, int providedInputMultiplier,
                                        int maxMultiplier) {
        if (recipe.getTopOptional() == Ingredient.EMPTY || recipe.getBottomOptional() == Ingredient.EMPTY) {
            return null;
        }

        Plan direct = planPressOrientation(recipe, recipe.getTopOptional(), recipe.getBottomOptional(), actualInputs,
            state, parallelLimit, providedInputMultiplier, maxMultiplier);
        if (direct != null) {
            return direct;
        }
        return planPressOrientation(recipe, recipe.getBottomOptional(), recipe.getTopOptional(), actualInputs,
            state, parallelLimit, providedInputMultiplier, maxMultiplier);
    }

    @Nullable
    private static Plan planPressOrientation(InscriberRecipe recipe, Ingredient physicalTop,
                                             Ingredient physicalBottom,
                                             List<InscriberInputMatcher.Input<AEKey>> actualInputs,
                                             State state, int parallelLimit, int providedInputMultiplier,
                                             int maxMultiplier) {
        int inputMultiplier = resolveInputMultiplier(actualInputs, 3, providedInputMultiplier);
        if (inputMultiplier <= 0) {
            return null;
        }

        var roles = List.of(
            new InscriberInputMatcher.Role<AEKey>(
                key -> ingredientMatches(physicalTop, key) && canInsertInto(state.top(), key)),
            new InscriberInputMatcher.Role<AEKey>(
                key -> ingredientMatches(recipe.getMiddleInput(), key) && canInsertInto(state.middle(), key)),
            new InscriberInputMatcher.Role<AEKey>(
                key -> ingredientMatches(physicalBottom, key) && canInsertInto(state.bottom(), key)));
        InscriberInputMatcher.Match<AEKey> match = InscriberInputMatcher.match(roles, actualInputs, inputMultiplier);
        if (match == null) {
            return null;
        }

        SlotPlan top = consumedSlotPlan(state.top(), toSingleItemStack(match.assignment(0)));
        SlotPlan middle = consumedSlotPlan(state.middle(), toSingleItemStack(match.assignment(1)));
        SlotPlan bottom = consumedSlotPlan(state.bottom(), toSingleItemStack(match.assignment(2)));
        return finishPlan(recipe, state, parallelLimit, maxMultiplier, top, middle, bottom);
    }

    @Nullable
    private static Plan finishPlan(InscriberRecipe recipe, State state, int parallelLimit, int maxMultiplier,
                                   SlotPlan top, SlotPlan middle, SlotPlan bottom) {
        if (top == null || middle == null || bottom == null) {
            return null;
        }

        int maxRuns = InscriberPushCapacity.maxRuns(
            maxMultiplier,
            parallelLimit,
            outputRuns(state.output(), recipe.getResultItem()),
            top.availableRuns(state.inputCapacity()),
            middle.availableRuns(state.inputCapacity()),
            bottom.availableRuns(state.inputCapacity()));
        if (maxRuns <= 0) {
            return null;
        }

        return new Plan(recipe, top, middle, bottom, maxRuns);
    }

    private static boolean matchesOutput(AEProcessingPattern pattern, InscriberRecipe recipe) {
        GenericStack output = pattern.getOutputs().getFirst();
        return output.what() instanceof AEItemKey itemKey
            && output.amount() == recipe.getResultItem().getCount()
            && ItemStack.areItemsEqual(itemKey.toStack(), recipe.getResultItem())
            && ItemStack.areItemStackTagsEqual(itemKey.toStack(), recipe.getResultItem());
    }

    @Nullable
    private static SlotPlan consumedSlotPlan(ItemStack current, ItemStack patternStack) {
        if (patternStack.isEmpty()) {
            return null;
        }
        if (!current.isEmpty() && !canStack(current, patternStack)) {
            return null;
        }
        return new SlotPlan(patternStack, true, true, current.isEmpty() ? 0 : current.getCount());
    }

    private static SlotPlan existingSlotPlan(ItemStack current) {
        return current.isEmpty() ? SlotPlan.empty() : new SlotPlan(current, false, false, current.getCount());
    }

    private static int outputRuns(ItemStack currentOutput, ItemStack recipeOutput) {
        if (recipeOutput.isEmpty()) {
            return 0;
        }
        if (currentOutput.isEmpty()) {
            return recipeOutput.getMaxStackSize() / recipeOutput.getCount();
        }
        if (!canStack(currentOutput, recipeOutput)) {
            return 0;
        }
        return (currentOutput.getMaxStackSize() - currentOutput.getCount()) / recipeOutput.getCount();
    }

    @Nullable
    private static List<InscriberInputMatcher.Input<AEKey>> collectInputs(KeyCounter[] inputs) {
        KeyCounter actual = new KeyCounter();
        for (KeyCounter input : inputs) {
            if (input == null) {
                return null;
            }
            actual.addAll(input);
        }

        List<InscriberInputMatcher.Input<AEKey>> result = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : actual) {
            if (entry.getLongValue() < 0) {
                return null;
            }
            if (entry.getLongValue() > 0) {
                result.add(new InscriberInputMatcher.Input<>(entry.getKey(), entry.getLongValue()));
            }
        }
        return result;
    }

    private static int resolveInputMultiplier(List<InscriberInputMatcher.Input<AEKey>> actualInputs,
                                              int inputsPerRecipe, int providedInputMultiplier) {
        if (providedInputMultiplier > 0) {
            return providedInputMultiplier;
        }
        return InscriberInputMatcher.inferMultiplier(actualInputs, inputsPerRecipe);
    }

    private static boolean hasExactPatternInputAmount(AEProcessingPattern pattern, long expectedAmount) {
        long actualAmount = 0;
        for (var input : pattern.getInputs()) {
            long amount = input.getMultiplier();
            if (amount <= 0 || Long.MAX_VALUE - actualAmount < amount) {
                return false;
            }
            actualAmount += amount;
        }
        return actualAmount == expectedAmount;
    }

    private static boolean optionalIngredientMatches(Ingredient ingredient, ItemStack stack) {
        return ingredient == Ingredient.EMPTY ? stack.isEmpty() : ingredient.apply(stack);
    }

    private static boolean ingredientMatches(Ingredient ingredient, AEKey key) {
        return key instanceof AEItemKey itemKey && ingredient.apply(itemKey.toStack());
    }

    private static boolean canInsertInto(ItemStack current, AEKey key) {
        return key instanceof AEItemKey itemKey && (current.isEmpty() || canStack(current, itemKey.toStack()));
    }

    private static boolean canStack(ItemStack current, ItemStack incoming) {
        return ItemStack.areItemsEqual(current, incoming) && ItemStack.areItemStackTagsEqual(current, incoming);
    }

    private static ItemStack toSingleItemStack(AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) {
            return ItemStack.EMPTY;
        }
        return itemKey.toStack();
    }

    record State(ItemStack top, ItemStack middle, ItemStack bottom, ItemStack output, int inputCapacity,
                 boolean smash) {
    }

    record Plan(InscriberRecipe recipe, SlotPlan top, SlotPlan middle, SlotPlan bottom, int maxMultiplier) {
    }

    record SlotPlan(ItemStack stack, boolean consumed, boolean insertRequired, int currentAmount) {
        static SlotPlan empty() {
            return new SlotPlan(ItemStack.EMPTY, false, false, 0);
        }

        int availableRuns(int inputCapacity) {
            if (!this.consumed || this.stack.isEmpty()) {
                return -1;
            }
            return Math.max(0, Math.min(inputCapacity, this.stack.getMaxStackSize()) - this.currentAmount);
        }

        ItemStack stackForRuns(int runs) {
            if (this.stack.isEmpty() || !this.insertRequired) {
                return ItemStack.EMPTY;
            }
            ItemStack result = this.stack.copy();
            result.setCount(runs);
            return result;
        }
    }
}
