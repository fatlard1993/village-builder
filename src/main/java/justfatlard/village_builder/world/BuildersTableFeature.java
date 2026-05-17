package justfatlard.village_builder.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import justfatlard.village_builder.Main;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildersTableFeature {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final int MAX_EVALUATED_BELLS = 10000;
   private static final Map<ResourceKey<Level>, Set<BlockPos>> evaluatedBells = new HashMap<>();

   public static void clearForWorld(ResourceKey<Level> worldKey) {
      evaluatedBells.remove(worldKey);
   }

   public static void trySpawnInVillage(ServerLevel world, BlockPos chunkPos) {
      PoiManager poiStorage = world.getPoiManager();
      Optional<BlockPos> bellPos = poiStorage.findClosest(poi -> poi.is(PoiTypes.MEETING), chunkPos, 48, Occupancy.ANY);
      if (!bellPos.isEmpty()) {
         Set<BlockPos> worldBells = evaluatedBells.computeIfAbsent(world.dimension(), k -> new LinkedHashSet<>());
         if (!worldBells.contains(bellPos.get())) {
            while (worldBells.size() >= 10000) {
               Iterator<BlockPos> iter = worldBells.iterator();
               iter.next();
               iter.remove();
            }

            worldBells.add(bellPos.get());
            RandomSource random = world.getRandom();
            if (random.nextInt(10) <= 8) {
               Optional<BlockPos> existingTable = poiStorage.findClosest(
                  poi -> poi.is(Main.BUILDERS_TABLE_POI_KEY), bellPos.get(), 64, Occupancy.ANY
               );
               if (!existingTable.isPresent()) {
                  BlockPos tablePos = findSuitableLocation(world, bellPos.get(), random);
                  if (tablePos != null) {
                     world.setBlockAndUpdate(tablePos, Main.BUILDERS_TABLE_BLOCK.defaultBlockState());
                     LOGGER.info("Spawned Builder's Table at {} in village", tablePos);
                  }
               }
            }
         }
      }
   }

   private static BlockPos findSuitableLocation(ServerLevel world, BlockPos center, RandomSource random) {
      for (int attempt = 0; attempt < 20; attempt++) {
         int x = center.getX() + random.nextIntBetweenInclusive(-15, 15);
         int z = center.getZ() + random.nextIntBetweenInclusive(-15, 15);
         int y = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         BlockPos groundPos = new BlockPos(x, y, z);
         if (isLocationSuitable(world, groundPos)) {
            AABB searchBox = new AABB(groundPos).inflate(10.0);
            List<Villager> nearbyVillagers = world.getEntitiesOfClass(Villager.class, searchBox, v -> true);
            if (!nearbyVillagers.isEmpty() || attempt > 10) {
               return groundPos;
            }
         }
      }

      return null;
   }

   private static boolean isLocationSuitable(ServerLevel world, BlockPos pos) {
      if (!world.getBlockState(pos).isAir()) {
         return false;
      } else {
         BlockState groundState = world.getBlockState(pos.below());
         if (!groundState.isSolid()) {
            return false;
         } else if (groundState.getBlock() != Blocks.FARMLAND && groundState.getBlock() != Blocks.DIRT_PATH) {
            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (dx != 0 || dz != 0) {
                     BlockPos checkPos = pos.offset(dx, 0, dz);
                     if (!world.getBlockState(checkPos).isAir()) {
                        return false;
                     }
                  }
               }
            }

            return true;
         } else {
            return false;
         }
      }
   }
}
