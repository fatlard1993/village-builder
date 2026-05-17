package justfatlard.village_builder.world;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagePoolInjector {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final int WORKSHOP_WEIGHT = 3;
   private static final Map<String, String> BIOME_POOLS = Map.of(
      "plains",
      "minecraft:village/plains/houses",
      "taiga",
      "minecraft:village/taiga/houses",
      "desert",
      "minecraft:village/desert/houses",
      "savanna",
      "minecraft:village/savanna/houses",
      "snowy",
      "minecraft:village/snowy/houses"
   );

   public static void inject(MinecraftServer server) {
      Registry<StructureTemplatePool> poolRegistry = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
      int injected = 0;

      for (Entry<String, String> entry : BIOME_POOLS.entrySet()) {
         String biome = entry.getKey();
         Identifier poolId = Identifier.tryParse(entry.getValue());
         if (poolId != null) {
            StructureTemplatePool pool = poolRegistry.getValue(poolId);
            if (pool == null) {
               LOGGER.warn("Village pool not found: {} — skipping workshop injection", poolId);
            } else {
               String templatePath = "village-builder:builders_workshop_" + biome;
               if (addToPool(pool, templatePath, 3)) {
                  injected++;
                  LOGGER.debug("Injected {} into {}", templatePath, poolId);
               }
            }
         }
      }

      LOGGER.info("Injected Builder's Workshop into {} village pools", injected);
   }

   private static boolean addToPool(StructureTemplatePool pool, String templatePath, int weight) {
      try {
         StructurePoolElement element = (StructurePoolElement)SinglePoolElement.single(templatePath).apply(Projection.RIGID);
         if (pool.rawTemplates instanceof ArrayList) {
            pool.rawTemplates.add(Pair.of(element, weight));
         } else {
            ArrayList<Pair<StructurePoolElement, Integer>> mutable = new ArrayList<>(pool.rawTemplates);
            mutable.add(Pair.of(element, weight));
            pool.rawTemplates = mutable;
         }

         for (int i = 0; i < weight; i++) {
            pool.templates.add(element);
         }

         return true;
      } catch (Exception var5) {
         LOGGER.error("Failed to inject {} into pool", templatePath, var5);
         return false;
      }
   }
}
