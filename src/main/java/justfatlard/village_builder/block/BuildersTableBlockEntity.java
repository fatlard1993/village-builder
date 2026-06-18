package justfatlard.village_builder.block;

import java.util.ArrayList;
import java.util.List;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_builder.screen.BuildersTableData;
import justfatlard.village_builder.screen.BuildersTableScreenHandler;
import justfatlard.village_builder.village.VillageData;
import justfatlard.village_builder.village.VillageNeedsAnalyzer;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class BuildersTableBlockEntity extends BlockEntity implements ExtendedMenuProvider<BuildersTableData> {
   public BuildersTableBlockEntity(BlockPos pos, BlockState state) {
      super(Main.BUILDERS_TABLE_BLOCK_ENTITY, pos, state);
   }

   public void openScreen(ServerPlayer player) {
      player.openMenu(this);
   }

   @Override
   public Component getDisplayName() {
      return Component.translatable("container.village-builder.builders_table");
   }

   @Override
   public BuildersTableData getScreenOpeningData(ServerPlayer player) {
      if (!(this.level instanceof ServerLevel serverWorld)) {
         return BuildersTableData.EMPTY;
      }

      VillageData villageData = Main.VILLAGE_DATA_MANAGER.getVillageDataForTable(serverWorld, this.worldPosition);
      if (villageData == null || villageData.getCurrentPlan() == null) {
         return BuildersTableData.EMPTY;
      }

      StructurePlan plan = villageData.getCurrentPlan();
      VillageNeedsAnalyzer analyzer = villageData.getOrCreateAnalyzer(serverWorld);
      VillageNeedsAnalyzer.VillageNeed need = analyzer.analyzeNeeds();
      String needReason = formatNeedReason(need, analyzer);

      List<BuildersTableData.MaterialInfo> materials = new ArrayList<>();
      for (StructureType.MaterialRequirement req : plan.getRequirements()) {
         Identifier itemId = BuiltInRegistries.ITEM.getKey(req.item());
         int have = Math.min(villageData.getMaterialCount(req.item()), req.amount());
         materials.add(new BuildersTableData.MaterialInfo(itemId.toString(), have, req.amount()));
      }

      AABB searchBox = new AABB(this.worldPosition).inflate(64.0);
      int builderCount = serverWorld.getEntitiesOfClass(Villager.class, searchBox,
         v -> v.getVillagerData().profession().is(Main.BUILDER_KEY)).size();

      String constructionHint;
      if (builderCount == 0) {
         constructionHint = Component.translatable("gui.village-builder.hint.no_builder").getString();
      } else if (villageData.hasAllMaterials()) {
         constructionHint = Component.translatable("gui.village-builder.hint.ready").getString();
      } else {
         constructionHint = Component.translatable("gui.village-builder.hint.gathering", builderCount).getString();
      }

      return new BuildersTableData(plan.getDisplayName(), needReason, materials, builderCount, constructionHint);
   }

   private static String formatNeedReason(VillageNeedsAnalyzer.VillageNeed need, VillageNeedsAnalyzer analyzer) {
      int villagers = analyzer.getVillagerCount();
      int beds = analyzer.getBedCount();

      return switch (need) {
         case HOUSING -> beds == 0
            ? Component.translatable("gui.village-builder.need.housing").getString() + " (no beds)"
            : Component.translatable("gui.village-builder.need.housing").getString() + String.format(" (%d villagers, %d beds)", villagers, beds);
         case FOOD -> Component.translatable("gui.village-builder.need.food").getString() + String.format(" (%d farmland)", analyzer.getFarmlandCount());
         case PROFESSION -> Component.translatable("gui.village-builder.need.profession").getString();
         case DEFENSE -> Component.translatable("gui.village-builder.need.defense").getString()
            + String.format(" (%d villagers, %d golems)", villagers, analyzer.getIronGolemCount());
         case UTILITY -> Component.translatable("gui.village-builder.need.utility").getString();
         case PROSPERITY -> Component.translatable("gui.village-builder.need.prosperity").getString() + String.format(" (%d villagers)", villagers);
      };
   }

   @Override
   public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
      if (this.level instanceof ServerLevel serverWorld) {
         return new BuildersTableScreenHandler(syncId, playerInventory, serverWorld, this.worldPosition);
      }
      throw new IllegalStateException("Builder's Table screen can only be created on the server");
   }
}
