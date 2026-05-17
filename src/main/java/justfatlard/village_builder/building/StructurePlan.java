package justfatlard.village_builder.building;

import java.util.List;
import net.minecraft.resources.Identifier;

public class StructurePlan {
   private final Identifier structureId;
   private final String displayName;
   private final List<StructureType.MaterialRequirement> requirements;

   public StructurePlan(StructureType structureType) {
      this.structureId = Identifier.fromNamespaceAndPath("village-builder", structureType.getId());
      this.displayName = structureType.getDisplayName();
      this.requirements = List.of(structureType.getRequirements());
   }

   public StructurePlan(StructureEntry entry) {
      this.structureId = entry.id();
      this.displayName = entry.displayName();
      this.requirements = entry.requirements();
   }

   public Identifier getStructureId() {
      return this.structureId;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public List<StructureType.MaterialRequirement> getRequirements() {
      return this.requirements;
   }
}
