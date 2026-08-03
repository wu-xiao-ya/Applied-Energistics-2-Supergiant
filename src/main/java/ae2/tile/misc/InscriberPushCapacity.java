package ae2.tile.misc;

final class InscriberPushCapacity {
    private InscriberPushCapacity() {
    }

    static int maxRuns(int requestedRuns, int parallelLimit, int outputRuns, int... consumedSlotRuns) {
        if (requestedRuns <= 0 || parallelLimit <= 0 || outputRuns <= 0) {
            return 0;
        }

        int result = Math.min(Math.min(requestedRuns, parallelLimit), outputRuns);
        for (int slotRuns : consumedSlotRuns) {
            if (slotRuns >= 0) {
                result = Math.min(result, slotRuns);
            }
        }
        return result;
    }
}
