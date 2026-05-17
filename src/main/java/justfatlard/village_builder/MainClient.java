package justfatlard.village_builder;

import justfatlard.village_builder.screen.BuildersTableScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class MainClient implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      MenuScreens.register(Main.BUILDERS_TABLE_SCREEN_HANDLER, BuildersTableScreen::new);
   }
}
