package justfatlard.village_builder.building;

import java.util.List;
import java.util.Set;
import justfatlard.village_builder.village.VillageNeedsAnalyzer;
import net.minecraft.resources.Identifier;

/**
 * @param limitGroup    structures sharing a group count against one another for
 *                      {@link #maxPerVillage}. Lets a mod register several variants of one thing
 *                      (a castle in three sizes, say) and cap the lot at one per village rather
 *                      than one of each. Defaults to the entry's own id, so an entry that names no
 *                      group is only ever limited against itself.
 * @param maxPerVillage how many of this group a single village may build. Zero or less is
 *                      unlimited, which is the default and what every built-in structure uses.
 */
public record StructureEntry(
   Identifier id,
   String displayName,
   Set<VillageNeedsAnalyzer.VillageNeed> needs,
   List<StructureType.MaterialRequirement> requirements,
   Set<String> biomePreferences,
   int clearanceSize,
   StructureEntry.Source source,
   String limitGroup,
   int maxPerVillage
) {
   /** Value of {@link #maxPerVillage} meaning "as many as the village wants". */
   public static final int UNLIMITED = 0;

   /** An entry with no per-village limit. */
   public StructureEntry(
      Identifier id,
      String displayName,
      Set<VillageNeedsAnalyzer.VillageNeed> needs,
      List<StructureType.MaterialRequirement> requirements,
      Set<String> biomePreferences,
      int clearanceSize,
      StructureEntry.Source source
   ) {
      this(id, displayName, needs, requirements, biomePreferences, clearanceSize, source, null, UNLIMITED);
   }

   public StructureEntry {
      if (limitGroup == null || limitGroup.isBlank()) {
         limitGroup = id.toString();
      }
   }

   /** Whether this entry is capped at all. */
   public boolean hasVillageLimit() {
      return this.maxPerVillage > UNLIMITED;
   }

   /**
    * Whether a village that has already built {@code builtInGroup} of this entry's limit group
    * may build another.
    */
   public boolean allowsAnotherInVillage(int builtInGroup) {
      return !this.hasVillageLimit() || builtInGroup < this.maxPerVillage;
   }

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
