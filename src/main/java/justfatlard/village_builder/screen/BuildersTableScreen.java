package justfatlard.village_builder.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class BuildersTableScreen extends AbstractContainerScreen<BuildersTableScreenHandler> {
   private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
   private static final Identifier PLAN_ICON = Identifier.fromNamespaceAndPath("village-builder", "textures/gui/plan_icon.png");
   private static final int VILLAGE_SLOT_COUNT = 27;

   public BuildersTableScreen(BuildersTableScreenHandler handler, Inventory inventory, Component title) {
      super(handler, inventory, title, 330, 166);
      this.inventoryLabelY = this.imageHeight - 94;
   }

   @Override
   public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, 176, 71, 256, 256);
      context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y + 54 + 17, 0.0F, 126.0F, 176, 96, 256, 256);
      context.fill(x + 176, y, x + this.imageWidth, y + this.imageHeight, -266198494);
      context.fill(x + 176, y, x + 177, y + this.imageHeight, -15658735);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      super.extractRenderState(context, mouseX, mouseY, delta);
      this.extractTooltip(context, mouseX, mouseY);

      int x = (this.width - this.imageWidth) / 2;
      int y = (this.height - this.imageHeight) / 2;
      BuildersTableData data = this.menu.getTableData();
      int xPos = x + 180;
      int yPos = y + 20;
      int panelWidth = this.imageWidth - 176 - 8;
      int panelBottom = y + this.imageHeight - 4;

      if (data != null && !data.planName().isEmpty()) {
         context.blit(RenderPipelines.GUI_TEXTURED, PLAN_ICON, xPos, yPos - 2, 0.0F, 0.0F, 10, 10, 16, 16);
         context.text(this.font, Component.translatable("gui.village-builder.current_plan"), xPos + 12, yPos, -23296, true);
         yPos += 12;
         context.text(this.font, this.trimToWidth(data.planName(), panelWidth), xPos, yPos, -1, true);
         yPos += 12;

         float totalCompletion = 0.0F;
         int reqCount = data.materials().size();
         context.text(this.font, Component.translatable("gui.village-builder.materials_needed"), xPos, yPos + 4, -4473925, true);
         int matYPos = yPos + 16;
         int materialsRendered = 0;

         for (BuildersTableData.MaterialInfo mat : data.materials()) {
            if (matYPos + 10 > panelBottom - 24) {
               int remaining = data.materials().size() - materialsRendered;
               if (remaining > 0) {
                  context.text(this.font, Component.literal("...and " + remaining + " more"), xPos + 5, matYPos, -5592406, true);
                  matYPos += 10;
               }
               for (int mi = materialsRendered; mi < data.materials().size(); mi++) {
                  BuildersTableData.MaterialInfo hidden = data.materials().get(mi);
                  Item hiddenItem = resolveItem(hidden.itemId());
                  int hiddenHave = hiddenItem != null ? this.countItemInVillageSlots(hiddenItem) : hidden.have();
                  int hiddenCapped = Math.min(hiddenHave, hidden.need());
                  if (reqCount > 0) {
                     totalCompletion += Math.min(1.0F, (float) hiddenCapped / hidden.need());
                  }
               }
               break;
            }

            Item item = resolveItem(mat.itemId());
            int liveHave = item != null ? this.countItemInVillageSlots(item) : mat.have();
            int capped = Math.min(liveHave, mat.need());
            String itemName = item != null ? Component.translatable(item.getDescriptionId()).getString() : mat.itemId();
            boolean complete = capped >= mat.need();
            String reqText = String.format("%s: %d/%d%s", itemName, capped, mat.need(), complete ? " \u2713" : "");
            int textColor = complete ? -11141291 : -2236963;
            context.text(this.font, this.trimToWidth(reqText, panelWidth - 5), xPos + 5, matYPos, textColor, true);
            matYPos += 10;
            materialsRendered++;
            if (reqCount > 0) {
               totalCompletion += Math.min(1.0F, (float) capped / mat.need());
            }
         }

         float pct = reqCount > 0 ? totalCompletion / reqCount * 100.0F : 0.0F;
         int footerY = Math.min(matYPos + 4, panelBottom - 20);
         context.text(this.font, Component.translatable("gui.village-builder.progress", String.format("%.0f", pct)), xPos, footerY, -4473925, true);
         if (!data.constructionHint().isEmpty()) {
            context.text(this.font, this.trimToWidth(data.constructionHint(), panelWidth), xPos, footerY + 10, -5592406, true);
         }
      } else {
         context.text(this.font, Component.translatable("gui.village-builder.no_plan"), xPos, yPos, -4473925, true);
         context.text(this.font, Component.translatable("gui.village-builder.no_plan_hint_1"), xPos, yPos + 12, -5592406, true);
         context.text(this.font, Component.translatable("gui.village-builder.no_plan_hint_2"), xPos, yPos + 24, -5592406, true);
      }
   }

   private int countItemInVillageSlots(Item item) {
      int count = 0;
      for (int i = 0; i < VILLAGE_SLOT_COUNT; i++) {
         ItemStack stack = this.menu.getSlot(i).getItem();
         if (!stack.isEmpty() && stack.getItem() == item) {
            count += stack.getCount();
         }
      }
      return count;
   }

   private Component trimToWidth(String text, int maxWidth) {
      if (this.font.width(text) <= maxWidth) {
         return Component.literal(text);
      }
      String ellipsis = "...";
      int ellipsisWidth = this.font.width(ellipsis);
      String trimmed = this.font.plainSubstrByWidth(text, maxWidth - ellipsisWidth);
      return Component.literal(trimmed + ellipsis);
   }

   private static Item resolveItem(String itemId) {
      Identifier id = Identifier.tryParse(itemId);
      return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
   }

   @Override
   protected void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
      context.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
      context.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
   }

   @Override
   protected void init() {
      super.init();
      this.titleLabelX = (176 - this.font.width(this.title)) / 2;
   }
}
