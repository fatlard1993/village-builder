package justfatlard.village_builder.api;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A structure computed by code rather than read from an NBT template.
 *
 * <p>Positions are RELATIVE to the build origin: (0, 0, 0) is the structure's min-X/min-Z corner
 * at ground level. Village Builder computes the ground Y and offsets every position on placement;
 * providers never touch absolute world coordinates.
 *
 * <p>{@code blocks} iterates in insertion order and a later entry at a position wins, so a
 * {@link java.util.LinkedHashMap} is the expected implementation: generators can draw a wall
 * whole and then carve a doorway through it. Air entries are placed deliberately - they carve.
 *
 * <p>Every component is a vanilla or JDK type so the record can be constructed reflectively by
 * mods that avoid a compile-time dependency on Village Builder.
 */
public record BuildPlan(Vec3i size, Map<BlockPos, BlockState> blocks, List<BuildPlan.Chest> chests) {

   /** A chest to place with a loot table; {@code lootTable} is the table's identifier. */
   public record Chest(BlockPos pos, Direction facing, Identifier lootTable) {
   }

   public BuildPlan {
      chests = chests == null ? List.of() : chests;
   }
}
