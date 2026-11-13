package justfatlard.village_builder.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Which worlds villages grow in.
 *
 * <p>The overworld, and PvP Dimensions' arena overworld: a plot there is overworld ground with the
 * overworld's day and night, and it is where that mod plays this one's demo. Named by id rather
 * than by asking the dimension what it is like, so no other mod's skylit dimension starts growing
 * villages by accident, and no dependency on PvP Dimensions is needed to recognise it.
 *
 * <p>An arena plot is thrown away when its arena ends, so a village in one is kept in memory only
 * and never written to the save; see {@code Main.villages}.
 */
public final class VillageDimensions {
   private VillageDimensions() {}

   /** PvP Dimensions' overworld-sky arena dimension, where every overworld arena gets a plot. */
   public static final ResourceKey<Level> ARENA_OVERWORLD = ResourceKey.create(
      Registries.DIMENSION, Identifier.fromNamespaceAndPath("pvp-dimensions-justfatlard", "overworld"));

   /** Whether the Builder's Table, the Builder's Flag and dawn construction work here. */
   public static boolean isVillageWorld(Level world) {
      return world.dimension() == Level.OVERWORLD || isArena(world);
   }

   /** Whether this is the arena overworld, whose villages last only as long as their plot. */
   public static boolean isArena(Level world) {
      return world.dimension().equals(ARENA_OVERWORLD);
   }
}
