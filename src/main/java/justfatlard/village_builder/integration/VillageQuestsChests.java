package justfatlard.village_builder.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Tells Village Quests about village property this mod raises after worldgen.
 *
 * <p>Guarded the way the rest of the village web is: the flag is read in this class, which always
 * loads, and the type it names is only touched inside a branch a server without that mod never
 * takes. Nothing here is required for a village to get built - without Village Quests a chest is
 * just a chest, which is the correct answer when there is no reputation to steal from.
 */
public final class VillageQuestsChests {
	private VillageQuestsChests() {}

	private static final boolean PRESENT =
		FabricLoader.getInstance().isModLoaded("village-quests-justfatlard");

	public static void claim(ServerLevel world, BlockPos pos) {
		if (!PRESENT) return;
		justfatlard.village_quests.api.VillageQuestsAPI.claimVillageChest(world, pos);
	}
}
