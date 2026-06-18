package justfatlard.village_builder.mixin;

import java.util.List;
import java.util.UUID;
import justfatlard.village_builder.BuilderTrades;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.building.StructureEntry;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_builder.util.BuildersTableFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public class VillagerEntityMixin {
   private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");

   /**
    * After the data-driven trade set has been loaded into this villager's offers,
    * replace all offers with our dynamically priced ones for Builder villagers.
    *
    * updateTrades is called whenever the villager gains a level or is freshly
    * spawned. We inject at TAIL so the data-driven offers are already populated,
    * then we clear and replace with our runtime-generated set.
    */
   @Inject(method = "updateTrades", at = @At("TAIL"))
   private void onUpdateTrades(ServerLevel world, CallbackInfo ci) {
      Villager villager = (Villager)(Object)this;
      if (!villager.getVillagerData().profession().is(Main.BUILDER_KEY)) {
         return;
      }
      int villagerLevel = villager.getVillagerData().level();
      List<MerchantOffer> dynamic = BuilderTrades.buildDynamicOffers(
         world, villager.blockPosition(), villagerLevel);
      if (dynamic.isEmpty()) {
         return;
      }
      MerchantOffers offers = villager.getOffers();
      offers.clear();
      offers.addAll(dynamic);
   }

   @Inject(method = "rewardTradeXp", at = @At("TAIL"))
   private void onTradeComplete(MerchantOffer offer, CallbackInfo ci) {
      Villager villager = (Villager)(Object)this;
      if (villager.getVillagerData().profession().is(Main.BUILDER_KEY)) {
         if (villager.level() instanceof ServerLevel serverWorld) {
            BlockPos tablePos = BuildersTableFinder.findNearestBuildersTable(serverWorld, villager.blockPosition());
            if (tablePos == null) {
               LOGGER.warn("Builder villager completed trade but no builder's table found nearby");
               if (villager.getTradingPlayer() instanceof ServerPlayer player) {
                  player.sendSystemMessage(Component.translatable("message.village-builder.no_table_nearby"));
               }
            } else {
               Item firstBuyItem = offer.getItemCostA().item().value();
               int firstBuyCount = offer.getItemCostA().count();
               Item sellItem = offer.getResult().getItem();
               if (sellItem == Items.EMERALD && firstBuyItem != Items.EMERALD) {
                  Main.VILLAGE_DATA_MANAGER.addMaterialToVillage(serverWorld, tablePos, firstBuyItem, firstBuyCount);
                  LOGGER.debug("Village received {}x {} from material trade", firstBuyCount, firstBuyItem);
               } else if (firstBuyItem == Items.EMERALD && sellItem == Items.PAPER) {
                  StructurePlan plan = getPlanFromPaper(offer.getResult());
                  if (plan != null) {
                     if (villager.getTradingPlayer() instanceof ServerPlayer player) {
                        String planStructureId = plan.getStructureId().toString();

                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                           ItemStack stack = player.getInventory().getItem(i);
                           if (stack.getItem() == Items.PAPER && matchesPlanId(stack, planStructureId)) {
                              player.getInventory().removeItemNoUpdate(i);
                              break;
                           }
                        }
                     }

                     UUID patronUuid = null;
                     String patronName = null;
                     if (villager.getTradingPlayer() instanceof ServerPlayer buyer) {
                        patronUuid = buyer.getUUID();
                        patronName = buyer.getName().getString();
                     }

                     Main.VILLAGE_DATA_MANAGER.setVillagePlan(serverWorld, tablePos, plan, patronUuid, patronName);
                     LOGGER.debug("Village plan set to: {}", plan.getDisplayName());
                  } else {
                     LOGGER.warn("Could not determine structure from plan paper — structure may have been unregistered");
                     if (villager.getTradingPlayer() instanceof ServerPlayer player) {
                        player.sendSystemMessage(Component.translatable("message.village-builder.plan_invalid"));
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean matchesPlanId(ItemStack stack, String structureId) {
      CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
      if (customData == null) {
         return false;
      } else {
         CompoundTag nbt = customData.copyTag();
         String paperId = nbt.getString("village_builder_structure_id").orElse(null);
         return structureId.equals(paperId);
      }
   }

   private static StructurePlan getPlanFromPaper(ItemStack paper) {
      CustomData customData = paper.get(DataComponents.CUSTOM_DATA);
      if (customData == null) {
         return null;
      } else {
         CompoundTag nbt = customData.copyTag();
         String structureId = nbt.getString("village_builder_structure_id").orElse(null);
         if (structureId == null) {
            return null;
         } else {
            Identifier id = Identifier.tryParse(structureId);
            if (id != null) {
               StructureEntry entry = Main.STRUCTURE_REGISTRY.get(id);
               if (entry != null) {
                  return new StructurePlan(entry);
               }
            }

            StructureType type = StructureType.fromId(structureId);
            if (type != null) {
               return new StructurePlan(type);
            } else {
               if (!structureId.contains(":")) {
                  StructureEntry entry = Main.STRUCTURE_REGISTRY.get(Identifier.fromNamespaceAndPath("village-builder", structureId));
                  if (entry != null) {
                     return new StructurePlan(entry);
                  }
               }

               return null;
            }
         }
      }
   }
}
