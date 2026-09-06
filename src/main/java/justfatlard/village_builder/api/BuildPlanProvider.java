package justfatlard.village_builder.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Computes a {@link BuildPlan} at build time for a procedurally generated structure.
 *
 * <p>{@code facing} is Village Builder's chosen orientation: the PROVIDER rotates its plan to
 * face it - Village Builder never rotates block states. {@code biomeKey} is one of the five API
 * biome strings ({@link VillageBuilderAPI#BIOME_PLAINS} and friends). {@code origin} is the
 * ground position (y = surface) handed over for terrain sampling only; the returned plan is
 * still origin-relative per the {@link BuildPlan} contract.
 *
 * <p>A null return, or a plan with no blocks, makes the placement fail cleanly (the village
 * keeps its materials and reassigns its plan).
 *
 * <p>Single abstract method with vanilla-typed parameters, so reflective mods can implement it
 * with a {@link java.lang.reflect.Proxy}.
 */
@FunctionalInterface
public interface BuildPlanProvider {

   BuildPlan generate(ServerLevel world, BlockPos origin, RandomSource random,
                      String biomeKey, Direction facing);
}
