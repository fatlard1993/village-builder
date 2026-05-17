package justfatlard.village_builder.building;

import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import justfatlard.village_builder.Main;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuildingManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final Rotation[] ROTATIONS = Rotation.values();
   private static final Map<StructureType, Blueprint> BLUEPRINTS = Map.of(
      StructureType.BUILDERS_WORKSHOP,
      new Blueprint(
         5, 5, 3,
         Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.OAK_PLANKS,
         2,
         Map.of(new BlockPos(2, 0, 2), Main.BUILDERS_TABLE_BLOCK, new BlockPos(1, 0, 1), Blocks.TORCH)
      )
   );

   private boolean isBuildAreaLoaded(ServerLevel world, BlockPos origin, int width, int depth) {
      int minChunkX = origin.getX() >> 4;
      int maxChunkX = (origin.getX() + width - 1) >> 4;
      int minChunkZ = origin.getZ() >> 4;
      int maxChunkZ = (origin.getZ() + depth - 1) >> 4;
      for (int cx = minChunkX; cx <= maxChunkX; cx++) {
         for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            if (!world.hasChunk(cx, cz)) {
               return false;
            }
         }
      }
      return true;
   }

   private int getGroundY(ServerLevel world, BlockPos origin, int width, int depth) {
      int centerX = origin.getX() + width / 2;
      int centerZ = origin.getZ() + depth / 2;
      return world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ);
   }

   public boolean buildStructure(ServerLevel world, BlockPos pos, StructureType type, BlockPos villageCenter) {
      if (this.placeFromTemplate(world, pos, type, villageCenter)) {
         LOGGER.debug("Placed {} from structure template", type.getId());
         return true;
      }
      LOGGER.debug("No template for {}, using hardcoded placement", type.getId());
      return this.buildFromBlueprint(world, pos, type, villageCenter);
   }

   public boolean placeTemplate(ServerLevel world, BlockPos pos, Identifier templateId, BlockPos villageCenter) {
      StructureTemplateManager templateManager = world.getStructureManager();
      Optional<StructureTemplate> template = templateManager.get(templateId);
      if (template.isEmpty()) {
         LOGGER.warn("Template not found: {}", templateId);
         return false;
      }

      Vec3i size = template.get().getSize();
      if (!this.isBuildAreaLoaded(world, pos, size.getX(), size.getZ())) {
         LOGGER.warn("Build area at {} not fully loaded for template {}, aborting", pos, templateId);
         return false;
      }

      Rotation rotation = ROTATIONS[world.getRandom().nextInt(ROTATIONS.length)];
      int prepWidth = size.getX();
      int prepDepth = size.getZ();
      if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
         prepWidth = size.getZ();
         prepDepth = size.getX();
      }

      int groundY = this.getGroundY(world, pos, prepWidth, prepDepth);
      BlockPos groundPos = new BlockPos(pos.getX(), groundY, pos.getZ());
      if (!this.prepareTerrainForBuilding(world, groundPos, prepWidth, prepDepth)) {
         LOGGER.warn("Terrain prep failed for template {} at {}, aborting placement", templateId, groundPos);
         return false;
      }

      try {
         boolean hasEntities = !template.get().entityInfoList.isEmpty();
         boolean flipped = isAprilFools() && !hasEntities && size.getY() >= 2 && world.getRandom().nextInt(4) == 0;
         if (flipped) {
            this.placeFlipped(world, template.get(), groundPos, size.getY());
            LOGGER.info("April Fools! Placed {} upside down at {}", templateId, groundPos);
         } else {
            StructurePlaceSettings placementData = new StructurePlaceSettings()
               .setMirror(Mirror.NONE)
               .setRotation(rotation)
               .setIgnoreEntities(false)
               .addProcessor(JigsawReplacementProcessor.INSTANCE);
            template.get().placeInWorld(world, groundPos, groundPos, placementData, world.getRandom(), 2);
         }

         this.connectToNearestPath(world, groundPos, prepWidth, prepDepth, villageCenter);
         return true;
      } catch (Exception e) {
         LOGGER.error("Template placement failed at {} for {}: {}", groundPos, templateId, e.getMessage());
         return false;
      }
   }

   private boolean placeFromTemplate(ServerLevel world, BlockPos pos, StructureType type, BlockPos villageCenter) {
      return this.placeTemplate(world, pos, Identifier.fromNamespaceAndPath("village-builder", type.getId()), villageCenter);
   }

   private boolean buildFromBlueprint(ServerLevel world, BlockPos pos, StructureType type, BlockPos villageCenter) {
      Blueprint bp = BLUEPRINTS.get(type);
      if (bp == null) {
         LOGGER.warn("No blueprint defined for structure type: {}", type.getId());
         return false;
      }
      return bp.wallHeight == 0 ? this.buildFlatStructure(world, pos, bp, villageCenter) : this.buildEnclosedStructure(world, pos, bp, villageCenter);
   }

   private Direction findBestDoorFacing(ServerLevel world, BlockPos pos, Blueprint bp) {
      int bestAir = -1;
      Direction best = Direction.SOUTH;
      int midX = bp.width / 2;
      int midZ = bp.depth / 2;
      int southAir = this.countAirInFront(world, pos.offset(midX, 0, -1), Direction.SOUTH);
      int northAir = this.countAirInFront(world, pos.offset(midX, 0, bp.depth), Direction.NORTH);
      int eastAir = this.countAirInFront(world, pos.offset(-1, 0, midZ), Direction.EAST);
      int westAir = this.countAirInFront(world, pos.offset(bp.width, 0, midZ), Direction.WEST);
      if (southAir > bestAir) {
         bestAir = southAir;
         best = Direction.SOUTH;
      }
      if (northAir > bestAir) {
         bestAir = northAir;
         best = Direction.NORTH;
      }
      if (eastAir > bestAir) {
         bestAir = eastAir;
         best = Direction.EAST;
      }
      if (westAir > bestAir) {
         best = Direction.WEST;
      }
      return best;
   }

   private int countAirInFront(ServerLevel world, BlockPos start, Direction dir) {
      int count = 0;
      BlockPos check = start;
      for (int i = 0; i < 3; i++) {
         if (world.getBlockState(check).isAir()) {
            count++;
         }
         check = check.relative(dir.getOpposite());
      }
      return count;
   }

   private boolean buildFlatStructure(ServerLevel world, BlockPos pos, Blueprint bp, BlockPos villageCenter) {
      int groundY = this.getGroundY(world, pos, bp.width, bp.depth);
      pos = new BlockPos(pos.getX(), groundY, pos.getZ());
      if (!this.isBuildAreaLoaded(world, pos, bp.width, bp.depth)) {
         LOGGER.warn("Build area at {} not fully loaded, aborting", pos);
         return false;
      }
      if (!this.prepareTerrainForBuilding(world, pos, bp.width, bp.depth)) {
         return false;
      }

      for (int x = 0; x < bp.width; x++) {
         for (int z = 0; z < bp.depth; z++) {
            world.setBlock(pos.offset(x, -1, z), bp.floorBlock.defaultBlockState(), 2);
            if (bp.floorBlock == Blocks.DIRT) {
               world.setBlock(pos.offset(x, 0, z), Blocks.FARMLAND.defaultBlockState(), 2);
            }
         }
      }

      this.placeInteriorBlocks(world, pos, bp, Direction.NORTH);
      this.connectToNearestPath(world, pos, bp.width, bp.depth, villageCenter);
      return true;
   }

   private boolean buildEnclosedStructure(ServerLevel world, BlockPos pos, Blueprint bp, BlockPos villageCenter) {
      int groundY = this.getGroundY(world, pos, bp.width, bp.depth);
      pos = new BlockPos(pos.getX(), groundY, pos.getZ());
      if (!this.isBuildAreaLoaded(world, pos, bp.width, bp.depth)) {
         LOGGER.warn("Build area at {} not fully loaded, aborting", pos);
         return false;
      }
      if (!this.prepareTerrainForBuilding(world, pos, bp.width, bp.depth)) {
         return false;
      }

      Direction doorFacing = this.findBestDoorFacing(world, pos, bp);
      int doorWallX = -1;
      int doorWallZ = -1;
      if (bp.doorX >= 0) {
         switch (doorFacing) {
            case SOUTH:
               doorWallX = bp.doorX;
               doorWallZ = 0;
               break;
            case NORTH:
               doorWallX = bp.doorX;
               doorWallZ = bp.depth - 1;
               break;
            case WEST:
               doorWallX = bp.width - 1;
               doorWallZ = bp.doorX;
               break;
            case EAST:
               doorWallX = 0;
               doorWallZ = bp.doorX;
               break;
            default:
               doorWallX = bp.doorX;
               doorWallZ = 0;
         }
      }

      int lastX = bp.width - 1;
      int lastZ = bp.depth - 1;

      for (int x = 0; x < bp.width; x++) {
         for (int z = 0; z < bp.depth; z++) {
            // Floor
            world.setBlock(pos.offset(x, -1, z), bp.floorBlock.defaultBlockState(), 2);
            // Walls
            if (x == 0 || x == lastX || z == 0 || z == lastZ) {
               for (int y = 0; y < bp.wallHeight; y++) {
                  if (bp.doorX < 0 || x != doorWallX || z != doorWallZ || y >= 2) {
                     world.setBlock(pos.offset(x, y, z), bp.wallBlock.defaultBlockState(), 2);
                  }
               }
            }
            // Roof
            world.setBlock(pos.offset(x, bp.wallHeight, z), bp.roofBlock.defaultBlockState(), 2);
         }
      }

      // Door
      if (bp.doorX >= 0) {
         world.setBlock(
            pos.offset(doorWallX, 0, doorWallZ),
            Blocks.OAK_DOOR.defaultBlockState()
               .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
               .setValue(DoorBlock.FACING, doorFacing),
            2
         );
         world.setBlock(
            pos.offset(doorWallX, 1, doorWallZ),
            Blocks.OAK_DOOR.defaultBlockState()
               .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
               .setValue(DoorBlock.FACING, doorFacing),
            2
         );
      }

      this.placeInteriorBlocks(world, pos, bp, doorFacing);
      this.connectToNearestPath(world, pos, bp.width, bp.depth, villageCenter);
      return true;
   }

   private void placeInteriorBlocks(ServerLevel world, BlockPos origin, Blueprint bp, Direction doorFacing) {
      for (Entry<BlockPos, Block> entry : bp.interiorBlocks.entrySet()) {
         BlockPos localPos = entry.getKey();
         Block block = entry.getValue();
         BlockPos rotatedLocal = rotateInteriorPos(localPos, bp.width, bp.depth, doorFacing);
         BlockPos worldPos = origin.offset(rotatedLocal);
         if (block instanceof BedBlock) {
            Direction bedFacing = doorFacing.getOpposite();
            world.setBlock(
               worldPos,
               block.defaultBlockState()
                  .setValue(BedBlock.PART, BedPart.FOOT)
                  .setValue(BedBlock.FACING, bedFacing),
               2
            );
            BlockPos headPos = worldPos.relative(bedFacing);
            BlockPos headOffset = headPos.subtract(origin);
            if (headOffset.getX() >= 1
               && headOffset.getX() < bp.width - 1
               && headOffset.getZ() >= 1
               && headOffset.getZ() < bp.depth - 1) {
               world.setBlock(
                  headPos,
                  block.defaultBlockState()
                     .setValue(BedBlock.PART, BedPart.HEAD)
                     .setValue(BedBlock.FACING, bedFacing),
                  2
               );
            }
         } else {
            world.setBlock(worldPos, block.defaultBlockState(), 2);
         }
      }
   }

   private static BlockPos rotateInteriorPos(BlockPos local, int width, int depth, Direction doorFacing) {
      int x = local.getX();
      int y = local.getY();
      int z = local.getZ();
      return switch (doorFacing) {
         case SOUTH -> local;
         case NORTH -> new BlockPos(width - 1 - x, y, depth - 1 - z);
         case WEST -> new BlockPos(depth - 1 - z, y, x);
         case EAST -> new BlockPos(z, y, width - 1 - x);
         default -> local;
      };
   }

   private static boolean isAprilFools() {
      MonthDay today = MonthDay.now();
      return today.getMonthValue() == 4 && today.getDayOfMonth() == 1;
   }

   private void placeFlipped(ServerLevel world, StructureTemplate template, BlockPos origin, int height) {
      List<StructureBlockInfo> blockInfos = template.palettes.getFirst().blocks();
      int maxY = height - 1;

      for (StructureBlockInfo info : blockInfos) {
         if (!info.state().isAir()) {
            BlockPos flippedPos = origin.offset(info.pos().getX(), maxY - info.pos().getY(), info.pos().getZ());
            world.setBlock(flippedPos, info.state(), 2);
            if (info.nbt() != null && info.nbt().contains("LootTable")) {
               info.nbt().getString("LootTable").ifPresent(lootTableId -> {
                  long seed = info.nbt().getLongOr("LootTableSeed", 0L);
                  if (world.getBlockEntity(flippedPos) instanceof RandomizableContainer lootable) {
                     lootable.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(lootTableId)), seed);
                  }
               });
            }
         }
      }
   }

   private void connectToNearestPath(ServerLevel world, BlockPos buildOrigin, int width, int depth, BlockPos villageCenter) {
      int centerX = buildOrigin.getX() + width / 2;
      int centerZ = buildOrigin.getZ() + depth / 2;
      int groundY = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ) - 1;
      BlockPos buildCenter = new BlockPos(centerX, groundY, centerZ);
      BlockPos nearestPath = null;
      double nearestDist = Double.MAX_VALUE;
      int searchRadius = 32;

      for (int dx = -searchRadius; dx <= searchRadius; dx += 2) {
         for (int dz = -searchRadius; dz <= searchRadius; dz += 2) {
            int sx = centerX + dx;
            int sz = centerZ + dz;
            if (sx < buildOrigin.getX()
               || sx >= buildOrigin.getX() + width
               || sz < buildOrigin.getZ()
               || sz >= buildOrigin.getZ() + depth) {
               int sy = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, sx, sz) - 1;
               BlockPos checkPos = new BlockPos(sx, sy, sz);
               if (world.getBlockState(checkPos).getBlock() == Blocks.DIRT_PATH) {
                  double dist = checkPos.distSqr(buildCenter);
                  if (dist < nearestDist) {
                     nearestDist = dist;
                     nearestPath = checkPos;
                  }
               }
            }
         }
      }

      if (nearestPath == null) {
         int villageCenterY = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, villageCenter.getX(), villageCenter.getZ()) - 1;
         nearestPath = new BlockPos(villageCenter.getX(), villageCenterY, villageCenter.getZ());
         LOGGER.debug("No existing path found near {}, connecting to village center at {}", buildOrigin, nearestPath);
      }

      int startX = Math.max(buildOrigin.getX(), Math.min(buildOrigin.getX() + width - 1, nearestPath.getX()));
      int startZ = Math.max(buildOrigin.getZ(), Math.min(buildOrigin.getZ() + depth - 1, nearestPath.getZ()));
      int endX = nearestPath.getX();
      int endZ = nearestPath.getZ();
      int ddx = Integer.signum(endX - startX);
      int ddz = Integer.signum(endZ - startZ);
      int cx = startX;
      int cz = startZ;
      int maxSteps = Math.abs(endX - startX) + Math.abs(endZ - startZ) + 2;

      for (int step = 0; step < maxSteps; step++) {
         int cy = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
         BlockPos pathPos = new BlockPos(cx, cy, cz);
         BlockState currentState = world.getBlockState(pathPos);
         Block currentBlock = currentState.getBlock();
         if (currentBlock == Blocks.DIRT || currentBlock == Blocks.GRASS_BLOCK || currentBlock == Blocks.COARSE_DIRT) {
            world.setBlock(pathPos, Blocks.DIRT_PATH.defaultBlockState(), 2);
         }

         if (cx == endX && cz == endZ) {
            break;
         }

         if (cx == endX || (cz != endZ && step % 2 != 0)) {
            if (cz != endZ) {
               cz += ddz;
            }
         } else {
            cx += ddx;
         }
      }

      LOGGER.debug("Connected building at {} to path at {}", buildOrigin, nearestPath);
   }

   private boolean prepareTerrainForBuilding(ServerLevel world, BlockPos origin, int width, int depth) {
      int totalY = 0;
      int sampleCount = 0;

      for (int x = 0; x < width; x++) {
         for (int z = 0; z < depth; z++) {
            if (!world.hasChunk((origin.getX() + x) >> 4, (origin.getZ() + z) >> 4)) {
               LOGGER.warn("Terrain prep aborted at {} — chunk unloaded during height sampling", origin);
               return false;
            }
            int surfaceY = world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, origin.getX() + x, origin.getZ() + z);
            totalY += surfaceY;
            sampleCount++;
         }
      }

      int avgY = sampleCount > 0 ? totalY / sampleCount : origin.getY();

      for (int x = -1; x <= width; x++) {
         for (int z = -1; z <= depth; z++) {
            if (!world.hasChunk((origin.getX() + x) >> 4, (origin.getZ() + z) >> 4)) {
               LOGGER.warn("Terrain prep aborted at {} — chunk unloaded mid-operation at column ({}, {})",
                  origin, origin.getX() + x, origin.getZ() + z);
               return false;
            }

            // Fill below average Y with cobblestone
            for (int y = avgY - 1; y >= avgY - 3; y--) {
               BlockPos fillPos = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
               BlockState state = world.getBlockState(fillPos);
               if (!state.isAir() && !state.canBeReplaced()) {
                  break;
               }
               world.setBlock(fillPos, Blocks.COBBLESTONE.defaultBlockState(), 2);
            }

            // Clear vegetation above
            if (x >= 0 && x < width && z >= 0 && z < depth) {
               for (int y = 0; y < 8; y++) {
                  BlockPos clearPos = new BlockPos(origin.getX() + x, avgY + y, origin.getZ() + z);
                  BlockState state = world.getBlockState(clearPos);
                  if (!state.isAir() && (state.canBeReplaced() || isVegetation(state))) {
                     world.setBlock(clearPos, Blocks.AIR.defaultBlockState(), 2);
                  }
               }
            }
         }
      }

      return true;
   }

   private static boolean isVegetation(BlockState state) {
      Block block = state.getBlock();
      return block instanceof VegetationBlock || block instanceof MushroomBlock || block == Blocks.SNOW;
   }

   private record Blueprint(
      int width, int depth, int wallHeight, Block floorBlock, Block wallBlock, Block roofBlock, int doorX, Map<BlockPos, Block> interiorBlocks
   ) {
   }
}
