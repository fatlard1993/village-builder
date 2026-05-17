package justfatlard.village_builder.building;

import java.util.List;
import java.util.Set;
import justfatlard.village_builder.village.VillageNeedsAnalyzer;
import net.minecraft.resources.Identifier;

public record StructureEntry(
   Identifier id,
   String displayName,
   Set<VillageNeedsAnalyzer.VillageNeed> needs,
   List<StructureType.MaterialRequirement> requirements,
   Set<String> biomePreferences,
   int clearanceSize,
   StructureEntry.Source source
) {
   public int totalMaterialCost() {
      int total = 0;

      for (StructureType.MaterialRequirement req : this.requirements) {
         total += req.amount();
      }

      return total;
   }

   public int minBuildersRequired() {
      int cost = this.totalMaterialCost();
      if (cost > 500) {
         return 3;
      } else {
         return cost > 200 ? 2 : 1;
      }
   }

   public boolean satisfiesNeed(VillageNeedsAnalyzer.VillageNeed need) {
      return this.needs.contains(need);
   }

   public boolean fitsInBiome(String biomeKey) {
      return this.biomePreferences.isEmpty() ? true : this.biomePreferences.contains(biomeKey);
   }

   public static StructureEntry fromStructureType(StructureType type, VillageNeedsAnalyzer.VillageNeed need) {
      return new StructureEntry(
         Identifier.fromNamespaceAndPath("village-builder", type.getId()),
         type.getDisplayName(),
         Set.of(need),
         List.of(type.getRequirements()),
         Set.of(),
         type.getFootprintSize(),
         StructureEntry.Source.FALLBACK
      );
   }

   public static StructureEntry fromStructureType(StructureType type, Set<VillageNeedsAnalyzer.VillageNeed> needs) {
      return new StructureEntry(
         Identifier.fromNamespaceAndPath("village-builder", type.getId()),
         type.getDisplayName(),
         needs,
         List.of(type.getRequirements()),
         Set.of(),
         type.getFootprintSize(),
         StructureEntry.Source.FALLBACK
      );
   }

   public static enum Source {
      DISCOVERED,
      MOD_REGISTERED,
      FALLBACK;
   }
}
