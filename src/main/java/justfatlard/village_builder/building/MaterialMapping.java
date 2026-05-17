package justfatlard.village_builder.building;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class MaterialMapping {
   private static final Map<Block, Item> BLOCK_TO_MATERIAL = new HashMap<>();
   private static final Map<Block, Integer> BLOCK_TO_WEIGHT = new HashMap<>();
   private static final Map<Block, Integer> GENERIC_WOOD_BLOCKS = new HashMap<>();
   private static Set<Item> ALL_MATERIAL_ITEMS;

   private static void putCrafted(Block block, Item material, int weight) {
      BLOCK_TO_MATERIAL.put(block, material);
      if (weight > 1) {
         BLOCK_TO_WEIGHT.put(block, weight);
      }
   }

   public static Item getMaterial(Block block) {
      return BLOCK_TO_MATERIAL.get(block);
   }

   public static int getWeight(Block block) {
      return BLOCK_TO_WEIGHT.getOrDefault(block, 1);
   }

   public static boolean isMappedBlock(Block block) {
      return BLOCK_TO_MATERIAL.containsKey(block) || GENERIC_WOOD_BLOCKS.containsKey(block);
   }

   public static boolean isGenericWood(Block block) {
      return GENERIC_WOOD_BLOCKS.containsKey(block);
   }

   public static int getGenericWoodWeight(Block block) {
      return GENERIC_WOOD_BLOCKS.getOrDefault(block, 0);
   }

   public static Item getBiomeLog(String biomeKey) {
      return switch (biomeKey) {
         case "taiga", "snowy" -> Items.SPRUCE_LOG;
         case "savanna" -> Items.ACACIA_LOG;
         case "cherry" -> Items.CHERRY_LOG;
         case "mangrove" -> Items.MANGROVE_LOG;
         case "desert" -> Items.OAK_LOG;
         default -> Items.OAK_LOG;
      };
   }

   public static Set<Item> getAllMaterialItems() {
      if (ALL_MATERIAL_ITEMS == null) {
         ALL_MATERIAL_ITEMS = Set.copyOf(BLOCK_TO_MATERIAL.values());
      }

      return ALL_MATERIAL_ITEMS;
   }

   public static int roundToCleanStack(int rawCount) {
      if (rawCount <= 0) {
         return 0;
      } else if (rawCount <= 4) {
         return rawCount;
      } else if (rawCount <= 16) {
         return (rawCount + 3) / 4 * 4;
      } else {
         return rawCount <= 64 ? (rawCount + 7) / 8 * 8 : (rawCount + 15) / 16 * 16;
      }
   }

   static {
      for (Block b : new Block[]{
         Blocks.STONE,
         Blocks.COBBLESTONE,
         Blocks.STONE_BRICKS,
         Blocks.MOSSY_STONE_BRICKS,
         Blocks.CRACKED_STONE_BRICKS,
         Blocks.CHISELED_STONE_BRICKS,
         Blocks.SMOOTH_STONE,
         Blocks.COBBLESTONE_WALL,
         Blocks.STONE_BRICK_WALL,
         Blocks.COBBLESTONE_STAIRS,
         Blocks.STONE_STAIRS,
         Blocks.STONE_BRICK_STAIRS,
         Blocks.COBBLESTONE_SLAB,
         Blocks.STONE_SLAB,
         Blocks.STONE_BRICK_SLAB,
         Blocks.POLISHED_GRANITE,
         Blocks.POLISHED_DIORITE,
         Blocks.POLISHED_ANDESITE,
         Blocks.GRANITE,
         Blocks.DIORITE,
         Blocks.ANDESITE,
         Blocks.MOSSY_COBBLESTONE,
         Blocks.MOSSY_COBBLESTONE_WALL,
         Blocks.MOSSY_COBBLESTONE_STAIRS,
         Blocks.MOSSY_COBBLESTONE_SLAB,
         Blocks.INFESTED_STONE,
         Blocks.INFESTED_STONE_BRICKS,
         Blocks.INFESTED_COBBLESTONE
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.COBBLESTONE);
      }

      for (Block b : new Block[]{
         Blocks.SANDSTONE,
         Blocks.CUT_SANDSTONE,
         Blocks.CHISELED_SANDSTONE,
         Blocks.SMOOTH_SANDSTONE,
         Blocks.SANDSTONE_WALL,
         Blocks.SANDSTONE_STAIRS,
         Blocks.SANDSTONE_SLAB,
         Blocks.SMOOTH_SANDSTONE_STAIRS,
         Blocks.SMOOTH_SANDSTONE_SLAB,
         Blocks.RED_SANDSTONE,
         Blocks.CUT_RED_SANDSTONE,
         Blocks.CHISELED_RED_SANDSTONE,
         Blocks.SMOOTH_RED_SANDSTONE
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.SANDSTONE);
      }

      for (Block b : new Block[]{
         Blocks.OAK_LOG,
         Blocks.OAK_WOOD,
         Blocks.STRIPPED_OAK_LOG,
         Blocks.STRIPPED_OAK_WOOD,
         Blocks.OAK_PLANKS,
         Blocks.OAK_SLAB,
         Blocks.OAK_STAIRS,
         Blocks.OAK_FENCE,
         Blocks.OAK_FENCE_GATE,
         Blocks.OAK_DOOR,
         Blocks.OAK_TRAPDOOR,
         Blocks.OAK_SIGN,
         Blocks.OAK_WALL_SIGN,
         Blocks.OAK_PRESSURE_PLATE,
         Blocks.OAK_BUTTON
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.OAK_LOG);
      }

      for (Block b : new Block[]{
         Blocks.SPRUCE_LOG,
         Blocks.SPRUCE_WOOD,
         Blocks.STRIPPED_SPRUCE_LOG,
         Blocks.STRIPPED_SPRUCE_WOOD,
         Blocks.SPRUCE_PLANKS,
         Blocks.SPRUCE_SLAB,
         Blocks.SPRUCE_STAIRS,
         Blocks.SPRUCE_FENCE,
         Blocks.SPRUCE_FENCE_GATE,
         Blocks.SPRUCE_DOOR,
         Blocks.SPRUCE_TRAPDOOR,
         Blocks.SPRUCE_SIGN,
         Blocks.SPRUCE_WALL_SIGN,
         Blocks.SPRUCE_PRESSURE_PLATE,
         Blocks.SPRUCE_BUTTON
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.SPRUCE_LOG);
      }

      for (Block b : new Block[]{
         Blocks.BIRCH_LOG,
         Blocks.BIRCH_WOOD,
         Blocks.STRIPPED_BIRCH_LOG,
         Blocks.STRIPPED_BIRCH_WOOD,
         Blocks.BIRCH_PLANKS,
         Blocks.BIRCH_SLAB,
         Blocks.BIRCH_STAIRS,
         Blocks.BIRCH_FENCE,
         Blocks.BIRCH_FENCE_GATE,
         Blocks.BIRCH_DOOR,
         Blocks.BIRCH_TRAPDOOR,
         Blocks.BIRCH_SIGN,
         Blocks.BIRCH_WALL_SIGN,
         Blocks.BIRCH_PRESSURE_PLATE,
         Blocks.BIRCH_BUTTON
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.BIRCH_LOG);
      }

      for (Block b : new Block[]{
         Blocks.DARK_OAK_LOG,
         Blocks.DARK_OAK_WOOD,
         Blocks.STRIPPED_DARK_OAK_LOG,
         Blocks.STRIPPED_DARK_OAK_WOOD,
         Blocks.DARK_OAK_PLANKS,
         Blocks.DARK_OAK_SLAB,
         Blocks.DARK_OAK_STAIRS,
         Blocks.DARK_OAK_FENCE,
         Blocks.DARK_OAK_FENCE_GATE,
         Blocks.DARK_OAK_DOOR,
         Blocks.DARK_OAK_TRAPDOOR,
         Blocks.DARK_OAK_SIGN,
         Blocks.DARK_OAK_WALL_SIGN,
         Blocks.DARK_OAK_PRESSURE_PLATE,
         Blocks.DARK_OAK_BUTTON
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.DARK_OAK_LOG);
      }

      for (Block b : new Block[]{
         Blocks.ACACIA_LOG,
         Blocks.ACACIA_WOOD,
         Blocks.STRIPPED_ACACIA_LOG,
         Blocks.STRIPPED_ACACIA_WOOD,
         Blocks.ACACIA_PLANKS,
         Blocks.ACACIA_SLAB,
         Blocks.ACACIA_STAIRS,
         Blocks.ACACIA_FENCE,
         Blocks.ACACIA_FENCE_GATE,
         Blocks.ACACIA_DOOR,
         Blocks.ACACIA_TRAPDOOR,
         Blocks.ACACIA_SIGN,
         Blocks.ACACIA_WALL_SIGN,
         Blocks.ACACIA_PRESSURE_PLATE,
         Blocks.ACACIA_BUTTON
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.ACACIA_LOG);
      }

      for (Block b : new Block[]{
         Blocks.JUNGLE_LOG,
         Blocks.JUNGLE_WOOD,
         Blocks.STRIPPED_JUNGLE_LOG,
         Blocks.STRIPPED_JUNGLE_WOOD,
         Blocks.JUNGLE_PLANKS,
         Blocks.JUNGLE_SLAB,
         Blocks.JUNGLE_STAIRS,
         Blocks.JUNGLE_FENCE,
         Blocks.JUNGLE_FENCE_GATE,
         Blocks.JUNGLE_DOOR,
         Blocks.JUNGLE_TRAPDOOR,
         Blocks.JUNGLE_SIGN,
         Blocks.JUNGLE_WALL_SIGN,
         Blocks.JUNGLE_PRESSURE_PLATE,
         Blocks.JUNGLE_BUTTON
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.JUNGLE_LOG);
      }

      for (Block b : new Block[]{
         Blocks.CHERRY_LOG,
         Blocks.CHERRY_WOOD,
         Blocks.STRIPPED_CHERRY_LOG,
         Blocks.STRIPPED_CHERRY_WOOD,
         Blocks.CHERRY_PLANKS,
         Blocks.CHERRY_SLAB,
         Blocks.CHERRY_STAIRS,
         Blocks.CHERRY_FENCE,
         Blocks.CHERRY_FENCE_GATE,
         Blocks.CHERRY_DOOR,
         Blocks.CHERRY_TRAPDOOR,
         Blocks.CHERRY_SIGN,
         Blocks.CHERRY_WALL_SIGN,
         Blocks.CHERRY_PRESSURE_PLATE,
         Blocks.CHERRY_BUTTON
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.CHERRY_LOG);
      }

      for (Block b : new Block[]{
         Blocks.MANGROVE_LOG,
         Blocks.MANGROVE_WOOD,
         Blocks.STRIPPED_MANGROVE_LOG,
         Blocks.STRIPPED_MANGROVE_WOOD,
         Blocks.MANGROVE_PLANKS,
         Blocks.MANGROVE_SLAB,
         Blocks.MANGROVE_STAIRS,
         Blocks.MANGROVE_FENCE,
         Blocks.MANGROVE_FENCE_GATE,
         Blocks.MANGROVE_DOOR,
         Blocks.MANGROVE_TRAPDOOR,
         Blocks.MANGROVE_SIGN,
         Blocks.MANGROVE_WALL_SIGN,
         Blocks.MANGROVE_PRESSURE_PLATE,
         Blocks.MANGROVE_BUTTON,
         Blocks.MANGROVE_ROOTS,
         Blocks.MUDDY_MANGROVE_ROOTS
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.MANGROVE_LOG);
      }

      for (Block b : new Block[]{
         Blocks.BAMBOO_BLOCK,
         Blocks.STRIPPED_BAMBOO_BLOCK,
         Blocks.BAMBOO_PLANKS,
         Blocks.BAMBOO_SLAB,
         Blocks.BAMBOO_STAIRS,
         Blocks.BAMBOO_FENCE,
         Blocks.BAMBOO_FENCE_GATE,
         Blocks.BAMBOO_DOOR,
         Blocks.BAMBOO_TRAPDOOR,
         Blocks.BAMBOO_SIGN,
         Blocks.BAMBOO_WALL_SIGN,
         Blocks.BAMBOO_PRESSURE_PLATE,
         Blocks.BAMBOO_BUTTON,
         Blocks.BAMBOO_MOSAIC,
         Blocks.BAMBOO_MOSAIC_SLAB,
         Blocks.BAMBOO_MOSAIC_STAIRS
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.BAMBOO_BLOCK);
      }

      for (Block b : new Block[]{
         Blocks.CRIMSON_STEM,
         Blocks.CRIMSON_HYPHAE,
         Blocks.STRIPPED_CRIMSON_STEM,
         Blocks.STRIPPED_CRIMSON_HYPHAE,
         Blocks.CRIMSON_PLANKS,
         Blocks.CRIMSON_SLAB,
         Blocks.CRIMSON_STAIRS,
         Blocks.CRIMSON_FENCE,
         Blocks.CRIMSON_FENCE_GATE,
         Blocks.CRIMSON_DOOR,
         Blocks.CRIMSON_TRAPDOOR,
         Blocks.CRIMSON_SIGN,
         Blocks.CRIMSON_WALL_SIGN,
         Blocks.CRIMSON_PRESSURE_PLATE,
         Blocks.CRIMSON_BUTTON
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.CRIMSON_STEM);
      }

      for (Block b : new Block[]{
         Blocks.WARPED_STEM,
         Blocks.WARPED_HYPHAE,
         Blocks.STRIPPED_WARPED_STEM,
         Blocks.STRIPPED_WARPED_HYPHAE,
         Blocks.WARPED_PLANKS,
         Blocks.WARPED_SLAB,
         Blocks.WARPED_STAIRS,
         Blocks.WARPED_FENCE,
         Blocks.WARPED_FENCE_GATE,
         Blocks.WARPED_DOOR,
         Blocks.WARPED_TRAPDOOR,
         Blocks.WARPED_SIGN,
         Blocks.WARPED_WALL_SIGN,
         Blocks.WARPED_PRESSURE_PLATE,
         Blocks.WARPED_BUTTON
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.WARPED_STEM);
      }

      for (Block b : new Block[]{
         Blocks.GLASS,
         Blocks.GLASS_PANE,
         Blocks.WHITE_STAINED_GLASS,
         Blocks.ORANGE_STAINED_GLASS,
         Blocks.MAGENTA_STAINED_GLASS,
         Blocks.LIGHT_BLUE_STAINED_GLASS,
         Blocks.YELLOW_STAINED_GLASS,
         Blocks.LIME_STAINED_GLASS,
         Blocks.PINK_STAINED_GLASS,
         Blocks.GRAY_STAINED_GLASS,
         Blocks.LIGHT_GRAY_STAINED_GLASS,
         Blocks.CYAN_STAINED_GLASS,
         Blocks.PURPLE_STAINED_GLASS,
         Blocks.BLUE_STAINED_GLASS,
         Blocks.BROWN_STAINED_GLASS,
         Blocks.GREEN_STAINED_GLASS,
         Blocks.RED_STAINED_GLASS,
         Blocks.BLACK_STAINED_GLASS,
         Blocks.WHITE_STAINED_GLASS_PANE,
         Blocks.ORANGE_STAINED_GLASS_PANE,
         Blocks.MAGENTA_STAINED_GLASS_PANE,
         Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
         Blocks.YELLOW_STAINED_GLASS_PANE,
         Blocks.LIME_STAINED_GLASS_PANE,
         Blocks.PINK_STAINED_GLASS_PANE,
         Blocks.GRAY_STAINED_GLASS_PANE,
         Blocks.LIGHT_GRAY_STAINED_GLASS_PANE,
         Blocks.CYAN_STAINED_GLASS_PANE,
         Blocks.PURPLE_STAINED_GLASS_PANE,
         Blocks.BLUE_STAINED_GLASS_PANE,
         Blocks.BROWN_STAINED_GLASS_PANE,
         Blocks.GREEN_STAINED_GLASS_PANE,
         Blocks.RED_STAINED_GLASS_PANE,
         Blocks.BLACK_STAINED_GLASS_PANE
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.GLASS);
      }

      for (Block b : new Block[]{
         Blocks.TERRACOTTA,
         Blocks.WHITE_TERRACOTTA,
         Blocks.ORANGE_TERRACOTTA,
         Blocks.YELLOW_TERRACOTTA,
         Blocks.BROWN_TERRACOTTA,
         Blocks.RED_TERRACOTTA,
         Blocks.LIGHT_GRAY_TERRACOTTA,
         Blocks.CYAN_TERRACOTTA,
         Blocks.WHITE_GLAZED_TERRACOTTA,
         Blocks.ORANGE_GLAZED_TERRACOTTA
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.TERRACOTTA);
      }

      for (Block b : new Block[]{
         Blocks.DIRT,
         Blocks.GRASS_BLOCK,
         Blocks.DIRT_PATH,
         Blocks.COARSE_DIRT,
         Blocks.FARMLAND,
         Blocks.PODZOL,
         Blocks.ROOTED_DIRT,
         Blocks.MUD,
         Blocks.PACKED_MUD,
         Blocks.MUD_BRICKS,
         Blocks.MUD_BRICK_STAIRS,
         Blocks.MUD_BRICK_SLAB,
         Blocks.MUD_BRICK_WALL
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.DIRT);
      }

      BLOCK_TO_MATERIAL.put(Blocks.IRON_BARS, Items.IRON_INGOT);
      BLOCK_TO_MATERIAL.put(Blocks.IRON_BLOCK, Items.IRON_INGOT);
      BLOCK_TO_WEIGHT.put(Blocks.IRON_BLOCK, 9);
      BLOCK_TO_MATERIAL.put(Blocks.IRON_DOOR, Items.IRON_INGOT);
      BLOCK_TO_WEIGHT.put(Blocks.IRON_DOOR, 2);
      BLOCK_TO_MATERIAL.put(Blocks.IRON_TRAPDOOR, Items.IRON_INGOT);
      BLOCK_TO_WEIGHT.put(Blocks.IRON_TRAPDOOR, 4);
      BLOCK_TO_MATERIAL.put(Blocks.LANTERN, Items.IRON_INGOT);
      BLOCK_TO_MATERIAL.put(Blocks.TORCH, Items.COAL);
      BLOCK_TO_MATERIAL.put(Blocks.WALL_TORCH, Items.COAL);
      BLOCK_TO_MATERIAL.put(Blocks.CAMPFIRE, Items.COAL);
      BLOCK_TO_MATERIAL.put(Blocks.BELL, Items.GOLD_INGOT);
      BLOCK_TO_WEIGHT.put(Blocks.BELL, 4);
      BLOCK_TO_MATERIAL.put(Blocks.BOOKSHELF, Items.LEATHER);
      BLOCK_TO_WEIGHT.put(Blocks.BOOKSHELF, 3);
      BLOCK_TO_MATERIAL.put(Blocks.LECTERN, Items.LEATHER);

      for (Block b : new Block[]{
         Blocks.WHITE_WOOL,
         Blocks.ORANGE_WOOL,
         Blocks.MAGENTA_WOOL,
         Blocks.LIGHT_BLUE_WOOL,
         Blocks.YELLOW_WOOL,
         Blocks.LIME_WOOL,
         Blocks.PINK_WOOL,
         Blocks.GRAY_WOOL,
         Blocks.LIGHT_GRAY_WOOL,
         Blocks.CYAN_WOOL,
         Blocks.PURPLE_WOOL,
         Blocks.BLUE_WOOL,
         Blocks.BROWN_WOOL,
         Blocks.GREEN_WOOL,
         Blocks.RED_WOOL,
         Blocks.BLACK_WOOL,
         Blocks.WHITE_CARPET,
         Blocks.ORANGE_CARPET,
         Blocks.MAGENTA_CARPET,
         Blocks.LIGHT_BLUE_CARPET,
         Blocks.YELLOW_CARPET,
         Blocks.LIME_CARPET,
         Blocks.PINK_CARPET,
         Blocks.GRAY_CARPET,
         Blocks.LIGHT_GRAY_CARPET,
         Blocks.CYAN_CARPET,
         Blocks.PURPLE_CARPET,
         Blocks.BLUE_CARPET,
         Blocks.BROWN_CARPET,
         Blocks.GREEN_CARPET,
         Blocks.RED_CARPET,
         Blocks.BLACK_CARPET,
         Blocks.WHITE_BED,
         Blocks.ORANGE_BED,
         Blocks.MAGENTA_BED,
         Blocks.LIGHT_BLUE_BED,
         Blocks.YELLOW_BED,
         Blocks.LIME_BED,
         Blocks.PINK_BED,
         Blocks.GRAY_BED,
         Blocks.LIGHT_GRAY_BED,
         Blocks.CYAN_BED,
         Blocks.PURPLE_BED,
         Blocks.BLUE_BED,
         Blocks.BROWN_BED,
         Blocks.GREEN_BED,
         Blocks.RED_BED,
         Blocks.BLACK_BED
      }) {
         BLOCK_TO_MATERIAL.put(b, Items.WHITE_WOOL);
      }

      putCrafted(Blocks.FURNACE, Items.COBBLESTONE, 8);
      putCrafted(Blocks.GRINDSTONE, Items.COBBLESTONE, 2);
      putCrafted(Blocks.STONECUTTER, Items.COBBLESTONE, 3);
      putCrafted(Blocks.BREWING_STAND, Items.COBBLESTONE, 3);
      putCrafted(Blocks.FLOWER_POT, Items.TERRACOTTA, 1);
      putCrafted(Blocks.BLAST_FURNACE, Items.IRON_INGOT, 5);
      putCrafted(Blocks.SMITHING_TABLE, Items.IRON_INGOT, 2);
      putCrafted(Blocks.ANVIL, Items.IRON_INGOT, 12);
      putCrafted(Blocks.CHIPPED_ANVIL, Items.IRON_INGOT, 12);
      putCrafted(Blocks.DAMAGED_ANVIL, Items.IRON_INGOT, 12);
      putCrafted(Blocks.CAULDRON, Items.IRON_INGOT, 7);
      putCrafted(Blocks.HOPPER, Items.IRON_INGOT, 5);
      GENERIC_WOOD_BLOCKS.put(Blocks.CRAFTING_TABLE, 1);
      GENERIC_WOOD_BLOCKS.put(Blocks.CHEST, 2);
      GENERIC_WOOD_BLOCKS.put(Blocks.BARREL, 2);
      GENERIC_WOOD_BLOCKS.put(Blocks.SMOKER, 2);
      GENERIC_WOOD_BLOCKS.put(Blocks.COMPOSTER, 2);
      GENERIC_WOOD_BLOCKS.put(Blocks.CARTOGRAPHY_TABLE, 1);
      GENERIC_WOOD_BLOCKS.put(Blocks.FLETCHING_TABLE, 1);
      GENERIC_WOOD_BLOCKS.put(Blocks.LOOM, 1);
      GENERIC_WOOD_BLOCKS.put(Blocks.LADDER, 1);
      BLOCK_TO_MATERIAL.put(Blocks.HAY_BLOCK, Items.DIRT);
   }
}
