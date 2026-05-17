package justfatlard.village_builder.village;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.building.StructureEntry;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageData {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final int CURRENT_NBT_VERSION = 1;
   private BlockPos villageCenter;
   private StructurePlan currentPlan = null;
   private final NonNullList<ItemStack> inventory = NonNullList.withSize(27, ItemStack.EMPTY);
   private final VillageInventory inventoryWrapper = new VillageInventory(this);
   private boolean dirty = false;
   private int gatheringIndex = 0;
   private int placementFailures = 0;
   static final int MAX_PLACEMENT_FAILURES = 3;
   static final int MAX_BUILT_STRUCTURES = 40;
   private final List<BuiltStructure> builtStructures = new ArrayList<>();
   private transient VillageNeedsAnalyzer cachedAnalyzer;
   private transient long lastAccessedTick = 0L;
   private transient UUID planPatronUuid;
   private transient String planPatronName;

   public VillageData(BlockPos villageCenter) {
      this.villageCenter = villageCenter;
   }

   public void markDirty() {
      this.dirty = true;
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public void clearDirty() {
      this.dirty = false;
   }

   public void touch(long currentTick) {
      this.lastAccessedTick = currentTick;
   }

   public long getLastAccessedTick() {
      return this.lastAccessedTick;
   }

   public void setVillageCenter(BlockPos newCenter) {
      this.villageCenter = newCenter;
      this.cachedAnalyzer = null;
      this.markDirty();
   }

   public VillageNeedsAnalyzer getOrCreateAnalyzer(ServerLevel world) {
      if (this.cachedAnalyzer == null) {
         this.cachedAnalyzer = new VillageNeedsAnalyzer(world, this.villageCenter);
      }
      return this.cachedAnalyzer;
   }

   public void clearAnalyzer() {
      this.cachedAnalyzer = null;
   }

   public int getGatheringIndex() {
      return this.gatheringIndex;
   }

   public void advanceGatheringIndex() {
      this.gatheringIndex++;
      this.markDirty();
   }

   public void recordBuiltStructure(BlockPos pos, int size) {
      this.builtStructures.add(new BuiltStructure(pos, size));
      while (this.builtStructures.size() > MAX_BUILT_STRUCTURES) {
         this.builtStructures.remove(0);
      }
      this.markDirty();
   }

   public int getPlacementFailures() {
      return this.placementFailures;
   }

   public void incrementPlacementFailures() {
      this.placementFailures++;
      this.markDirty();
   }

   public void resetPlacementFailures() {
      this.placementFailures = 0;
   }

   public List<BuiltStructure> getBuiltStructures() {
      return this.builtStructures;
   }

   public int validateBuiltStructures(ServerLevel world) {
      int removed = 0;
      Iterator<BuiltStructure> iter = this.builtStructures.iterator();
      while (iter.hasNext()) {
         BuiltStructure bs = iter.next();
         BlockPos center = bs.pos().offset(bs.size() / 2, 0, bs.size() / 2);
         if (world.hasChunkAt(center)) {
            boolean groundSolid = world.getBlockState(center.below()).isSolid();
            boolean hasStructure = !world.getBlockState(center).isAir() || !world.getBlockState(center.above()).isAir();
            if (!groundSolid && !hasStructure) {
               iter.remove();
               removed++;
            }
         }
      }
      if (removed > 0) {
         this.markDirty();
      }
      return removed;
   }

   public boolean overlapsExistingStructure(BlockPos pos, int size) {
      for (BuiltStructure existing : this.builtStructures) {
         if (footprintsOverlap(pos, size, existing.pos(), existing.size())) {
            return true;
         }
      }
      return false;
   }

   private static boolean footprintsOverlap(BlockPos a, int sizeA, BlockPos b, int sizeB) {
      return a.getX() < b.getX() + sizeB
         && a.getX() + sizeA > b.getX()
         && a.getZ() < b.getZ() + sizeB
         && a.getZ() + sizeA > b.getZ();
   }

   public Container getInventory() {
      return this.inventoryWrapper;
   }

   public Container getInventoryForTable(BlockPos tablePos) {
      return this.inventoryWrapper;
   }

   public NonNullList<ItemStack> getInventoryStacks() {
      return this.inventory;
   }

   public StructurePlan getCurrentPlan() {
      return this.currentPlan;
   }

   public void setCurrentPlan(StructurePlan plan) {
      this.currentPlan = plan;
      this.markDirty();
   }

   public int tryAddMaterial(Item item, int count) {
      synchronized (this.inventory) {
         ItemStack toAdd = new ItemStack(item, count);
         this.addToInventory(toAdd);
         this.markDirty();
         return toAdd.getCount();
      }
   }

   public int getMaterialCount(Item item) {
      synchronized (this.inventory) {
         int total = 0;
         for (ItemStack stack : this.inventory) {
            if (!stack.isEmpty() && stack.getItem() == item) {
               total += stack.getCount();
            }
         }
         return total;
      }
   }

   public void clearMaterials() {
      synchronized (this.inventory) {
         for (int i = 0; i < this.inventory.size(); i++) {
            this.inventory.set(i, ItemStack.EMPTY);
         }
         this.markDirty();
      }
   }

   public boolean hasAllMaterials() {
      if (this.currentPlan == null) {
         return false;
      }
      synchronized (this.inventory) {
         for (StructureType.MaterialRequirement req : this.currentPlan.getRequirements()) {
            if (this.getMaterialCountUnsync(req.item()) < req.amount()) {
               return false;
            }
         }
         return true;
      }
   }

   private int getMaterialCountUnsync(Item item) {
      int total = 0;
      for (ItemStack stack : this.inventory) {
         if (!stack.isEmpty() && stack.getItem() == item) {
            total += stack.getCount();
         }
      }
      return total;
   }

   public float getCompletionPercentage() {
      if (this.currentPlan == null) {
         return 0.0F;
      }
      List<StructureType.MaterialRequirement> reqs = this.currentPlan.getRequirements();
      if (reqs.isEmpty()) {
         return 1.0F;
      }
      synchronized (this.inventory) {
         float sum = 0.0F;
         for (StructureType.MaterialRequirement req : reqs) {
            float fraction = Math.min(1.0F, (float) this.getMaterialCountUnsync(req.item()) / req.amount());
            sum += fraction;
         }
         return sum / reqs.size();
      }
   }

   public boolean tryConsumeMaterials() {
      synchronized (this.inventory) {
         if (this.currentPlan == null) {
            return false;
         }
         if (!this.hasAllMaterials()) {
            return false;
         }
         for (StructureType.MaterialRequirement req : this.currentPlan.getRequirements()) {
            int remaining = req.amount();
            for (int i = 0; i < this.inventory.size() && remaining > 0; i++) {
               ItemStack stack = this.inventory.get(i);
               if (!stack.isEmpty() && stack.getItem() == req.item()) {
                  int toRemove = Math.min(remaining, stack.getCount());
                  stack.shrink(toRemove);
                  if (stack.isEmpty()) {
                     this.inventory.set(i, ItemStack.EMPTY);
                  }
                  remaining -= toRemove;
               }
            }
         }
         this.markDirty();
         return true;
      }
   }

   private void addToInventory(ItemStack toAdd) {
      for (int i = 0; i < this.inventory.size() && !toAdd.isEmpty(); i++) {
         ItemStack slot = this.inventory.get(i);
         if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, toAdd)) {
            int canAdd = Math.min(slot.getMaxStackSize() - slot.getCount(), toAdd.getCount());
            if (canAdd > 0) {
               slot.grow(canAdd);
               toAdd.shrink(canAdd);
            }
         }
      }
      for (int i = 0; i < this.inventory.size() && !toAdd.isEmpty(); i++) {
         if (this.inventory.get(i).isEmpty()) {
            this.inventory.set(i, toAdd.split(Math.min(toAdd.getCount(), toAdd.getMaxStackSize())));
         }
      }
      if (!toAdd.isEmpty()) {
         LOGGER.warn("Village inventory full — {}x {} could not fit", toAdd.getCount(), toAdd.getItem());
      }
   }

   public List<ItemStack> snapshotInventory() {
      synchronized (this.inventory) {
         List<ItemStack> snapshot = new ArrayList<>(this.inventory.size());
         for (ItemStack stack : this.inventory) {
            snapshot.add(stack.copy());
         }
         return snapshot;
      }
   }

   public List<ItemStack> snapshotAndConsumeMaterials() {
      synchronized (this.inventory) {
         if (this.currentPlan == null || !this.hasAllMaterials()) {
            return null;
         }
         List<ItemStack> snapshot = new ArrayList<>(this.inventory.size());
         for (ItemStack stack : this.inventory) {
            snapshot.add(stack.copy());
         }
         for (StructureType.MaterialRequirement req : this.currentPlan.getRequirements()) {
            int remaining = req.amount();
            for (int i = 0; i < this.inventory.size() && remaining > 0; i++) {
               ItemStack stack = this.inventory.get(i);
               if (!stack.isEmpty() && stack.getItem() == req.item()) {
                  int toRemove = Math.min(remaining, stack.getCount());
                  stack.shrink(toRemove);
                  if (stack.isEmpty()) {
                     this.inventory.set(i, ItemStack.EMPTY);
                  }
                  remaining -= toRemove;
               }
            }
         }
         this.markDirty();
         return snapshot;
      }
   }

   public void restoreInventory(List<ItemStack> snapshot) {
      synchronized (this.inventory) {
         for (int i = 0; i < this.inventory.size() && i < snapshot.size(); i++) {
            this.inventory.set(i, snapshot.get(i));
         }
         this.markDirty();
      }
   }

   public void clearCurrentPlan() {
      this.currentPlan = null;
      this.planPatronUuid = null;
      this.planPatronName = null;
      this.markDirty();
   }

   public void setPlanPatron(UUID uuid, String name) {
      this.planPatronUuid = uuid;
      this.planPatronName = name;
   }

   public UUID getPlanPatronUuid() {
      return this.planPatronUuid;
   }

   public String getPlanPatronName() {
      return this.planPatronName;
   }

   public BlockPos getVillageCenter() {
      return this.villageCenter;
   }

   // --- NBT Serialization ---

   public static VillageData fromNbt(CompoundTag nbt) {
      try {
         return fromNbtInternal(nbt);
      } catch (Exception e) {
         LOGGER.error("Failed to deserialize village data — returning fresh village: {}", e.getMessage());
         BlockPos center = new BlockPos(
            nbt.getIntOr("centerX", 0),
            nbt.getIntOr("centerY", 64),
            nbt.getIntOr("centerZ", 0)
         );
         return new VillageData(center);
      }
   }

   private static VillageData fromNbtInternal(CompoundTag nbt) {
      int version = nbt.getIntOr("nbtVersion", 0);
      if (version > CURRENT_NBT_VERSION) {
         LOGGER.warn("Village data has newer version {} (current: {}) — loading anyway, some data may be lost", version, CURRENT_NBT_VERSION);
      }

      BlockPos center = new BlockPos(
         nbt.getIntOr("centerX", 0),
         nbt.getIntOr("centerY", 64),
         nbt.getIntOr("centerZ", 0)
      );
      VillageData data = new VillageData(center);

      nbt.getString("currentPlan").ifPresent(planId -> {
         Identifier structureId = Identifier.tryParse(planId);
         if (structureId != null) {
            StructureType type = StructureType.fromId(structureId.getPath());
            if (type != null) {
               data.currentPlan = new StructurePlan(type);
            } else {
               StructureEntry entry = Main.STRUCTURE_REGISTRY.get(structureId);
               if (entry != null) {
                  data.currentPlan = new StructurePlan(entry);
               } else {
                  LOGGER.warn("Could not resolve saved plan '{}' — will reassign on next tick", planId);
               }
            }
         }
      });

      if (nbt.contains("Inventory")) {
         nbt.getList("Inventory").ifPresent(inventoryList -> {
            for (int i = 0; i < inventoryList.size(); i++) {
               inventoryList.getCompound(i).ifPresent(slotNbt -> {
                  int slot = slotNbt.getByteOr("Slot", (byte) 0) & 255;
                  if (slot >= 0 && slot < data.inventory.size()) {
                     ItemStack.CODEC
                        .parse(NbtOps.INSTANCE, slotNbt)
                        .resultOrPartial(LOGGER::error)
                        .ifPresent(stack -> data.inventory.set(slot, stack));
                  }
               });
            }
         });
      }

      data.gatheringIndex = Math.max(0, nbt.getIntOr("gatheringIndex", 0));

      if (nbt.contains("BuiltStructures")) {
         nbt.getList("BuiltStructures").ifPresent(list -> {
            for (int i = 0; i < list.size(); i++) {
               list.getCompound(i).ifPresent(entry -> {
                  int bx = entry.getIntOr("x", 0);
                  int by = entry.getIntOr("y", 64);
                  int bz = entry.getIntOr("z", 0);
                  int size = entry.getIntOr("size", 5);
                  data.builtStructures.add(new BuiltStructure(new BlockPos(bx, by, bz), size));
               });
            }
         });
      }

      return data;
   }

   public CompoundTag toNbt() {
      CompoundTag nbt = new CompoundTag();
      nbt.putInt("nbtVersion", CURRENT_NBT_VERSION);
      nbt.putInt("centerX", this.villageCenter.getX());
      nbt.putInt("centerY", this.villageCenter.getY());
      nbt.putInt("centerZ", this.villageCenter.getZ());
      nbt.putInt("gatheringIndex", this.gatheringIndex);

      if (this.currentPlan != null) {
         nbt.putString("currentPlan", this.currentPlan.getStructureId().toString());
      }

      ListTag inventoryList = new ListTag();
      synchronized (this.inventory) {
         for (int i = 0; i < this.inventory.size(); i++) {
            ItemStack stack = this.inventory.get(i);
            if (!stack.isEmpty()) {
               int slot = i;
               ItemStack copy = stack.copy();
               ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, copy).resultOrPartial(LOGGER::error).ifPresent(stackNbt -> {
                  if (stackNbt instanceof CompoundTag compound) {
                     compound.putByte("Slot", (byte) slot);
                     inventoryList.add(compound);
                  }
               });
            }
         }
      }

      nbt.put("Inventory", inventoryList);

      ListTag builtList = new ListTag();
      for (BuiltStructure bs : this.builtStructures) {
         CompoundTag entry = new CompoundTag();
         entry.putInt("x", bs.pos().getX());
         entry.putInt("y", bs.pos().getY());
         entry.putInt("z", bs.pos().getZ());
         entry.putInt("size", bs.size());
         builtList.add(entry);
      }
      nbt.put("BuiltStructures", builtList);

      return nbt;
   }

   public record BuiltStructure(BlockPos pos, int size) {
   }

   static class VillageInventory implements Container {
      private final VillageData villageData;

      public VillageInventory(VillageData villageData) {
         this.villageData = villageData;
      }

      @Override
      public int getContainerSize() {
         return this.villageData.inventory.size();
      }

      @Override
      public boolean isEmpty() {
         synchronized (this.villageData.inventory) {
            for (ItemStack stack : this.villageData.inventory) {
               if (!stack.isEmpty()) {
                  return false;
               }
            }
            return true;
         }
      }

      @Override
      public ItemStack getItem(int slot) {
         synchronized (this.villageData.inventory) {
            return this.villageData.inventory.get(slot);
         }
      }

      @Override
      public ItemStack removeItem(int slot, int amount) {
         synchronized (this.villageData.inventory) {
            ItemStack result = ContainerHelper.removeItem(this.villageData.inventory, slot, amount);
            if (!result.isEmpty()) {
               this.setChanged();
            }
            return result;
         }
      }

      @Override
      public ItemStack removeItemNoUpdate(int slot) {
         synchronized (this.villageData.inventory) {
            ItemStack result = ContainerHelper.takeItem(this.villageData.inventory, slot);
            if (!result.isEmpty()) {
               this.setChanged();
            }
            return result;
         }
      }

      @Override
      public void setItem(int slot, ItemStack stack) {
         synchronized (this.villageData.inventory) {
            this.villageData.inventory.set(slot, stack);
            this.setChanged();
         }
      }

      @Override
      public void setChanged() {
         this.villageData.markDirty();
      }

      @Override
      public boolean stillValid(Player player) {
         BlockPos checkPos = this.villageData.villageCenter;
         return player.distanceToSqr(checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5) <= 64.0;
      }

      @Override
      public void clearContent() {
         this.villageData.inventory.clear();
         this.setChanged();
      }
   }
}
