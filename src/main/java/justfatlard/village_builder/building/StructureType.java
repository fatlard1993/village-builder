package justfatlard.village_builder.building;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum StructureType {
   BUILDERS_WORKSHOP(
      "builders_workshop",
      "Builder's Workshop",
      1,
      3,
      5,
      new StructureType.MaterialRequirement(Items.COBBLESTONE, 64),
      new StructureType.MaterialRequirement(Items.OAK_LOG, 32),
      new StructureType.MaterialRequirement(Items.COAL, 4)
   ),
   CHRISTMAS_TREE(
      "christmas_tree",
      "Village Christmas Tree",
      1,
      5,
      11,
      new StructureType.MaterialRequirement(Items.SPRUCE_LOG, 32),
      new StructureType.MaterialRequirement(Items.GOLD_INGOT, 8),
      new StructureType.MaterialRequirement(Items.GLOWSTONE, 16),
      new StructureType.MaterialRequirement(Items.CHEST, 4)
   ),
   PUMPKIN_FARM(
      "pumpkin_farm",
      "Pumpkin Patch",
      1,
      2,
      9,
      new StructureType.MaterialRequirement(Items.PUMPKIN, 16),
      new StructureType.MaterialRequirement(Items.PUMPKIN_SEEDS, 8),
      new StructureType.MaterialRequirement(Items.BONE_MEAL, 8)
   );

   private final String id;
   private final String displayName;
   private final int requiredLevel;
   private final int emeraldCost;
   private final int footprintSize;
   private final StructureType.MaterialRequirement[] requirements;

   private StructureType(
      String id, String displayName, int requiredLevel, int emeraldCost, int footprintSize, StructureType.MaterialRequirement... requirements
   ) {
      this.id = id;
      this.displayName = displayName;
      this.requiredLevel = requiredLevel;
      this.emeraldCost = emeraldCost;
      this.footprintSize = footprintSize;
      this.requirements = requirements;
   }

   public String getId() {
      return this.id;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public int getRequiredLevel() {
      return this.requiredLevel;
   }

   public int getEmeraldCost() {
      return this.emeraldCost;
   }

   public StructureType.MaterialRequirement[] getRequirements() {
      return (StructureType.MaterialRequirement[])this.requirements.clone();
   }

   public int getFootprintSize() {
      return this.footprintSize;
   }

   public static StructureType fromId(String id) {
      if (id == null) {
         return null;
      } else {
         for (StructureType type : values()) {
            if (type.id.equals(id)) {
               return type;
            }
         }

         return null;
      }
   }

   public record MaterialRequirement(Item item, int amount) {
   }
}
