package justfatlard.village_builder.block;

import com.mojang.serialization.MapCodec;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.village.VillageData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BuildersTableBlock extends Block implements EntityBlock {
   public static final MapCodec<BuildersTableBlock> CODEC = simpleCodec(p -> new BuildersTableBlock());

   public BuildersTableBlock() {
      super(Properties.of().setId(Main.BUILDERS_TABLE_BLOCK_KEY).strength(2.5F).sound(SoundType.WOOD));
   }

   @Override
   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new BuildersTableBlockEntity(pos, state);
   }

   @Override
   protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
      if (!moved) {
         VillageData villageData = Main.VILLAGE_DATA_MANAGER.getExistingVillageData(world, pos);
         if (villageData != null && !Main.VILLAGE_DATA_MANAGER.hasOtherTablesInVillage(world, pos)) {
            for (ItemStack stack : villageData.getInventoryStacks()) {
               if (!stack.isEmpty()) {
                  world.addFreshEntity(new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy()));
               }
            }
            villageData.clearMaterials();
         }
      }
      super.affectNeighborsAfterRemoval(state, world, pos, moved);
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
      if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
         if (world.dimension() != Level.OVERWORLD) {
            serverPlayer.sendSystemMessage(Component.translatable("message.village-builder.wrong_dimension"));
            return InteractionResult.CONSUME;
         }
         if (world.getBlockEntity(pos) instanceof BuildersTableBlockEntity buildersTable) {
            buildersTable.openScreen(serverPlayer);
         }
      }
      return InteractionResult.SUCCESS;
   }
}
