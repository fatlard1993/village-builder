package justfatlard.village_builder.gametest;

import java.util.Set;
import justfatlard.pandorical.gametest.Smoke;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.util.VillageDimensions;
import justfatlard.village_builder.village.VillageData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * In PvP Dimensions' arena overworld the Builder's Table opens and the Builder's Flag marks a site,
 * the village they make is kept apart from the real overworld's and is forgotten with its chunk,
 * and the nether still refuses both.
 *
 * <p>The arena is a stand-in: {@code pvp-dimensions-justfatlard:overworld}, a flat overworld this
 * test mod's own datapack adds, since the dimension's id is all this mod asks of it.
 */
public final class ArenaOverworld implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Smoke.full()) return;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			// Creative, so standing in the nether's rock for a few ticks costs nothing.
			server.runCommand("gamemode creative @a");

			// A table on the arena's flat ground, and the player beside it. A few ticks apart from
			// using it: a placed block's point of interest is recorded on a later tick, and the
			// flag finds its table by that.
			server.runOnServer(s -> {
				ServerLevel arena = s.getLevel(VillageDimensions.ARENA_OVERWORLD);
				check(arena != null, "the stand-in arena dimension is not loaded");
				check(VillageDimensions.isVillageWorld(arena), "the arena overworld is not taken for a village world");
				check(VillageDimensions.isVillageWorld(s.overworld()), "the overworld is not taken for a village world");
				check(!VillageDimensions.isVillageWorld(s.getLevel(Level.NETHER)), "the nether is taken for a village world");
				check(Main.villages(s.overworld()) == Main.VILLAGE_DATA_MANAGER, "the overworld lost its own villages");
				check(Main.villages(arena) != Main.VILLAGE_DATA_MANAGER, "the arena shares the real overworld's villages");

				arena.setBlockAndUpdate(table(arena), Main.BUILDERS_TABLE_BLOCK.defaultBlockState());
				connection.getServerPlayer().teleportTo(arena, 0.5, table(arena).getY(), 0.5, Set.of(), 0, 0, true);
			});
			context.waitTicks(5);

			server.runOnServer(s -> {
				ServerLevel arena = s.getLevel(VillageDimensions.ARENA_OVERWORLD);
				ServerPlayer player = connection.getServerPlayer();
				check(player.level() == arena, "the player did not reach the arena");
				BlockPos table = table(arena);
				int realVillages = Main.VILLAGE_DATA_MANAGER.getVillageCount();

				InteractionResult opened = arena.getBlockState(table).useWithoutItem(arena, player, hitOn(table));
				check(opened.equals(InteractionResult.SUCCESS), "the table refused in the arena: " + opened);

				BlockPos ground = new BlockPos(8, arena.getMinY() + 3, 8);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Main.BUILDERS_FLAG_ITEM));
				InteractionResult flagged = Main.BUILDERS_FLAG_ITEM.useOn(
					new UseOnContext(player, InteractionHand.MAIN_HAND, hitOn(ground)));
				check(flagged.equals(InteractionResult.SUCCESS), "the flag refused in the arena: " + flagged);

				VillageData village = Main.villages(arena).getExistingVillageData(arena, table);
				check(village != null, "the arena village was not recorded");
				check(ground.above().equals(village.getPreferredBuildSite()),
					"the flag marked " + village.getPreferredBuildSite() + ", not " + ground.above());
				check(village.getCurrentPlan() != null, "the arena village was given no plan");
				check(Main.VILLAGE_DATA_MANAGER.getVillageCount() == realVillages,
					"the arena village was recorded among the real overworld's");

				// What the arena's chunk unloading does: the village on it is forgotten.
				Main.villages(arena).forgetVillagesIn(ChunkPos.pack(table));
				check(Main.villages(arena).getExistingVillageData(arena, table) == null,
					"the arena village outlived its chunk");
			});

			// The nether still refuses, the table and the flag both.
			server.runOnServer(s -> {
				ServerLevel nether = s.getLevel(Level.NETHER);
				nether.setBlockAndUpdate(netherTable(), Main.BUILDERS_TABLE_BLOCK.defaultBlockState());
				connection.getServerPlayer().teleportTo(nether, 0.5, netherTable().getY(), 0.5, Set.of(), 0, 0, true);
			});
			context.waitTicks(5);

			server.runOnServer(s -> {
				ServerLevel nether = s.getLevel(Level.NETHER);
				ServerPlayer player = connection.getServerPlayer();
				check(player.level() == nether, "the player did not reach the nether");

				InteractionResult refused = nether.getBlockState(netherTable()).useWithoutItem(nether, player, hitOn(netherTable()));
				check(refused.equals(InteractionResult.CONSUME), "the table did not refuse in the nether: " + refused);

				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Main.BUILDERS_FLAG_ITEM));
				InteractionResult flagged = Main.BUILDERS_FLAG_ITEM.useOn(
					new UseOnContext(player, InteractionHand.MAIN_HAND, hitOn(netherTable().below())));
				check(flagged.equals(InteractionResult.FAIL), "the flag did not refuse in the nether: " + flagged);
			});
		}
	}

	/** On the stand-in's ground: bedrock and three stone, so the first air is four up. */
	private static BlockPos table(ServerLevel arena) {
		return new BlockPos(2, arena.getMinY() + 4, 0);
	}

	private static BlockPos netherTable() {
		return new BlockPos(2, 100, 0);
	}

	private static BlockHitResult hitOn(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
