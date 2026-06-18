package justfatlard.village_builder.item;

import justfatlard.village_builder.Main;
import justfatlard.village_builder.util.BuildersTableFinder;
import justfatlard.village_builder.village.VillageData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public class BuildersFlagItem extends Item {
   // How far from the flag position to search for an associated Builder's Table.
   // Mirrors the village notification radius; a player staking a site should be
   // within the village's area of influence.
   private static final int TABLE_SEARCH_RADIUS = 64;

   public BuildersFlagItem(Item.Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      if (!(level instanceof ServerLevel world)) return InteractionResult.PASS;

      Player player = context.getPlayer();
      BlockPos clicked = context.getClickedPos().relative(context.getClickedFace());

      // Snap to surface in case the player clicked a vertical face
      int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, clicked.getX(), clicked.getZ());
      BlockPos flagPos = new BlockPos(clicked.getX(), surfaceY, clicked.getZ());

      if (world.dimension() != Level.OVERWORLD) {
         if (player != null) {
            player.sendSystemMessage(Component.translatable("message.village-builder.wrong_dimension"));
         }
         return InteractionResult.FAIL;
      }

      BlockPos tablePos = BuildersTableFinder.findNearestBuildersTable(world, flagPos, TABLE_SEARCH_RADIUS);
      if (tablePos == null) {
         if (player != null) {
            player.sendSystemMessage(Component.translatable("message.village-builder.flag.no_table"));
         }
         return InteractionResult.FAIL;
      }

      VillageData data = Main.VILLAGE_DATA_MANAGER.getVillageDataForTable(world, tablePos);
      if (data == null) return InteractionResult.FAIL;

      data.setPreferredBuildSite(flagPos);
      Main.VILLAGE_DATA_MANAGER.markPersistentDirty();

      if (player != null) {
         player.sendSystemMessage(Component.translatable("message.village-builder.flag.placed",
            flagPos.getX(), flagPos.getY(), flagPos.getZ()));
         if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
         }
      }

      return InteractionResult.SUCCESS;
   }
}
