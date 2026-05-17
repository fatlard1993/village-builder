package justfatlard.village_builder.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import justfatlard.village_builder.village.VillageNeedsAnalyzer;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StructureAnalyzer {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
      Map.entry("small_house", "Small House"),
      Map.entry("medium_house", "Medium House"),
      Map.entry("big_house", "Large House"),
      Map.entry("large_farm", "Large Farm"),
      Map.entry("small_farm", "Small Farm"),
      Map.entry("farm", "Farm"),
      Map.entry("butcher_shop", "Butcher Shop"),
      Map.entry("butchers_shop", "Butcher Shop"),
      Map.entry("armorer_house", "Armorer's Workshop"),
      Map.entry("armorer", "Armorer's Workshop"),
      Map.entry("fisher_cottage", "Fisher's Cottage"),
      Map.entry("fisher", "Fisher's Cottage"),
      Map.entry("fletcher_house", "Fletcher's Workshop"),
      Map.entry("cartographer", "Cartographer's Workshop"),
      Map.entry("cartographer_house", "Cartographer's Workshop"),
      Map.entry("shepherd_house", "Shepherd's House"),
      Map.entry("shepherds_house", "Shepherd's House"),
      Map.entry("shepherd", "Shepherd's House"),
      Map.entry("mason_house", "Mason's Workshop"),
      Map.entry("mason", "Mason's Workshop"),
      Map.entry("weaponsmith", "Weaponsmith's Workshop"),
      Map.entry("tool_smith", "Toolsmith's Workshop"),
      Map.entry("temple", "Temple"),
      Map.entry("library", "Library"),
      Map.entry("meeting_point", "Meeting Point"),
      Map.entry("stable", "Stable"),
      Map.entry("animal_pen", "Animal Pen")
   );

   public static StructureEntry analyze(ServerLevel world, Identifier templateId, String displayName, Set<String> biomePreferences) {
      StructureTemplateManager manager = world.getStructureManager();
      Optional<StructureTemplate> templateOpt = manager.get(templateId);
      if (templateOpt.isEmpty()) {
         LOGGER.debug("Template not found: {}", templateId);
         return null;
      } else {
         StructureTemplate template = templateOpt.get();
         Vec3i size = template.getSize();
         Map<Item, Integer> materialCounts = new HashMap<>();
         int bedCount = 0;
         int cropCount = 0;
         int workstationCount = 0;
         int fenceCount = 0;
         int wallBlockCount = 0;
         int ironBarCount = 0;
         int totalBlocks = 0;

         List<StructureBlockInfo> blockInfos;
         try {
            if (template.palettes.isEmpty()) {
               LOGGER.debug("Template {} has empty block info lists", templateId);
               return null;
            }

            blockInfos = ((Palette)template.palettes.getFirst()).blocks();
         } catch (Exception var24) {
            LOGGER.error("Failed to access block info for template {} — access widener may not be applied correctly", templateId, var24);
            return null;
         }

         for (StructureBlockInfo blockInfo : blockInfos) {
            Block block = blockInfo.state().getBlock();
            if (block instanceof BedBlock) {
               bedCount++;
            }

            if (block instanceof CropBlock) {
               cropCount++;
            }

            if (block instanceof FenceBlock || block instanceof FenceGateBlock) {
               fenceCount++;
            }

            if (block instanceof WallBlock) {
               wallBlockCount++;
            }

            if (block == Blocks.IRON_BARS) {
               ironBarCount++;
            }

            if (isWorkstation(block)) {
               workstationCount++;
            }

            Item material = MaterialMapping.getMaterial(block);
            if (material != null) {
               int weight = MaterialMapping.getWeight(block);
               materialCounts.merge(material, weight, Integer::sum);
               totalBlocks++;
            } else if (MaterialMapping.isGenericWood(block)) {
               String biome = biomePreferences.isEmpty() ? "plains" : biomePreferences.iterator().next();
               Item biomeLog = MaterialMapping.getBiomeLog(biome);
               int weight = MaterialMapping.getGenericWoodWeight(block);
               materialCounts.merge(biomeLog, weight, Integer::sum);
               totalBlocks++;
            }
         }

         if (totalBlocks == 0) {
            LOGGER.debug("Template {} has no mapped building materials", templateId);
            return null;
         } else {
            List<StructureType.MaterialRequirement> requirements = new ArrayList<>();

            for (Entry<Item, Integer> entry : materialCounts.entrySet()) {
               int rounded = MaterialMapping.roundToCleanStack(entry.getValue());
               if (rounded > 0) {
                  requirements.add(new StructureType.MaterialRequirement(entry.getKey(), rounded));
               }
            }

            requirements.sort((a, b) -> Integer.compare(b.amount(), a.amount()));
            Set<VillageNeedsAnalyzer.VillageNeed> needs = classifyNeeds(
               bedCount, cropCount, workstationCount, fenceCount, wallBlockCount, ironBarCount, totalBlocks
            );
            int clearance = Math.max(size.getX(), size.getZ());
            return new StructureEntry(templateId, displayName, needs, List.copyOf(requirements), biomePreferences, clearance, StructureEntry.Source.DISCOVERED);
         }
      }
   }

   private static Set<VillageNeedsAnalyzer.VillageNeed> classifyNeeds(
      int beds, int crops, int workstations, int fences, int wallBlocks, int ironBars, int totalBlocks
   ) {
      Set<VillageNeedsAnalyzer.VillageNeed> needs = new HashSet<>();
      if (beds >= 1) {
         needs.add(VillageNeedsAnalyzer.VillageNeed.HOUSING);
      }

      if (crops >= 4) {
         needs.add(VillageNeedsAnalyzer.VillageNeed.FOOD);
      }

      if (fences >= 8) {
         needs.add(VillageNeedsAnalyzer.VillageNeed.FOOD);
      }

      if (workstations >= 1) {
         needs.add(VillageNeedsAnalyzer.VillageNeed.PROFESSION);
      }

      if (wallBlocks >= 6 || ironBars >= 4) {
         needs.add(VillageNeedsAnalyzer.VillageNeed.DEFENSE);
      }

      if (needs.isEmpty()) {
         needs.add(totalBlocks > 200 ? VillageNeedsAnalyzer.VillageNeed.PROSPERITY : VillageNeedsAnalyzer.VillageNeed.UTILITY);
      }

      return Set.copyOf(needs);
   }

   private static boolean isWorkstation(Block block) {
      return block == Blocks.SMITHING_TABLE
         || block == Blocks.BLAST_FURNACE
         || block == Blocks.BREWING_STAND
         || block == Blocks.CARTOGRAPHY_TABLE
         || block == Blocks.COMPOSTER
         || block == Blocks.FLETCHING_TABLE
         || block == Blocks.GRINDSTONE
         || block == Blocks.LOOM
         || block == Blocks.SMOKER
         || block == Blocks.STONECUTTER
         || block == Blocks.LECTERN
         || block == Blocks.BARREL
         || block == Blocks.CAULDRON;
   }

   public static int discoverModStructures(ServerLevel world, StructureRegistry registry) {
      int count = 0;
      StructureTemplateManager manager = world.getStructureManager();
      Map<String, List<String>> vanillagePools = getVanillaVillagePools();

      for (Entry<String, List<String>> biomePool : vanillagePools.entrySet()) {
         String biome = biomePool.getKey();

         for (String templatePath : biomePool.getValue()) {
            Identifier templateId = Identifier.fromNamespaceAndPath("minecraft", templatePath);
            StructureEntry entry = analyze(world, templateId, formatDisplayName(templatePath), Set.of(biome));
            if (entry != null) {
               registry.register(entry);
               count++;
            }
         }
      }

      if (count == 0) {
         LOGGER.error(
            "Structure discovery found ZERO vanilla structures! The access widener may be misconfigured, or vanilla template paths have changed. Villages will only be able to build Builder's Workshops."
         );
      } else {
         LOGGER.info("Discovered {} vanilla village structures", count);
      }

      return count;
   }

   private static Map<String, List<String>> getVanillaVillagePools() {
      Map<String, List<String>> pools = new LinkedHashMap<>();
      pools.put(
         "plains",
         List.of(
            "village/plains/houses/plains_small_house_1",
            "village/plains/houses/plains_small_house_2",
            "village/plains/houses/plains_small_house_3",
            "village/plains/houses/plains_small_house_4",
            "village/plains/houses/plains_small_house_5",
            "village/plains/houses/plains_small_house_6",
            "village/plains/houses/plains_small_house_7",
            "village/plains/houses/plains_small_house_8",
            "village/plains/houses/plains_medium_house_1",
            "village/plains/houses/plains_medium_house_2",
            "village/plains/houses/plains_big_house_1",
            "village/plains/houses/plains_library_1",
            "village/plains/houses/plains_library_2",
            "village/plains/houses/plains_butcher_shop_1",
            "village/plains/houses/plains_butcher_shop_2",
            "village/plains/houses/plains_armorer_house_1",
            "village/plains/houses/plains_fisher_cottage_1",
            "village/plains/houses/plains_fletcher_house_1",
            "village/plains/houses/plains_cartographer_1",
            "village/plains/houses/plains_shepherd_house_1",
            "village/plains/houses/plains_mason_house_1",
            "village/plains/houses/plains_weaponsmith_1",
            "village/plains/houses/plains_temple_3",
            "village/plains/houses/plains_temple_4",
            "village/plains/houses/plains_stable_1",
            "village/plains/houses/plains_stable_2",
            "village/plains/houses/plains_large_farm_1",
            "village/plains/houses/plains_small_farm_1",
            "village/plains/houses/plains_animal_pen_1",
            "village/plains/houses/plains_animal_pen_2",
            "village/plains/houses/plains_animal_pen_3",
            "village/plains/houses/plains_tool_smith_1",
            "village/plains/houses/plains_meeting_point_4",
            "village/plains/houses/plains_meeting_point_5"
         )
      );
      pools.put(
         "taiga",
         List.of(
            "village/taiga/houses/taiga_small_house_1",
            "village/taiga/houses/taiga_small_house_2",
            "village/taiga/houses/taiga_small_house_3",
            "village/taiga/houses/taiga_small_house_4",
            "village/taiga/houses/taiga_small_house_5",
            "village/taiga/houses/taiga_medium_house_1",
            "village/taiga/houses/taiga_medium_house_2",
            "village/taiga/houses/taiga_medium_house_3",
            "village/taiga/houses/taiga_medium_house_4",
            "village/taiga/houses/taiga_butcher_shop_1",
            "village/taiga/houses/taiga_library_1",
            "village/taiga/houses/taiga_cartographer_house_1",
            "village/taiga/houses/taiga_fisher_cottage_1",
            "village/taiga/houses/taiga_fletcher_house_1",
            "village/taiga/houses/taiga_armorer_house_1",
            "village/taiga/houses/taiga_shepherds_house_1",
            "village/taiga/houses/taiga_mason_house_1",
            "village/taiga/houses/taiga_weaponsmith_1",
            "village/taiga/houses/taiga_tool_smith_1",
            "village/taiga/houses/taiga_temple_1",
            "village/taiga/houses/taiga_large_farm_1",
            "village/taiga/houses/taiga_large_farm_2",
            "village/taiga/houses/taiga_small_farm_1",
            "village/taiga/houses/taiga_animal_pen_1"
         )
      );
      pools.put(
         "savanna",
         List.of(
            "village/savanna/houses/savanna_small_house_1",
            "village/savanna/houses/savanna_small_house_2",
            "village/savanna/houses/savanna_small_house_3",
            "village/savanna/houses/savanna_small_house_4",
            "village/savanna/houses/savanna_small_house_5",
            "village/savanna/houses/savanna_small_house_6",
            "village/savanna/houses/savanna_small_house_7",
            "village/savanna/houses/savanna_small_house_8",
            "village/savanna/houses/savanna_medium_house_1",
            "village/savanna/houses/savanna_medium_house_2",
            "village/savanna/houses/savanna_large_farm_1",
            "village/savanna/houses/savanna_large_farm_2",
            "village/savanna/houses/savanna_small_farm_1",
            "village/savanna/houses/savanna_butchers_shop_1",
            "village/savanna/houses/savanna_butchers_shop_2",
            "village/savanna/houses/savanna_armorer_1",
            "village/savanna/houses/savanna_fisher_cottage_1",
            "village/savanna/houses/savanna_fletcher_house_1",
            "village/savanna/houses/savanna_fletcher_house_2",
            "village/savanna/houses/savanna_cartographer_1",
            "village/savanna/houses/savanna_shepherd_1",
            "village/savanna/houses/savanna_mason_1",
            "village/savanna/houses/savanna_weaponsmith_1",
            "village/savanna/houses/savanna_weaponsmith_2",
            "village/savanna/houses/savanna_tool_smith_1",
            "village/savanna/houses/savanna_temple_1",
            "village/savanna/houses/savanna_temple_2"
         )
      );
      pools.put(
         "desert",
         List.of(
            "village/desert/houses/desert_small_house_1",
            "village/desert/houses/desert_small_house_2",
            "village/desert/houses/desert_small_house_3",
            "village/desert/houses/desert_small_house_4",
            "village/desert/houses/desert_small_house_5",
            "village/desert/houses/desert_small_house_6",
            "village/desert/houses/desert_small_house_7",
            "village/desert/houses/desert_small_house_8",
            "village/desert/houses/desert_medium_house_1",
            "village/desert/houses/desert_medium_house_2",
            "village/desert/houses/desert_large_farm_1",
            "village/desert/houses/desert_small_farm_1",
            "village/desert/houses/desert_butcher_shop_1",
            "village/desert/houses/desert_armorer_1",
            "village/desert/houses/desert_fisher_1",
            "village/desert/houses/desert_cartographer_house_1",
            "village/desert/houses/desert_shepherd_house_1",
            "village/desert/houses/desert_mason_1",
            "village/desert/houses/desert_weaponsmith_1",
            "village/desert/houses/desert_tool_smith_1",
            "village/desert/houses/desert_temple_1",
            "village/desert/houses/desert_temple_2"
         )
      );
      pools.put(
         "snowy",
         List.of(
            "village/snowy/houses/snowy_small_house_1",
            "village/snowy/houses/snowy_small_house_2",
            "village/snowy/houses/snowy_small_house_3",
            "village/snowy/houses/snowy_small_house_4",
            "village/snowy/houses/snowy_small_house_5",
            "village/snowy/houses/snowy_small_house_6",
            "village/snowy/houses/snowy_small_house_7",
            "village/snowy/houses/snowy_small_house_8",
            "village/snowy/houses/snowy_medium_house_1",
            "village/snowy/houses/snowy_medium_house_2",
            "village/snowy/houses/snowy_medium_house_3",
            "village/snowy/houses/snowy_library_1",
            "village/snowy/houses/snowy_butchers_shop_1",
            "village/snowy/houses/snowy_butchers_shop_2",
            "village/snowy/houses/snowy_armorer_house_1",
            "village/snowy/houses/snowy_armorer_house_2",
            "village/snowy/houses/snowy_fisher_cottage_1",
            "village/snowy/houses/snowy_fletcher_house_1",
            "village/snowy/houses/snowy_cartographer_house_1",
            "village/snowy/houses/snowy_shepherd_house_1",
            "village/snowy/houses/snowy_mason_house_1",
            "village/snowy/houses/snowy_mason_house_2",
            "village/snowy/houses/snowy_weaponsmith_1",
            "village/snowy/houses/snowy_tool_smith_1",
            "village/snowy/houses/snowy_temple_1",
            "village/snowy/houses/snowy_farm_1",
            "village/snowy/houses/snowy_farm_2",
            "village/snowy/houses/snowy_animal_pen_1",
            "village/snowy/houses/snowy_animal_pen_2"
         )
      );
      return pools;
   }

   private static String formatDisplayName(String path) {
      String filename = path.substring(path.lastIndexOf(47) + 1);
      String stem = filename.replaceAll("_\\d+$", "");

      for (String biome : new String[]{"plains_", "taiga_", "savanna_", "desert_", "snowy_"}) {
         if (stem.startsWith(biome)) {
            stem = stem.substring(biome.length());
            break;
         }
      }

      String display = DISPLAY_NAMES.get(stem);
      if (display != null) {
         return display;
      } else {
         String[] words = stem.split("_");
         StringBuilder sb = new StringBuilder();

         for (String word : words) {
            if (!word.isEmpty()) {
               if (sb.length() > 0) {
                  sb.append(' ');
               }

               sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
         }

         return sb.toString();
      }
   }
}
