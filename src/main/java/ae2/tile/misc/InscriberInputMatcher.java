package ae2.tile.misc;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

final class InscriberInputMatcher {
    private InscriberInputMatcher() {
    }

    @Nullable
    static <T> Match<T> match(List<Role<T>> roles, List<Input<T>> inputs, long multiplier) {
        if (multiplier <= 0) {
            return null;
        }

        Map<T, Long> combinedInputs = new LinkedHashMap<>();
        for (Input<T> input : inputs) {
            if (input.amount() < 0) {
                return null;
            }
            if (input.amount() == 0) {
                continue;
            }

            long existingAmount = combinedInputs.getOrDefault(input.key(), 0L);
            if (Long.MAX_VALUE - existingAmount < input.amount()) {
                return null;
            }
            combinedInputs.put(input.key(), existingAmount + input.amount());
        }

        List<T> keys = new ArrayList<>(combinedInputs.keySet());
        long[] remaining = new long[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            remaining[i] = combinedInputs.get(keys.get(i));
        }

        Search<T> search = new Search<>(roles, keys, remaining, multiplier);
        search.find(0);
        return search.ambiguous || search.solution == null ? null : new Match<>(search.solution);
    }

    static <T> int inferMultiplier(List<Input<T>> inputs, int inputsPerRecipe) {
        if (inputsPerRecipe <= 0) {
            return 0;
        }

        long total = 0;
        for (Input<T> input : inputs) {
            if (input.amount() < 0 || Long.MAX_VALUE - total < input.amount()) {
                return 0;
            }
            total += input.amount();
        }
        if (total <= 0 || total % inputsPerRecipe != 0 || total / inputsPerRecipe > Integer.MAX_VALUE) {
            return 0;
        }
        return (int) (total / inputsPerRecipe);
    }

    record Role<T>(Predicate<T> accepts) {
        Role {
            Objects.requireNonNull(accepts, "accepts");
        }
    }

    record Input<T>(T key, long amount) {
        Input {
            Objects.requireNonNull(key, "key");
        }
    }

    record Match<T>(List<T> assignments) {
        Match {
            assignments = List.copyOf(assignments);
        }

        T assignment(int role) {
            return this.assignments.get(role);
        }
    }

    private static final class Search<T> {
        private final List<Role<T>> roles;
        private final List<T> keys;
        private final long[] remaining;
        private final long multiplier;
        private final List<T> current;
        @Nullable
        private List<T> solution;
        private boolean ambiguous;

        private Search(List<Role<T>> roles, List<T> keys, long[] remaining, long multiplier) {
            this.roles = roles;
            this.keys = keys;
            this.remaining = remaining;
            this.multiplier = multiplier;
            this.current = new ArrayList<>(roles.size());
        }

        private void find(int roleIndex) {
            if (this.ambiguous) {
                return;
            }
            if (roleIndex == this.roles.size()) {
                for (long amount : this.remaining) {
                    if (amount != 0) {
                        return;
                    }
                }

                List<T> candidate = List.copyOf(this.current);
                if (this.solution == null) {
                    this.solution = candidate;
                } else if (!this.solution.equals(candidate)) {
                    this.ambiguous = true;
                }
                return;
            }

            Role<T> role = this.roles.get(roleIndex);
            for (int i = 0; i < this.keys.size(); i++) {
                if (this.remaining[i] < this.multiplier || !role.accepts().test(this.keys.get(i))) {
                    continue;
                }

                this.remaining[i] -= this.multiplier;
                this.current.add(this.keys.get(i));
                find(roleIndex + 1);
                this.current.removeLast();
                this.remaining[i] += this.multiplier;
            }
        }
    }
}
