package justfatlard.village_builder.building;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import justfatlard.village_builder.village.VillageNeedsAnalyzer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StructureRegistry {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private final Map<Identifier, StructureEntry> entries = new HashMap<>();
   private final List<StructureEntry> allEntries = new ArrayList<>();
   private final Map<Identifier, int[]> seasonalMonths = new HashMap<>();
   private final List<Runnable> reloadCallbacks = new ArrayList<>();
   private boolean initialized = false;

   public void register(StructureEntry entry) {
      StructureEntry existing = this.entries.get(entry.id());
      if (existing != null) {
         if (entry.source() == StructureEntry.Source.FALLBACK && existing.source() != StructureEntry.Source.FALLBACK) {
            return;
         }
         this.allEntries.remove(existing);
      }

      this.entries.put(entry.id(), entry);
      this.allEntries.add(entry);
      LOGGER.debug("Registered structure: {} ({}, {})", new Object[]{entry.id(), entry.needs(), entry.source()});
   }

   public StructureEntry get(Identifier id) {
      return this.entries.get(id);
   }

   public List<StructureEntry> query(VillageNeedsAnalyzer.VillageNeed need, String biomeKey) {
      return this.queryDirect(need, biomeKey);
   }

   private List<StructureEntry> queryDirect(VillageNeedsAnalyzer.VillageNeed need, String biomeKey) {
      List<StructureEntry> discovered = new ArrayList<>();
      List<StructureEntry> fallbacks = new ArrayList<>();

      for (StructureEntry entry : this.allEntries) {
         if (entry.satisfiesNeed(need) && entry.fitsInBiome(biomeKey) && this.isAvailableThisSeason(entry)) {
            if (entry.source() == StructureEntry.Source.FALLBACK) {
               fallbacks.add(entry);
            } else {
               discovered.add(entry);
            }
         }
      }

      return !discovered.isEmpty() ? discovered : fallbacks;
   }

   public List<StructureEntry> query(VillageNeedsAnalyzer.VillageNeed need) {
      List<StructureEntry> result = new ArrayList<>();

      for (StructureEntry entry : this.allEntries) {
         if (entry.satisfiesNeed(need) && this.isAvailableThisSeason(entry)) {
            result.add(entry);
         }
      }

      return result;
   }

   public List<StructureEntry> getAll() {
      return List.copyOf(this.allEntries);
   }

   public void addReloadCallback(Runnable callback) {
      this.reloadCallbacks.add(callback);
   }

   public void runReloadCallbacks() {
      for (Runnable callback : this.reloadCallbacks) {
         try {
            callback.run();
         } catch (Exception var4) {
            LOGGER.error("Error in structure registry reload callback", var4);
         }
      }
   }

   public void clear() {
      this.entries.clear();
      this.allEntries.clear();
      this.seasonalMonths.clear();
      this.initialized = false;
   }

   public void registerBuildersWorkshops() {
      String[] biomes = new String[]{"plains", "taiga", "desert", "savanna", "snowy"};

      for (String biome : biomes) {
         StructureEntry entry = new StructureEntry(
            Identifier.fromNamespaceAndPath("village-builder", "builders_workshop_" + biome),
            "Builder's Workshop",
            Set.of(VillageNeedsAnalyzer.VillageNeed.PROFESSION),
            List.of(StructureType.BUILDERS_WORKSHOP.getRequirements()),
            Set.of(biome),
            8,
            StructureEntry.Source.FALLBACK
         );
         this.register(entry);
      }

      LOGGER.info("Registered {} biome Builder's Workshop variants", biomes.length);
   }

   public void registerSeasonalStructures() {
      int month = LocalDate.now().getMonthValue();
      Identifier pumpkinFarmId = Identifier.fromNamespaceAndPath("village-builder", "pumpkin_farm");
      StructureEntry pumpkinFarm = new StructureEntry(
         pumpkinFarmId,
         "Pumpkin Patch",
         Set.of(VillageNeedsAnalyzer.VillageNeed.FOOD),
         List.of(StructureType.PUMPKIN_FARM.getRequirements()),
         Set.of(),
         9,
         StructureEntry.Source.FALLBACK
      );
      this.register(pumpkinFarm);
      this.seasonalMonths.put(pumpkinFarmId, new int[]{10});
      Identifier christmasTreeId = Identifier.fromNamespaceAndPath("village-builder", "christmas_tree");
      StructureEntry christmasTree = new StructureEntry(
         christmasTreeId,
         "Village Christmas Tree",
         Set.of(VillageNeedsAnalyzer.VillageNeed.PROSPERITY),
         List.of(StructureType.CHRISTMAS_TREE.getRequirements()),
         Set.of(),
         11,
         StructureEntry.Source.FALLBACK
      );
      this.register(christmasTree);
      this.seasonalMonths.put(christmasTreeId, new int[]{11, 12});
      if (month == 10) {
         LOGGER.info("It's spooky season! Pumpkin Patch added to the build pool");
      }

      if (month == 11 || month == 12) {
         LOGGER.info("'Tis the season! Village Christmas Tree added to the build pool");
      }
   }

   private boolean isAvailableThisSeason(StructureEntry entry) {
      int[] months = this.seasonalMonths.get(entry.id());
      if (months == null) {
         return true;
      } else {
         int currentMonth = LocalDate.now().getMonthValue();

         for (int m : months) {
            if (m == currentMonth) {
               return true;
            }
         }

         return false;
      }
   }

   public void markInitialized() {
      this.initialized = true;
   }

   public boolean isInitialized() {
      return this.initialized;
   }

   public int size() {
      return this.entries.size();
   }
}
