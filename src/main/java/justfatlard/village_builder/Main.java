package justfatlard.village_builder;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import justfatlard.village_builder.block.BuildersTableBlock;
import justfatlard.village_builder.block.BuildersTableBlockEntity;
import justfatlard.village_builder.building.BuildingManager;
import justfatlard.village_builder.building.StructureAnalyzer;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureRegistry;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_builder.integration.BuilderMailRegistration;
import justfatlard.village_builder.screen.BuildersTableData;
import justfatlard.village_builder.screen.BuildersTableScreenHandler;
import justfatlard.village_builder.village.VillageData;
import justfatlard.village_builder.village.VillageDataManager;
import justfatlard.village_builder.world.BuildersTableFeature;
import justfatlard.village_builder.world.VillagePoolInjector;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   public static final String MOD_ID = "village-builder";
   public static final Identifier BUILDERS_TABLE_ID = Identifier.fromNamespaceAndPath("village-builder", "builders_table");
   public static final Identifier BUILDER_ID = Identifier.fromNamespaceAndPath("village-builder", "builder");
   public static final ResourceKey<Block> BUILDERS_TABLE_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, BUILDERS_TABLE_ID);
   public static final ResourceKey<Item> BUILDERS_TABLE_ITEM_KEY = ResourceKey.create(Registries.ITEM, BUILDERS_TABLE_ID);
   public static final ResourceKey<PoiType> BUILDERS_TABLE_POI_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, BUILDERS_TABLE_ID);
   public static final ResourceKey<VillagerProfession> BUILDER_KEY = ResourceKey.create(Registries.VILLAGER_PROFESSION, BUILDER_ID);
   public static final ResourceKey<CreativeModeTab> ITEM_GROUP_KEY = ResourceKey.create(
      Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("village-builder", "village_builder")
   );
   public static final BuildersTableBlock BUILDERS_TABLE_BLOCK = new BuildersTableBlock();
   public static BlockEntityType<BuildersTableBlockEntity> BUILDERS_TABLE_BLOCK_ENTITY;
   public static final BlockItem BUILDERS_TABLE_ITEM = new BlockItem(
      BUILDERS_TABLE_BLOCK, new Item.Properties().setId(BUILDERS_TABLE_ITEM_KEY).useItemDescriptionPrefix()
   );
   public static PoiType BUILDERS_TABLE_POI;
   public static VillagerProfession BUILDER;
   public static ExtendedMenuType<BuildersTableScreenHandler, BuildersTableData> BUILDERS_TABLE_SCREEN_HANDLER;
   public static final BuildingManager BUILDING_MANAGER = new BuildingManager();
   public static final VillageDataManager VILLAGE_DATA_MANAGER = new VillageDataManager();
   public static final StructureRegistry STRUCTURE_REGISTRY = new StructureRegistry();

   public void onInitialize() {
      Registry.register(BuiltInRegistries.BLOCK, BUILDERS_TABLE_ID, BUILDERS_TABLE_BLOCK);
      BUILDERS_TABLE_BLOCK_ENTITY = Registry.register(
         BuiltInRegistries.BLOCK_ENTITY_TYPE,
         BUILDERS_TABLE_ID,
         FabricBlockEntityTypeBuilder.create(BuildersTableBlockEntity::new, BUILDERS_TABLE_BLOCK).build()
      );
      Registry.register(BuiltInRegistries.ITEM, BUILDERS_TABLE_ID, BUILDERS_TABLE_ITEM);
      BUILDERS_TABLE_SCREEN_HANDLER = Registry.register(
         BuiltInRegistries.MENU, BUILDERS_TABLE_ID, new ExtendedMenuType<>(BuildersTableScreenHandler::new, BuildersTableData.CODEC)
      );
      BUILDERS_TABLE_POI = Registry.register(
         BuiltInRegistries.POINT_OF_INTEREST_TYPE,
         BUILDERS_TABLE_ID,
         new PoiType(ImmutableSet.copyOf(BUILDERS_TABLE_BLOCK.getStateDefinition().getPossibleStates()), 1, 48)
      );
      BUILDER = Registry.register(
         BuiltInRegistries.VILLAGER_PROFESSION,
         BUILDER_ID,
         new VillagerProfession(
            Component.translatable("entity.minecraft.villager.village-builder.builder"),
            entry -> entry.is(BUILDERS_TABLE_POI_KEY),
            entry -> entry.is(BUILDERS_TABLE_POI_KEY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_MASON,
            Int2ObjectMaps.emptyMap()
         )
      );
      BuilderTrades.register();
      ServerLevelEvents.LOAD.register((server, world) -> {
         if (world.dimension() == Level.OVERWORLD) {
            STRUCTURE_REGISTRY.clear();
            STRUCTURE_REGISTRY.registerBuildersWorkshops();
            STRUCTURE_REGISTRY.registerSeasonalStructures();
            StructureAnalyzer.discoverModStructures(world, STRUCTURE_REGISTRY);
            STRUCTURE_REGISTRY.runReloadCallbacks();
            STRUCTURE_REGISTRY.markInitialized();
            VILLAGE_DATA_MANAGER.initialize(world);
            VillagePoolInjector.inject(server);
         }
      });
      ServerLevelEvents.UNLOAD.register((server, world) -> {
         BuildersTableFeature.clearForWorld(world.dimension());
         if (world.dimension() == Level.OVERWORLD) {
            VILLAGE_DATA_MANAGER.reset();
            STRUCTURE_REGISTRY.clear();
         }
      });
      ServerTickEvents.END_LEVEL_TICK.register(world -> {
         if (world instanceof ServerLevel && world.dimension() == Level.OVERWORLD) {
            VILLAGE_DATA_MANAGER.tick(world);
         }
      });
      ServerChunkEvents.CHUNK_LOAD.register((world, chunk, newlyGenerated) -> {
         if (world.dimension() == Level.OVERWORLD) {
            BuildersTableFeature.trySpawnInVillage(world, chunk.getPos().getWorldPosition());
         }
      });
      CreativeModeTab builderGroup = FabricCreativeModeTab.builder()
         .icon(() -> new ItemStack(BUILDERS_TABLE_ITEM))
         .title(Component.translatable("itemGroup.village-builder.village_builder"))
         .displayItems((context, entries) -> entries.accept(new ItemStack(BUILDERS_TABLE_ITEM)))
         .build();
      Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ITEM_GROUP_KEY, builderGroup);
      if (FabricLoader.getInstance().isModLoaded("village-quests-justfatlard")) {
         try {
            Class.forName("justfatlard.village_builder.integration.BuilderQuestRegistration").getMethod("register").invoke(null);
            LOGGER.info("Registered builder quests with village-quests");
         } catch (Exception var3) {
            LOGGER.warn("Failed to register builder quests: {}", var3.getMessage());
         }
      }

      if (FabricLoader.getInstance().isModLoaded("village-mail")) {
         BuilderMailRegistration.register();
         LOGGER.info("Registered mail structures and notifications with village-mail");
      }

      CommandRegistrationCallback.EVENT
         .register(
            (dispatcher, registryAccess, environment) -> dispatcher.register(
               Commands.literal("villagebuilder")
                  .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                  .then(
                     Commands.literal("status")
                        .executes(
                           context -> {
                              CommandSourceStack source = context.getSource();
                              ServerLevel world = source.getLevel();
                              BlockPos playerPos = BlockPos.containing(source.getPosition());
                              VillageData data = VILLAGE_DATA_MANAGER.getExistingVillageData(world, playerPos);
                              if (data == null) {
                                 source.sendSuccess(() -> Component.literal("No village found near your position."), false);
                                 return 0;
                              } else {
                                 StructurePlan plan = data.getCurrentPlan();
                                 String planName = plan != null ? plan.getDisplayName() : "none";
                                 float completion = data.getCompletionPercentage() * 100.0F;
                                 int builtCount = data.getBuiltStructures().size();
                                 source.sendSuccess(
                                    () -> Component.literal(
                                       String.format(
                                          "Village at %s\n  Plan: %s (%.0f%% complete)\n  Built structures: %d\n  Gathering index: %d",
                                          data.getVillageCenter().toShortString(),
                                          planName,
                                          completion,
                                          builtCount,
                                          data.getGatheringIndex()
                                       )
                                    ),
                                    false
                                 );
                                 if (plan != null) {
                                    StringBuilder materials = new StringBuilder("  Materials:");

                                    for (StructureType.MaterialRequirement req : plan.getRequirements()) {
                                       int have = data.getMaterialCount(req.item());
                                       materials.append(String.format("\n    %s: %d/%d", req.item().toString(), have, req.amount()));
                                    }

                                    source.sendSuccess(() -> Component.literal(materials.toString()), false);
                                 }

                                 return 1;
                              }
                           }
                        )
                  )
                  .then(Commands.literal("list").executes(context -> {
                     CommandSourceStack source = context.getSource();
                     int count = VILLAGE_DATA_MANAGER.getVillageCount();
                     source.sendSuccess(() -> Component.literal("Tracked villages: " + count), false);
                     return count;
                  }))
                  .then(Commands.literal("reassign").executes(context -> {
                     CommandSourceStack source = context.getSource();
                     ServerLevel world = source.getLevel();
                     BlockPos playerPos = BlockPos.containing(source.getPosition());
                     VillageData data = VILLAGE_DATA_MANAGER.getExistingVillageData(world, playerPos);
                     if (data == null) {
                        source.sendSuccess(() -> Component.literal("No village found near your position."), false);
                        return 0;
                     } else {
                        data.clearCurrentPlan();
                        data.clearAnalyzer();
                        VILLAGE_DATA_MANAGER.markPersistentDirty();
                        source.sendSuccess(() -> Component.literal("Plan cleared. A new plan will be assigned at next tick."), false);
                        return 1;
                     }
                  }))
            )
         );
      LOGGER.info("Loaded Village Builder mod");
   }
}
