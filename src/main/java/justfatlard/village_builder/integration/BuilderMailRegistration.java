package justfatlard.village_builder.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.building.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuilderMailRegistration {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");
   private static final int VILLAGE_MAIL_RADIUS = 96;
   private static final String OVERWORLD_DIMENSION = "minecraft:overworld";
   private static final Map<BlockPos, Long> lastMailSent = new HashMap<>();
   private static final long MAIL_COOLDOWN_TICKS = 24000L;
   private static final Map<BlockPos, Integer> buildCounts = new HashMap<>();

   public static void register() {
      LOGGER.info("Village-mail integration active (construction notifications)");
   }

   public static void notifyConstructionComplete(
      MinecraftServer server, BlockPos villageCenter, String structureName, String villageName, UUID patronUuid, String patronName, boolean offTheme
   ) {
      if (VillageMailIntegration.AVAILABLE) {
         buildCounts.putIfAbsent(villageCenter, Main.VILLAGE_DATA_MANAGER.getBuiltStructureCount(villageCenter));
         int count = buildCounts.merge(villageCenter, 1, Integer::sum);
         if (patronUuid != null && patronName != null && ThreadLocalRandom.current().nextDouble() < 0.25) {
            String patronBody = pickPatronLetter(structureName, patronName, offTheme);
            String builderName = getBuilderName(villageCenter);
            VillageMailIntegration.sendMessage(server, patronUuid, builderName, patronBody);
         }

         if (count <= 1 || count % 3 == 0) {
            long currentTick = server.getTickCount();
            Long lastSent = lastMailSent.get(villageCenter);
            if (lastSent == null || currentTick - lastSent >= 24000L) {
               String builderName = getBuilderName(villageCenter);
               String body = pickConstructionLetter(structureName, count, villageName);
               List<UUID> residents = findTrustedResidents(server, villageCenter);

               for (UUID resident : residents) {
                  if (!resident.equals(patronUuid)) {
                     VillageMailIntegration.sendMessage(server, resident, builderName, body);
                  }
               }

               if (!residents.isEmpty()) {
                  lastMailSent.put(villageCenter, currentTick);
                  LOGGER.debug("Sent construction mail to {} residents (build #{})", residents.size(), count);
               }
            }
         }
      }
   }

   public static void notifyConstructionComplete(MinecraftServer server, BlockPos villageCenter, String structureName, String villageName) {
      notifyConstructionComplete(server, villageCenter, structureName, villageName, null, null, false);
   }

   private static String pickPatronLetter(String structureName, String patronName, boolean offTheme) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      if (offTheme) {
         String[] letters = new String[]{
            "The " + structureName + " is up. Your choice, not mine. Looks... different. The villagers are polite about it.",
            "Built your " + structureName + ". Doesn't match anything else here but the walls are straight. That's on me. The style is on you.",
            "Finished the " + structureName + ". " + patronName + ", I trust you had your reasons. The roof doesn't match the others but it doesn't leak.",
            "Your " + structureName + " is done. It stands out. I'm being generous with 'stands out.' But it's solid work.",
            "The " + structureName + " you wanted is built. I'd have gone a different direction. But you didn't ask me, you told me. It's fine. It's fine."
         };
         return letters[rng.nextInt(letters.length)];
      } else {
         String[] letters = new String[]{
            "The " + structureName + " went up this morning. Good call on that one, " + patronName + ". Village needed it.",
            "Your " + structureName + " is done. Fits right in. Like it was always supposed to be there.",
            "Built the " + structureName + " you picked. Right choice. I was thinking the same thing but you said it first.",
            "The " + structureName + " is finished. " + patronName + ", you've got a good eye for what this village needs."
         };
         return letters[rng.nextInt(letters.length)];
      }
   }

   public static void notifyNewPlan(
      MinecraftServer server, BlockPos villageCenter, String planName, String villageName, List<StructureType.MaterialRequirement> requirements
   ) {
   }

   public static void checkMilestoneMail(MinecraftServer server, BlockPos villageCenter, int totalBuilds) {
      if (VillageMailIntegration.AVAILABLE) {
         if (totalBuilds == 5 || totalBuilds == 10 || totalBuilds == 15 || totalBuilds == 20) {
            long currentTick = server.getTickCount();
            Long lastSent = lastMailSent.get(villageCenter);
            if (lastSent == null || currentTick - lastSent >= 24000L) {
               String builderName = getBuilderName(villageCenter);
               String villageName = VillageQuestsIntegration.getVillageName(villageCenter);
               String body = pickMilestoneLetter(totalBuilds, villageName);
               List<UUID> residents = findTrustedResidents(server, villageCenter);

               for (UUID resident : residents) {
                  VillageMailIntegration.sendMessage(server, resident, builderName, body);
               }

               if (!residents.isEmpty()) {
                  lastMailSent.put(villageCenter, currentTick);
               }
            }
         }
      }
   }

   private static String pickMilestoneLetter(int totalBuilds, String villageName) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      String village = villageName != null ? villageName : "the village";
      if (totalBuilds == 5) {
         String[] letters = new String[]{
            "Five buildings now. " + village + " is starting to look like something.",
            "Counted the rooftops today. Five. Doesn't sound like much, but it is.",
            "Five. The paths between buildings are becoming worn. That means people use them."
         };
         return letters[rng.nextInt(letters.length)];
      } else if (totalBuilds == 10) {
         String[] letters = new String[]{
            "Ten buildings. I remember when it was just the workshop and a well. " + village + " is real now.",
            "Double digits. The skyline from the hill is different than it was. I keep looking at it.",
            "Ten. Some days I forget which ones I built first. That's a good sign, I think."
         };
         return letters[rng.nextInt(letters.length)];
      } else if (totalBuilds == 15) {
         String[] letters = new String[]{
            "Fifteen. Children are growing up who don't remember " + village + " being small.",
            "I walked the village at night. Fifteen windows lit up. Took my breath a little.",
            "Fifteen buildings. My hands remember every one. My back remembers too."
         };
         return letters[rng.nextInt(letters.length)];
      } else {
         String[] letters = new String[]{
            "Twenty. I don't know what to say. " + village + " is bigger than what I planned.",
            "Twenty buildings. Someone asked me to build a monument. I told them the village is the monument.",
            "I sat on the hill today and counted. Twenty. Some of these walls will outlast me. I'm alright with that."
         };
         return letters[rng.nextInt(letters.length)];
      }
   }

   private static String pickConstructionLetter(String structureName, int buildCount, String villageName) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      if (buildCount == 1) {
         String[] first = new String[]{
            "Got the " + structureName + " up. Took a while on my own, but the walls are straight. Might need help with the next one.",
            "Finished the " + structureName + " this morning. Not bad for a first project. Village needed it.",
            "The " + structureName + " is done. Simple work, but it's standing. That's what matters."
         };
         return first[rng.nextInt(first.length)];
      } else {
         String[] later = new String[]{
            "Another one done. " + structureName + " this time. The village is filling out.",
            structureName + " went up at dawn. Easier when you know the ground.",
            "The " + structureName + " is finished. Getting faster at this.",
            "Put the last stones on the " + structureName + " today. Good spot for it."
         };
         return later[rng.nextInt(later.length)];
      }
   }

   private static String getBuilderName(BlockPos villageCenter) {
      String villageName = VillageQuestsIntegration.getVillageName(villageCenter);
      return villageName != null ? "Builder of " + villageName : "The Builder";
   }

   private static List<UUID> findTrustedResidents(MinecraftServer server, BlockPos villageCenter) {
      List<UUID> allResidents = VillageMailIntegration.findMailboxOwnersNear(server, villageCenter, "minecraft:overworld", 96);
      if (!VillageQuestsIntegration.AVAILABLE) {
         return allResidents;
      } else {
         List<UUID> trusted = new ArrayList<>();

         for (UUID residentUuid : allResidents) {
            ServerPlayer player = server.getPlayerList().getPlayer(residentUuid);
            if (player == null) {
               trusted.add(residentUuid);
            } else {
               int rep = VillageQuestsIntegration.getPlayerReputation(player, villageCenter);
               if (rep >= 25) {
                  trusted.add(residentUuid);
               }
            }
         }

         return trusted;
      }
   }

   public static void reset() {
      lastMailSent.clear();
      buildCounts.clear();
   }
}
