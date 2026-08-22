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
   // Cap in-flight POI searches: during rapid chunk loading this prevents the
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
      pendingBells.remove(worldKey);
   }

   public static void trySpawnInVillage(ServerLevel world, BlockPos chunkPos) {
      int cx = chunkPos.getX() + 8;
      int cz = chunkPos.getZ() + 8;

      // Quantize to 128-block grid: one POI search covers a 48-block radius so
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
         // Server is busy with other bell scans; remove the zone so it can retry later.
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
      LOGGER.info("Found bell at {}, queued for a table", bellPos.get());

      RandomSource random = world.getRandom();
      if (random.nextInt(10) > 8) {
         worldBells.add(bellPos.get());
         LOGGER.info("Bell at {}: skipped by chance roll (10%% miss)", bellPos.get());
         return;
      }

      Optional<BlockPos> existingTable = poiStorage.findClosest(
         poi -> poi.is(Main.BUILDERS_TABLE_POI_KEY), bellPos.get(), 64, Occupancy.ANY
      );
      if (existingTable.isPresent()) {
         worldBells.add(bellPos.get());
         LOGGER.info("Bell at {} already has a table at {}", bellPos.get(), existingTable.get());
         return;
      }

      // Queued rather than placed here. This runs off a chunk-load callback, and
      // the bell can be 48 blocks from the chunk that triggered it, so the ground
      // around it is usually still unloaded: every sampled position lands in a
      // chunk getChunkNow cannot see and the whole attempt burns without placing
      // anything. Retrying over the following ticks lets the village load first.
      pendingBells.computeIfAbsent(world.dimension(), k -> new HashMap<>())
         .putIfAbsent(bellPos.get(), PLACEMENT_ATTEMPTS);
   }

   /**
    * Bells still owed a table, with attempts remaining.
    *
    * <p>A bell is only retired into {@code evaluatedBells} once it has a table or
    * has run out of attempts. Retiring it up front, as this used to, gave a bell
    * exactly one try at the worst possible moment and then never looked again:
    * seven bells found on this server in one day, seven failures, no table.
    */
   private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> pendingBells = new HashMap<>();

   private static final int PLACEMENT_ATTEMPTS = 20;
   private static final int RETRY_INTERVAL_TICKS = 40;

   /** Retries queued bells once the ground under them exists. */
   public static void tick(ServerLevel world) {
      if (world.getGameTime() % RETRY_INTERVAL_TICKS != 0) return;

      Map<BlockPos, Integer> pending = pendingBells.get(world.dimension());
      if (pending == null || pending.isEmpty()) return;

      Set<BlockPos> worldBells = evaluatedBells.computeIfAbsent(world.dimension(), k -> new LinkedHashSet<>());
      Iterator<Map.Entry<BlockPos, Integer>> it = pending.entrySet().iterator();

      while (it.hasNext()) {
         Map.Entry<BlockPos, Integer> entry = it.next();
         BlockPos bell = entry.getKey();

         BlockPos tablePos = findSuitableLocation(world, bell, world.getRandom());
         if (tablePos != null) {
            world.setBlockAndUpdate(tablePos, Main.BUILDERS_TABLE_BLOCK.defaultBlockState());
            LOGGER.info("Spawned a table at {} near bell {}", tablePos, bell);
            worldBells.add(bell);
            it.remove();
            continue;
         }

         int left = entry.getValue() - 1;
         if (left <= 0) {
            LOGGER.warn("Bell at {}: gave up after {} attempts", bell, PLACEMENT_ATTEMPTS);
            worldBells.add(bell);
            it.remove();
         } else {
            entry.setValue(left);
         }
      }
   }

   /** How far above or below the bell a table may sit, so it stays out of the rooftops. */
   private static final int MAX_HEIGHT_FROM_BELL = 4;

   private static BlockPos findSuitableLocation(ServerLevel world, BlockPos center, RandomSource random) {
      for (int attempt = 0; attempt < 40; attempt++) {
         int x = center.getX() + random.nextIntBetweenInclusive(-20, 20);
         int z = center.getZ() + random.nextIntBetweenInclusive(-20, 20);
         // getChunkNow returns null if the chunk isn't fully loaded yet.
         // getHeight() would block waiting for full chunk status: deadlock inside
         // a chunk-load callback chain. Use the heightmap on the already-loaded chunk instead.
         LevelChunk chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
         if (chunk == null) continue;
         int y = chunk.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
         // The heightmap counts buildings, so without this the table lands on
         // whatever roof the sample happened to hit. The bell stands in the
         // square, so its height is what street level means here.
         if (Math.abs(y - center.getY()) > MAX_HEIGHT_FROM_BELL) continue;

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

   /**
    * How far down must be solid before a spot counts as ground.
    *
    * <p>A roof passes every other test: it is air with air above it and something solid
    * underneath, and it sits within {@link #MAX_HEIGHT_FROM_BELL} of a bell, because the bell
    * stands on its own raised platform and a village house is not tall. What a roof has that
    * ground does not is a room under it.
    */
   private static final int SOLID_DEPTH = 4;

   private static boolean isLocationSuitable(ServerLevel world, BlockPos pos) {
      if (!world.getBlockState(pos).isAir()) {
         return false;
      }
      if (!world.getBlockState(pos.above()).isAir()) {
         return false;
      }
      BlockState groundState = world.getBlockState(pos.below());
      if (!groundState.isSolid()) {
         return false;
      }

      return standsOnGround(world, pos);
   }

   /**
    * Whether this is ground rather than something built on it.
    *
    * <p>Two questions. Is it solid a good way down - a roof, a wall top, an awning and a tree
    * canopy all have a gap not far below, and ground does not. And is its neighbourhood solid
    * too - that rules out perching on a roof ridge, a fence post or a wall, which are one block
    * wide and would otherwise pass the first test on the way down through the wall.
    */
   private static boolean standsOnGround(ServerLevel world, BlockPos pos) {
      for (int depth = 1; depth <= SOLID_DEPTH; depth++) {
         if (!world.getBlockState(pos.below(depth)).isSolid()) {
            return false;
         }
      }

      for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
         if (!world.getBlockState(pos.relative(side).below()).isSolid()) {
            return false;
         }
      }
      return true;
   }
}
