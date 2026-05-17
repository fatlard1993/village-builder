package justfatlard.village_builder.village;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class VillageDataState extends SavedData {
   private static final String DATA_NAME = "village-builder_villages";
   private Map<BlockPos, VillageData> villages = new HashMap<>();
   public static final Codec<VillageDataState> CODEC = CompoundTag.CODEC.xmap(VillageDataState::fromNbtCompound, VillageDataState::toNbtCompound);
   public static final SavedDataType<VillageDataState> TYPE = new SavedDataType<>(
      Identifier.fromNamespaceAndPath("village-builder", "villages"), VillageDataState::new, CODEC, null
   );

   public Map<BlockPos, VillageData> getVillages() {
      return this.villages;
   }

   public void setVillages(Map<BlockPos, VillageData> villages) {
      this.villages = villages;
   }

   private CompoundTag toNbtCompound() {
      CompoundTag root = new CompoundTag();
      ListTag list = new ListTag();

      for (VillageData villageData : Map.copyOf(this.villages).values()) {
         list.add(villageData.toNbt());
      }

      root.put("villages", list);
      return root;
   }

   private static VillageDataState fromNbtCompound(CompoundTag root) {
      VillageDataState state = new VillageDataState();
      root.getList("villages").ifPresent(villageList -> {
         for (int i = 0; i < villageList.size(); i++) {
            Tag tag = villageList.get(i);
            if (tag instanceof CompoundTag nbt) {
               VillageData data = VillageData.fromNbt(nbt);
               state.villages.put(data.getVillageCenter(), data);
            }
         }
      });
      return state;
   }

   public static VillageDataState getOrCreate(ServerLevel world) {
      return world.getDataStorage().computeIfAbsent(TYPE);
   }
}
