package justfatlard.village_builder.util;

import java.util.Optional;
import justfatlard.village_builder.Main;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy;

public class BuildersTableFinder {
   public static BlockPos findNearestBuildersTable(ServerLevel world, BlockPos center) {
      return findNearestBuildersTable(world, center, 64);
   }

   public static BlockPos findNearestBuildersTable(ServerLevel world, BlockPos center, int radius) {
      PoiManager poiStorage = world.getPoiManager();
      Optional<BlockPos> nearest = poiStorage.findClosest(poi -> poi.is(Main.BUILDERS_TABLE_POI_KEY), center, radius, Occupancy.ANY);
      return nearest.orElse(null);
   }
}
