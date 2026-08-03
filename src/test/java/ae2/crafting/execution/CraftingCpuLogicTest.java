package ae2.crafting.execution;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.crafting.CraftingLink;
import ae2.crafting.CraftingLinkNexus;
import ae2.crafting.inv.ListCraftingInventory;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingCpuLogicTest {
    private static final String NBT_PENDING_STANDALONE_OUTPUT = "pendingStandaloneOutput";
    private static final AEKeyType TEST_TYPE = new AEKeyType(
        new ResourceLocation("test", "crafting_cpu_output"),
        TestKey.class,
        new TextComponentString("test")) {
        @Override
        public AEKey readFromPacket(PacketBuffer input) {
            return null;
        }

        @Override
        public AEKey loadKeyFromTag(NBTTagCompound tag) {
            return null;
        }
    };
    private static final AEKey OUTPUT = new TestKey();
    private static final IActionSource SOURCE = IActionSource.empty();

    @Test
    void transfersAllStandaloneOutputWhenStorageAcceptsIt() {
        var inventory = inventoryWith(32);
        var storage = new CapacityStorage(32);

        var remaining = CraftingCpuLogic.transferStandaloneOutput(OUTPUT, 32, inventory, storage, SOURCE);

        assertEquals(0, remaining);
        assertEquals(0, storedInCpu(inventory));
        assertEquals(32, storage.stored);
    }

    @Test
    void retriesStandaloneOutputAfterPartialInsertion() {
        var inventory = inventoryWith(12);
        var storage = new CapacityStorage(5);

        var remaining = CraftingCpuLogic.transferStandaloneOutput(OUTPUT, 12, inventory, storage, SOURCE);

        assertEquals(7, remaining);
        assertEquals(7, storedInCpu(inventory));
        assertEquals(5, storage.stored);

        storage.capacity = 12;
        remaining = CraftingCpuLogic.transferStandaloneOutput(OUTPUT, remaining, inventory, storage, SOURCE);

        assertEquals(0, remaining);
        assertEquals(0, storedInCpu(inventory));
        assertEquals(12, storage.stored);
    }

    @Test
    void keepsStandaloneOutputWhenStorageRejectsIt() {
        var inventory = inventoryWith(9);
        var storage = new CapacityStorage(0);

        var remaining = CraftingCpuLogic.transferStandaloneOutput(OUTPUT, 9, inventory, storage, SOURCE);

        assertEquals(9, remaining);
        assertEquals(9, storedInCpu(inventory));
        assertEquals(0, storage.stored);

        storage.capacity = 9;
        remaining = CraftingCpuLogic.transferStandaloneOutput(OUTPUT, remaining, inventory, storage, SOURCE);

        assertEquals(0, remaining);
        assertEquals(0, storedInCpu(inventory));
        assertEquals(9, storage.stored);
    }

    @Test
    void neverTransfersMoreThanTheCpuActuallyContains() {
        var inventory = inventoryWith(4);
        var storage = new CapacityStorage(10);

        var remaining = CraftingCpuLogic.transferStandaloneOutput(OUTPUT, 10, inventory, storage, SOURCE);

        assertEquals(0, remaining);
        assertEquals(0, storedInCpu(inventory));
        assertEquals(4, storage.stored);
    }

    @Test
    void keepsIntermediateFinalOutputInTheCpu() {
        var cluster = new CraftingCPUCluster(BlockPos.ORIGIN, BlockPos.ORIGIN);
        var logic = cluster.craftingLogic;
        var plan = new TestPlan(10, 4);
        var link = new CraftingLink(
            CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false),
            cluster);
        var job = new ExecutingCraftingJob(plan, what -> {
        }, link, null, new KeyCounter());
        setJob(logic, job);

        assertEquals(10, logic.insert(OUTPUT, 10, Actionable.MODULATE));

        assertEquals(10, logic.getStored(OUTPUT));
        assertEquals(6, getPendingStandaloneOutput(logic));
        assertEquals(0, job.remainingIntermediateFinalOutput);
        assertTrue(logic.hasJob());
    }

    @Test
    void requesterJobsStillDeliverOutputDirectly() {
        var cluster = new CraftingCPUCluster(BlockPos.ORIGIN, BlockPos.ORIGIN);
        var logic = cluster.craftingLogic;
        var requester = new RecordingRequester();
        var craftId = UUID.randomUUID();
        var cpuLink = new CraftingLink(
            CraftingCpuHelper.generateLinkData(craftId, false, false),
            cluster);
        var requesterLink = new CraftingLink(
            CraftingCpuHelper.generateLinkData(craftId, false, true),
            requester);
        var nexus = new CraftingLinkNexus(craftId);
        cpuLink.setNexus(nexus);
        requesterLink.setNexus(nexus);
        var job = new ExecutingCraftingJob(new TestPlan(5, 0), what -> {
        }, cpuLink, null, new KeyCounter());
        setJob(logic, job);

        assertEquals(5, logic.insert(OUTPUT, 5, Actionable.MODULATE));

        assertEquals(5, requester.received);
        assertEquals(0, logic.getStored(OUTPUT));
        assertEquals(0, getPendingStandaloneOutput(logic));
        assertFalse(logic.hasJob());
        assertTrue(requester.jobFinished);
    }

    @Test
    void pendingStandaloneOutputSurvivesNbtRoundTrip() {
        var loadedData = new NBTTagCompound();
        loadedData.setLong(NBT_PENDING_STANDALONE_OUTPUT, 17);

        var original = new CraftingCPUCluster(BlockPos.ORIGIN, BlockPos.ORIGIN).craftingLogic;
        original.readFromNBT(loadedData);
        var savedData = new NBTTagCompound();
        original.writeToNBT(savedData);

        var restored = new CraftingCPUCluster(BlockPos.ORIGIN, BlockPos.ORIGIN).craftingLogic;
        restored.readFromNBT(savedData);
        var resavedData = new NBTTagCompound();
        restored.writeToNBT(resavedData);

        assertEquals(17, resavedData.getLong(NBT_PENDING_STANDALONE_OUTPUT));
    }

    private static ListCraftingInventory inventoryWith(long amount) {
        var inventory = new ListCraftingInventory(what -> {
        });
        inventory.insert(OUTPUT, amount, Actionable.MODULATE);
        return inventory;
    }

    private static long storedInCpu(ListCraftingInventory inventory) {
        return inventory.extract(OUTPUT, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    private static void setJob(CraftingCpuLogic logic, ExecutingCraftingJob job) {
        try {
            getField("job").set(logic, job);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static long getPendingStandaloneOutput(CraftingCpuLogic logic) {
        try {
            return getField("pendingStandaloneOutput").getLong(logic);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Field getField(String name) {
        try {
            var field = CraftingCpuLogic.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    private static final class TestPlan implements ICraftingPlan {
        private final GenericStack finalOutput;
        private final KeyCounter emittedItems = new KeyCounter();
        private final long intermediateFinalOutputAmount;

        private TestPlan(long outputAmount, long intermediateFinalOutputAmount) {
            this.finalOutput = new GenericStack(OUTPUT, outputAmount);
            this.emittedItems.add(OUTPUT, outputAmount);
            this.intermediateFinalOutputAmount = intermediateFinalOutputAmount;
        }

        @Override
        public GenericStack finalOutput() {
            return finalOutput;
        }

        @Override
        public long bytes() {
            return 0;
        }

        @Override
        public boolean simulation() {
            return false;
        }

        @Override
        public boolean multiplePaths() {
            return false;
        }

        @Override
        public KeyCounter usedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter emittedItems() {
            return emittedItems;
        }

        @Override
        public KeyCounter missingItems() {
            return new KeyCounter();
        }

        @Override
        public long intermediateFinalOutputAmount() {
            return intermediateFinalOutputAmount;
        }

        @Override
        public Object2LongMap<IPatternDetails> patternTimes() {
            return new Object2LongOpenHashMap<>();
        }
    }

    private static final class RecordingRequester implements ICraftingRequester {
        private long received;
        private boolean jobFinished;

        @Override
        public ImmutableSet<ICraftingLink> getRequestedJobs() {
            return ImmutableSet.of();
        }

        @Override
        public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
            if (mode == Actionable.MODULATE) {
                received += amount;
            }
            return amount;
        }

        @Override
        public void jobStateChange(ICraftingLink link) {
            jobFinished = true;
        }

        @Override
        public IGridNode getActionableNode() {
            return null;
        }
    }

    private static final class TestKey extends AEKey {
        private static final Object PRIMARY_KEY = new Object();

        @Override
        public AEKeyType getType() {
            return TEST_TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public NBTTagCompound toTag() {
            return new NBTTagCompound();
        }

        @Override
        public Object getPrimaryKey() {
            return PRIMARY_KEY;
        }

        @Override
        public ResourceLocation getId() {
            return null;
        }

        @Override
        public void writeToPacket(PacketBuffer data) {
        }

        @Override
        public Object getReadOnlyStack() {
            return null;
        }

        @Override
        protected ITextComponent computeDisplayName() {
            return new TextComponentString("test");
        }

        @Override
        public NBTBase get(String componentId) {
            return null;
        }
    }

    private static final class CapacityStorage implements MEStorage {
        private long capacity;
        private long stored;

        private CapacityStorage(long capacity) {
            this.capacity = capacity;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            long accepted = Math.min(amount, Math.max(0, capacity - stored));
            if (mode == Actionable.MODULATE) {
                stored += accepted;
            }
            return accepted;
        }

        @Override
        public ITextComponent getDescription() {
            return new TextComponentString("test");
        }
    }
}
