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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildersTableFeature {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final int MAX_EVALUATED_BELLS = 10000;
   // Cap in-flight POI searches — during rapid chunk loading this prevents the
   // server execute queue from filling with dozens of concurrent bell scans.
   private static final int MAX_PENDING_SEARCHES = 4;
   private static final java.util.concurrent.atomic.AtomicInteger pendingSearches = new java.util.concurrent.atomic.AtomicInteger(0);
   private static final Map<ResourceKey<Level>, Set<BlockPos>> evaluatedBells = new HashMap<>();
   // Quantized grid positions (128-block cells) already scheduled for a POI search.
   // The POI search radius is 48 blocks; one search per 128×128 cell is sufficient.
   private static final Map<ResourceKey<Level>, Set<Long>> queriedZones = new HashMap<>();

   public static void clearForWorld(ResourceKey<Level> worldKey) {
      evaluatedBells.remove(worldKey);
      queriedZones.remove(worldKey);
   }

   public static void trySpawnInVillage(ServerLevel world, BlockPos chunkPos) {
      int cx = chunkPos.getX() + 8;
      int cz = chunkPos.getZ() + 8;

      // Quantize to 128-block grid — one POI search covers a 48-block radius so
      // there is no benefit firing more than once per 128×128 cell.
      long zoneKey = ((long)(cx >> 7) << 32) | ((cz >> 7) & 0xFFFFFFFFL);
      Set<Long> zones = queriedZones.computeIfAbsent(world.dimension(), k -> new java.util.HashSet<>());
      if (!zones.add(zoneKey)) {
         return; // already scheduled or searched this cell
      }

      // Also skip if we already know a bell is nearby (fast path for known villages).
      Set<BlockPos> worldBells = evaluatedBells.computeIfAbsent(world.dimension(), k -> new LinkedHashSet<>());
      for (BlockPos knownBell : worldBells) {
         int dx = knownBell.getX() - cx;
         int dz = knownBell.getZ() - cz;
         if (dx * dx + dz * dz < 128 * 128) {
            return;
         }
      }

      if (pendingSearches.get() >= MAX_PENDING_SEARCHES) {
         // Server is busy with other bell scans — remove the zone so it can retry later.
         zones.remove(zoneKey);
         return;
      }
      BlockPos searchCenter = new BlockPos(cx, world.getSeaLevel() + 40, cz);
      pendingSearches.incrementAndGet();
      world.getServer().execute(() -> {
         try {
            spawnNearBell(world, searchCenter);
         } finally {
            pendingSearches.decrementAndGet();
         }
      });
   }

   private static void spawnNearBell(ServerLevel world, BlockPos searchCenter) {
      PoiManager poiStorage = world.getPoiManager();
      Optional<BlockPos> bellPos = poiStorage.findClosest(poi -> poi.is(PoiTypes.MEETING), searchCenter, 48, Occupancy.ANY);
      LOGGER.debug("[VB] spawnNearBell: bells found near {}: {}", searchCenter, bellPos.isPresent() ? 1 : 0);
      if (bellPos.isEmpty()) {
         return;
      }

      Set<BlockPos> worldBells = evaluatedBells.computeIfAbsent(world.dimension(), k -> new LinkedHashSet<>());
      if (worldBells.contains(bellPos.get())) {
         return;
      }

      while (worldBells.size() >= MAX_EVALUATED_BELLS) {
         Iterator<BlockPos> iter = worldBells.iterator();
         iter.next();
         iter.remove();
      }
      worldBells.add(bellPos.get());

      LOGGER.info("Found bell at {}, checking for table", bellPos.get());

      RandomSource random = world.getRandom();
      if (random.nextInt(10) > 8) {
         LOGGER.info("Bell at {} — skipped by chance roll (10%% miss)", bellPos.get());
         return;
      }

      Optional<BlockPos> existingTable = poiStorage.findClosest(
         poi -> poi.is(Main.BUILDERS_TABLE_POI_KEY), bellPos.get(), 64, Occupancy.ANY
      );
      if (existingTable.isPresent()) {
         LOGGER.info("Bell at {} already has a Builder's Table at {}", bellPos.get(), existingTable.get());
         return;
      }

      BlockPos tablePos = findSuitableLocation(world, bellPos.get(), random);
      if (tablePos == null) {
         LOGGER.warn("Bell at {} — no suitable location found in 40 attempts", bellPos.get());
         return;
      }

      world.setBlockAndUpdate(tablePos, Main.BUILDERS_TABLE_BLOCK.defaultBlockState());
      LOGGER.info("Spawned Builder's Table at {} near bell {}", tablePos, bellPos.get());
   }

   private static BlockPos findSuitableLocation(ServerLevel world, BlockPos center, RandomSource random) {
      for (int attempt = 0; attempt < 40; attempt++) {
         int x = center.getX() + random.nextIntBetweenInclusive(-20, 20);
         int z = center.getZ() + random.nextIntBetweenInclusive(-20, 20);
         // getChunkNow returns null if the chunk isn't fully loaded yet.
         // getHeight() would block waiting for full chunk status — deadlock inside
         // a chunk-load callback chain. Use the heightmap on the already-loaded chunk instead.
         LevelChunk chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
         if (chunk == null) continue;
         int y = chunk.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
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
      }
      if (!world.getBlockState(pos.above()).isAir()) {
         return false;
      }
      BlockState groundState = world.getBlockState(pos.below());
      return groundState.isSolid();
   }
}
