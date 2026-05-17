package justfatlard.village_builder.integration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageMailIntegration {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   public static final boolean AVAILABLE;
   private static Method sendMessageMethod;
   private static Method hasMailboxMethod;
   private static Method storageGetMethod;
   private static Method getMailboxOwnersMethod;
   private static Method getMailboxLocationMethod;
   private static Method locationDimensionMethod;
   private static Method locationPosMethod;

   public static void sendMessage(MinecraftServer server, UUID recipientUuid, String senderName, String body) {
      if (AVAILABLE && sendMessageMethod != null) {
         try {
            sendMessageMethod.invoke(null, server, recipientUuid, senderName, body);
         } catch (Exception var5) {
            LOGGER.warn("Failed to send mail — village-mail API may have changed: {}", var5.getMessage());
         }
      }
   }

   public static boolean hasMailbox(MinecraftServer server, UUID playerUuid) {
      if (AVAILABLE && hasMailboxMethod != null) {
         try {
            return hasMailboxMethod.invoke(null, server, playerUuid) instanceof Boolean b && b;
         } catch (Exception var4) {
            LOGGER.warn("Failed to check mailbox — village-mail API may have changed: {}", var4.getMessage());
            return false;
         }
      } else {
         return false;
      }
   }

   public static List<UUID> findMailboxOwnersNear(MinecraftServer server, BlockPos center, String dimension, int radius) {
      List<UUID> result = new ArrayList<>();
      if (AVAILABLE && storageGetMethod != null) {
         try {
            Object storage = storageGetMethod.invoke(null, server);
            Set<UUID> owners = (Set<UUID>)getMailboxOwnersMethod.invoke(storage);
            double radiusSq = (double)radius * radius;

            for (UUID owner : owners) {
               Optional<?> locOpt = (Optional<?>)getMailboxLocationMethod.invoke(storage, owner);
               if (!locOpt.isEmpty()) {
                  Object loc = locOpt.get();
                  String locDimension = (String)locationDimensionMethod.invoke(loc);
                  if (dimension.equals(locDimension)) {
                     BlockPos locPos = (BlockPos)locationPosMethod.invoke(loc);
                     if (center.distSqr(locPos) <= radiusSq) {
                        result.add(owner);
                     }
                  }
               }
            }
         } catch (Exception var15) {
            LOGGER.warn("Failed to query mailbox locations — village-mail API may have changed: {}", var15.getMessage());
         }

         return result;
      } else {
         return result;
      }
   }

   static {
      boolean loaded = false;
      if (FabricLoader.getInstance().isModLoaded("village-mail")) {
         try {
            Class<?> mailApi = Class.forName("justfatlard.village_mail.api.MailApi");
            Class<?> storageClass = Class.forName("justfatlard.village_mail.mail.PlayerMailStorage");
            sendMessageMethod = mailApi.getMethod("sendMessage", MinecraftServer.class, UUID.class, String.class, String.class);
            hasMailboxMethod = mailApi.getMethod("hasMailbox", MinecraftServer.class, UUID.class);
            storageGetMethod = storageClass.getMethod("get", MinecraftServer.class);
            getMailboxOwnersMethod = storageClass.getMethod("getMailboxOwners");
            getMailboxLocationMethod = storageClass.getMethod("getMailboxLocation", UUID.class);
            Class<?> locationClass = Class.forName("justfatlard.village_mail.mail.PlayerMailStorage$MailboxLocation");
            locationDimensionMethod = locationClass.getMethod("dimension");
            locationPosMethod = locationClass.getMethod("pos");
            loaded = true;
            LOGGER.info("village-mail integration loaded");
         } catch (ClassNotFoundException var4) {
            LOGGER.warn("village-mail mod is installed but API classes not found — integration disabled: {}", var4.getMessage());
         } catch (Exception var5) {
            LOGGER.warn("village-mail mod detected but API unavailable: {}", var5.getMessage());
         }
      }

      AVAILABLE = loaded;
   }
}
