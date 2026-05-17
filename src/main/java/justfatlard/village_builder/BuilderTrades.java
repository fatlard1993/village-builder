package justfatlard.village_builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuilderTrades {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final Item[] MATERIAL_POOL = new Item[]{
      Items.COBBLESTONE, Items.STONE, Items.STONE_BRICKS,
      Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.DARK_OAK_LOG,
      Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG,
      Items.BAMBOO_BLOCK, Items.CRIMSON_STEM, Items.WARPED_STEM,
      Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS,
      Items.DARK_OAK_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS,
      Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS,
      Items.CRIMSON_PLANKS, Items.WARPED_PLANKS,
      Items.GLASS, Items.GLASS_PANE, Items.DIRT, Items.GRASS_BLOCK,
      Items.SAND, Items.SANDSTONE, Items.BRICKS,
      Items.IRON_INGOT, Items.GOLD_INGOT,
      Items.WHITE_WOOL, Items.RED_WOOL, Items.BLUE_WOOL,
      Items.TERRACOTTA, Items.DEEPSLATE_BRICKS,
      Items.TUFF_BRICKS, Items.COPPER_BLOCK, Items.ANVIL, Items.LEATHER,
      Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL
   };
   public static final Set<Item> MATERIAL_POOL_SET = new HashSet<>(Arrays.asList(MATERIAL_POOL));

   public static void register() {
      // TODO: Villager trades are now data-driven in MC 26.1.
      // Dynamic trades based on village state need a new mechanism.
      // The Builder profession is registered but trades are not yet implemented for 26.1.
      LOGGER.info("Builder profession registered (trades pending 26.1 data-driven migration)");
   }

   public static void refreshVillagerOffers(ServerLevel world, BlockPos villageCenter) {
      // Refresh offers for Builder villagers near the village center
      AABB area = new AABB(villageCenter).inflate(96);
      for (Villager villager : world.getEntitiesOfClass(Villager.class, area, v -> v.getVillagerData().profession().is(Main.BUILDER_KEY))) {
         villager.getOffers().clear();
      }
   }
}
