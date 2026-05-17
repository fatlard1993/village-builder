package justfatlard.village_builder.integration;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageQuestsIntegration {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   public static final boolean AVAILABLE;
   private static Method getVillageNameMethod;
   private static Method getPlayerReputationMethod;
   private static Method modifyPlayerReputationMethod;

   public static String getVillageName(BlockPos villageCenter) {
      if (AVAILABLE && getVillageNameMethod != null) {
         try {
            if (getVillageNameMethod.invoke(null, villageCenter) instanceof String name && !name.isEmpty()) {
               return name;
            }
         } catch (Exception var3) {
            LOGGER.warn("Failed to get village name — village-quests API may have changed: {}", var3.getMessage());
         }

         return null;
      } else {
         return null;
      }
   }

   public static int getPlayerReputation(ServerPlayer player, BlockPos villageCenter) {
      if (AVAILABLE && getPlayerReputationMethod != null) {
         try {
            if (getPlayerReputationMethod.invoke(null, player, villageCenter) instanceof Integer rep) {
               return rep;
            }
         } catch (Exception var4) {
            LOGGER.warn("Failed to get player reputation — village-quests API may have changed: {}", var4.getMessage());
         }

         return 0;
      } else {
         return 0;
      }
   }

   public static boolean modifyPlayerReputation(ServerPlayer player, BlockPos villageCenter, int amount, String reason) {
      if (AVAILABLE && modifyPlayerReputationMethod != null) {
         try {
            modifyPlayerReputationMethod.invoke(null, player, villageCenter, amount, reason);
            return true;
         } catch (Exception var5) {
            LOGGER.warn("Failed to modify player reputation — village-quests API may have changed: {}", var5.getMessage());
            return false;
         }
      } else {
         return false;
      }
   }

   static {
      boolean loaded = false;
      if (FabricLoader.getInstance().isModLoaded("village-quests-justfatlard")) {
         try {
            Class<?> villageAPI = Class.forName("justfatlard.village_quests.api.VillageQuestsAPI");

            try {
               getVillageNameMethod = villageAPI.getMethod("getVillageName", BlockPos.class);
            } catch (NoSuchMethodException var5) {
               LOGGER.warn("village-quests: getVillageName not found — village names unavailable");
            }

            try {
               getPlayerReputationMethod = villageAPI.getMethod("getPlayerReputation", ServerPlayer.class, BlockPos.class);
            } catch (NoSuchMethodException var4) {
               LOGGER.warn("village-quests: getPlayerReputation not found — reputation checks unavailable");
            }

            try {
               modifyPlayerReputationMethod = villageAPI.getMethod("modifyPlayerReputation", ServerPlayer.class, BlockPos.class, int.class, String.class);
            } catch (NoSuchMethodException var3) {
               LOGGER.warn("village-quests: modifyPlayerReputation not found — reputation modification unavailable");
            }

            loaded = true;
            LOGGER.info("village-quests integration loaded (names: {}, reputation: {})", getVillageNameMethod != null, modifyPlayerReputationMethod != null);
         } catch (ClassNotFoundException var6) {
            LOGGER.warn("village-quests mod is installed but its API class was not found — integration disabled.");
         } catch (Exception var7) {
            LOGGER.warn("village-quests mod detected but API unavailable: {}", var7.getMessage());
         }
      }

      AVAILABLE = loaded;
   }
}
