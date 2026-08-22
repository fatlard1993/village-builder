package justfatlard.village_builder.block;

import java.util.ArrayList;
import justfatlard.pandorical.protocol.ComponentUpdate;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import justfatlard.village_builder.Main;
import justfatlard.village_builder.api.VillageBuilderAPI;
import justfatlard.village_builder.building.StructurePlan;
import justfatlard.village_builder.building.StructureType;
import justfatlard.village_builder.integration.VillageQuestsIntegration;
import justfatlard.village_builder.screen.BuildersTableData;
import justfatlard.village_builder.village.VillageData;
import justfatlard.village_builder.village.VillageNeedsAnalyzer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class BuildersTableBlockEntity extends BlockEntity {
    // Screen type constant, used to register action/slot handlers in Main
    public static final String SCREEN_TYPE = "village-builder:builders_table";

    // Layout constants
    private static final int SCREEN_WIDTH = 330;
    private static final int SCREEN_HEIGHT = 166;
    private static final int VILLAGE_SLOT_COUNT = 27;
    private static final int PLAYER_MAIN_SLOTS = 27;
    private static final int PLAYER_INV_START = VILLAGE_SLOT_COUNT;
    private static final int HOTBAR_START = PLAYER_INV_START + PLAYER_MAIN_SLOTS;

    /** The info panel down the right-hand side, and the grid of materials inside it. */
    private static final int PANEL_X = 178;
    /**
     * Clear of the panel's own edge on every side. The beveled border is four pixels deep, so
     * anything less than this sits on the bevel rather than inside the box.
     */
    private static final int PANEL_INSET = 8;
    private static final int CONTENT_X = PANEL_X + PANEL_INSET;
    private static final int CONTENT_TOP = PANEL_INSET + 2;
    private static final int CONTENT_WIDTH = SCREEN_WIDTH - PANEL_X - PANEL_INSET * 2;
    private static final int MATERIAL_CELL_H = 20;
    /**
     * Digits and the slash are all six pixels wide in the vanilla font, and the tick after a
     * finished count is worth about eight. Only the client can measure text properly, so this is
     * an estimate - but it only decides how many columns to use, and it errs toward the roomier
     * answer. Requirements are counted off the real structure template, so a large build can ask
     * for four digits of something and "1728/1728" does not fit beside an icon in half a panel.
     */
    private static final int COUNT_CHAR_W = 6;
    private static final int CHECK_W = 8;
    /** Cells sit flush against each other, so a count needs this much clear of the next icon. */
    private static final int CELL_GUTTER = 2;
    /** Icons are 16 square, so the count sits three pixels clear of one and rides its middle. */
    private static final int COUNT_X = 19;
    private static final int COUNT_Y = 4;
    private static final int BAR_HEIGHT = 8;
    /** The rest of the content width belongs to the percentage sitting beside the bar. */
    private static final int BAR_WIDTH = CONTENT_WIDTH - 32;
    /** Bar, gap, the builder count, and the same inset underneath it as everywhere else. */
    private static final int FOOTER_HEIGHT = BAR_HEIGHT + 4 + 9 + PANEL_INSET;

    /**
     * What each player has open, and what they last saw in it.
     *
     * <p>Two things the slot-change callback cannot work out for itself. It reports every
     * slot on every change rather than only the one that moved, so "this slot is empty" says
     * nothing on its own - twenty-seven empty shelves read as twenty-seven withdrawals. And
     * it is registered once for the screen type, not once per table, so the position it used
     * to charge reputation against was whatever was passed at startup: {@code BlockPos.ZERO}.
     */
    private record Session(BlockPos table, String screenId, int[] seen, Map<String, String> lastSent) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public BuildersTableBlockEntity(BlockPos pos, BlockState state) {
        super(Main.BUILDERS_TABLE_BLOCK_ENTITY, pos, state);
    }

    public void openScreen(ServerPlayer player) {
        if (!(this.level instanceof ServerLevel serverWorld)) return;

        if (!PandoricalApi.isAvailable(player)) {
            player.sendSystemMessage(Component.literal(
                "[village-builder] The Builder's Table requires Pandorical on your client. Please install Pandorical."
            ));
            return;
        }

        BuildersTableData data = buildScreenData(serverWorld);
        VillageData villageData = Main.VILLAGE_DATA_MANAGER.getVillageDataForTable(serverWorld, this.worldPosition);

        OpenScreenS2C screenDef = buildScreenDef(data);

        if (villageData != null) {
            int[] seen = new int[VILLAGE_SLOT_COUNT];
            for (int i = 0; i < VILLAGE_SLOT_COUNT; i++) {
                seen[i] = villageData.getInventory().getItem(i).getCount();
            }
            SESSIONS.put(player.getUUID(),
                new Session(this.worldPosition, screenDef.screenId(), seen, new HashMap<>()));

            // Open a full container screen backed by the village inventory
            PandoricalApi.screens().openContainer(
                player,
                screenDef,
                villageData.getInventory(),
                Set.of() // no read-only slots; players may interact with all 27 slots
            );
        } else {
            // No village data yet: open a display-only info screen
            PandoricalApi.screens().open(player, screenDef);
        }
    }

    private BuildersTableData buildScreenData(ServerLevel serverWorld) {
        VillageData villageData = Main.VILLAGE_DATA_MANAGER.getVillageDataForTable(serverWorld, this.worldPosition);
        if (villageData == null || villageData.getCurrentPlan() == null) {
            return BuildersTableData.EMPTY;
        }

        StructurePlan plan = villageData.getCurrentPlan();
        VillageNeedsAnalyzer analyzer = villageData.getOrCreateAnalyzer(serverWorld);
        VillageNeedsAnalyzer.VillageNeed need = analyzer.analyzeNeeds();
        String needReason = formatNeedReason(need, analyzer);

        List<BuildersTableData.MaterialInfo> materials = new ArrayList<>();
        for (StructureType.MaterialRequirement req : plan.getRequirements()) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(req.item());
            // Not clamped to what the build needs. A shared store people tip whole stacks
            // into is worth reporting honestly: capping it here meant nine hundred logs and
            // exactly enough logs read the same, and there was no way to tell from the table
            // whether there was anything spare in there.
            int have = villageData.getMaterialCount(req.item());
            materials.add(new BuildersTableData.MaterialInfo(itemId.toString(), have, req.amount()));
        }

        AABB searchBox = new AABB(this.worldPosition).inflate(64.0);
        int builderCount = serverWorld.getEntitiesOfClass(Villager.class, searchBox,
            v -> v.getVillagerData().profession().is(Main.BUILDER_KEY)).size();

        return new BuildersTableData(plan.getDisplayName(), needReason, materials, builderCount);
    }

    private OpenScreenS2C buildScreenDef(BuildersTableData data) {
        ScreenBuilder builder = new ScreenBuilder(SCREEN_TYPE)
            .size(SCREEN_WIDTH, SCREEN_HEIGHT)
            .title("Builder's Table")
            // Main inventory panel (left side, chest-style)
            .panel("bg_left", 0, 0, 176, SCREEN_HEIGHT, java.util.Map.of())
            // Pandorical lays the menu out as the mod's own slots, then the player's 27 main
            // slots, then their 9 hotbar slots - so both of these follow from the container
            // size rather than being written down again. The hotbar used to say 63, which is
            // one past the end of a 63-slot menu, and so it showed nothing at all.
            .inventoryGrid("village_inv", 8, 18, 3, 9, 0)
            .inventoryGrid("player_inv", 8, 86, 3, 9, PLAYER_INV_START)
            .inventoryGrid("hotbar", 8, 144, 1, 9, HOTBAR_START)
            // Info panel (right side)
            .panel("bg_right", 178, 0, SCREEN_WIDTH - 178, SCREEN_HEIGHT,
                java.util.Map.of(ComponentType.PROP_BACKGROUND, "#FF2A2A2A"));

        // Register the container so Pandorical handles item slot synchronization
        builder.container(VILLAGE_SLOT_COUNT, true);

        int xPos = CONTENT_X;
        int yPos = CONTENT_TOP;

        if (data != null && !data.planName().isEmpty()) {
            builder.text("plan_name", xPos, yPos,
                java.util.Map.of(
                    ComponentType.PROP_TEXT, data.planName(),
                    ComponentType.PROP_COLOR, "#FFA500",
                    ComponentType.PROP_SHADOW, "true",
                    ComponentType.PROP_WRAP_WIDTH, String.valueOf(CONTENT_WIDTH)
                ));
            yPos += 14;

            int reqCount = data.materials().size();
            // "have/need" at its widest, which is when a material is finished.
            int widestCount = 0;
            for (BuildersTableData.MaterialInfo mat : data.materials()) {
                // Measured off both halves: the held count is no longer capped at the needed
                // one, so it is the half that can be wider.
                int digits = String.valueOf(mat.have()).length() + String.valueOf(mat.need()).length();
                widestCount = Math.max(widestCount, COUNT_X + (digits + 1) * COUNT_CHAR_W);
            }
            int columns = widestCount + CELL_GUTTER <= CONTENT_WIDTH / 2 ? 2 : 1;
            int cellWidth = CONTENT_WIDTH / columns;
            // The tick is the first thing to go when the numbers get long: colour says the same
            // thing, and losing a second column to a decoration is a worse trade than losing it.
            boolean showCheck = widestCount + CHECK_W + CELL_GUTTER <= cellWidth;

            int gridTop = yPos;
            // Whatever the grid does not use, the bar and the builder count still need.
            int gridBottom = SCREEN_HEIGHT - FOOTER_HEIGHT;
            int capacity = Math.max(0, (gridBottom - gridTop) / MATERIAL_CELL_H) * columns;
            // The overflow note takes a cell of its own, so it costs one of the ones it counts.
            int shown = reqCount > capacity ? Math.max(0, capacity - 1) : reqCount;

            float totalCompletion = 0.0F;
            for (int i = 0; i < reqCount; i++) {
                BuildersTableData.MaterialInfo mat = data.materials().get(i);
                totalCompletion += Math.min(1.0F, (float) mat.have() / mat.need());

                // Counted whether or not it fits: the bar is about the build, not about the view.
                if (i >= shown) continue;

                int cellX = xPos + (i % columns) * cellWidth;
                int cellY = gridTop + (i / columns) * MATERIAL_CELL_H;
                boolean complete = mat.have() >= mat.need();

                builder.itemIcon("mat_icon_" + i, cellX, cellY, mat.itemId(), 1);
                builder.text("mat_" + i, cellX + COUNT_X, cellY + COUNT_Y,
                    java.util.Map.of(
                        ComponentType.PROP_TEXT, mat.have() + "/" + mat.need() + (complete && showCheck ? " \u2714" : ""),
                        ComponentType.PROP_COLOR, complete ? "#55FF55" : "#FFAA00",
                        ComponentType.PROP_SHADOW, "true"
                    ));
            }

            int cellsUsed = shown;
            if (reqCount > shown) {
                builder.text("materials_more",
                    xPos + (shown % columns) * cellWidth,
                    gridTop + (shown / columns) * MATERIAL_CELL_H + COUNT_Y,
                    java.util.Map.of(
                        ComponentType.PROP_TEXT, "+" + (reqCount - shown) + " more",
                        ComponentType.PROP_COLOR, "#888888"
                    ));
                cellsUsed = shown + 1;
            }

            int rowsUsed = (cellsUsed + columns - 1) / columns;
            int footerY = Math.min(gridTop + rowsUsed * MATERIAL_CELL_H + 6, gridBottom);

            float fraction = reqCount > 0 ? totalCompletion / reqCount : 0.0F;
            boolean ready = fraction >= 1.0F;

            builder.sprite("progress_track", xPos, footerY, BAR_WIDTH, BAR_HEIGHT,
                java.util.Map.of(ComponentType.PROP_COLOR, "#FF141414"));
            // Always laid down, even at nothing-yet width: a component that does not exist is a
            // component no later update can reach, and a bar that starts empty is exactly the one
            // that most needs to be able to grow. A zero-width fill draws nothing.
            builder.sprite("progress_fill", xPos + 1, footerY + 1,
                Math.round((BAR_WIDTH - 2) * fraction), BAR_HEIGHT - 2,
                java.util.Map.of(ComponentType.PROP_COLOR, ready ? "#FF55FF55" : "#FFFFAA00"));
            builder.text("progress_pct", xPos + BAR_WIDTH + 4, footerY,
                java.util.Map.of(
                    ComponentType.PROP_TEXT, Math.round(fraction * 100.0F) + "%",
                    ComponentType.PROP_COLOR, ready ? "#55FF55" : "#BBBBBB",
                    ComponentType.PROP_SHADOW, "true"
                ));

            // The count, without the running commentary. Nought is the one reading that still
            // says something the bar cannot: materials will sit there for ever.
            int builders = data.builderCount();
            builder.text("builders", xPos, footerY + BAR_HEIGHT + 4,
                java.util.Map.of(
                    ComponentType.PROP_TEXT, builders + (builders == 1 ? " builder" : " builders"),
                    ComponentType.PROP_COLOR, builders == 0 ? "#FF5555" : "#888888",
                    ComponentType.PROP_SHADOW, "true"
                ));
        } else {
            builder.text("no_plan", xPos, yPos,
                java.util.Map.of(
                    ComponentType.PROP_TEXT, "No construction plan",
                    ComponentType.PROP_COLOR, "#BBBBBB",
                    ComponentType.PROP_SHADOW, "true"
                ));
            builder.text("no_plan_hint_1", xPos, yPos + 12,
                java.util.Map.of(
                    ComponentType.PROP_TEXT, "Trade with a Builder",
                    ComponentType.PROP_COLOR, "#888888"
                ));
            builder.text("no_plan_hint_2", xPos, yPos + 24,
                java.util.Map.of(
                    ComponentType.PROP_TEXT, "to select a plan",
                    ComponentType.PROP_COLOR, "#888888"
                ));
        }

        return builder.build();
    }

    private static String formatNeedReason(VillageNeedsAnalyzer.VillageNeed need, VillageNeedsAnalyzer analyzer) {
        int villagers = analyzer.getVillagerCount();
        int beds = analyzer.getBedCount();

        return switch (need) {
            case HOUSING -> beds == 0
                ? Component.translatable("gui.village-builder.need.housing").getString() + " (no beds)"
                : Component.translatable("gui.village-builder.need.housing").getString() + String.format(" (%d villagers, %d beds)", villagers, beds);
            case FOOD -> Component.translatable("gui.village-builder.need.food").getString() + String.format(" (%d farmland)", analyzer.getFarmlandCount());
            case PROFESSION -> Component.translatable("gui.village-builder.need.profession").getString();
            case DEFENSE -> Component.translatable("gui.village-builder.need.defense").getString()
                + String.format(" (%d villagers, %d golems)", villagers, analyzer.getIronGolemCount());
            case UTILITY -> Component.translatable("gui.village-builder.need.utility").getString();
            case PROSPERITY -> Component.translatable("gui.village-builder.need.prosperity").getString() + String.format(" (%d villagers)", villagers);
        };
    }

    /**
     * Called from Main.onInitialize() to wire up slot-change and container-removed handlers
     * for the Builder's Table Pandorical screen type.
     */
    public static void registerPandoricalHandlers() {
        PandoricalApi.screens().onSlotChange(SCREEN_TYPE, (player, slotIndex, stack) -> {
            if (slotIndex >= VILLAGE_SLOT_COUNT) return; // only village slots

            Session session = SESSIONS.get(player.getUUID());
            if (session == null) return;

            int before = session.seen()[slotIndex];
            int now = stack.getCount();
            session.seen()[slotIndex] = now;

            refresh(player, session);

            // Reputation for taking from the village's own stores, charged only on what left.
            if (now < before && VillageQuestsIntegration.AVAILABLE) {
                boolean success = VillageQuestsIntegration.modifyPlayerReputation(
                    player, session.table(), -1, "Took items from Builder's Table");
                if (!success) {
                    player.sendSystemMessage(Component.literal(
                        "The village could not be reached to record that."));
                }
            }
        });

        PandoricalApi.screens().onContainerRemoved(SCREEN_TYPE, player -> {
            SESSIONS.remove(player.getUUID());
            // Persist village data when the screen closes
            Main.VILLAGE_DATA_MANAGER.markPersistentDirty();
        });
    }

    /**
     * Bring the counts and the bar up to date without closing the screen.
     *
     * <p>The panel is built once, at open, and nothing afterwards told it anything had changed:
     * you tipped a stack in, watched the slot fill, and the line beside it went on claiming
     * zero until you shut the table and opened it again.
     *
     * <p>Called once per reported slot - the callback reports all of them on every change - but
     * only the first of those finds anything different, so a change costs one packet rather than
     * twenty-seven. Comparing against what was last sent, rather than counting callbacks, means
     * this does not depend on how many times or in what order it is called.
     *
     * <p>What it cannot revise is the column count, which was chosen at open from the widths the
     * numbers had then. A count that grows a digit mid-session keeps the layout it was born with
     * until the screen is reopened.
     */
    private static void refresh(ServerPlayer player, Session session) {
        if (!(player.level() instanceof ServerLevel level)) return;

        VillageData villageData = Main.VILLAGE_DATA_MANAGER.getVillageDataForTable(level, session.table());
        if (villageData == null || villageData.getCurrentPlan() == null) return;

        List<StructureType.MaterialRequirement> requirements = villageData.getCurrentPlan().getRequirements();
        Map<String, String> now = new HashMap<>();
        float totalCompletion = 0.0F;

        for (int i = 0; i < requirements.size(); i++) {
            StructureType.MaterialRequirement req = requirements.get(i);
            int have = villageData.getMaterialCount(req.item());
            totalCompletion += Math.min(1.0F, (float) have / req.amount());
            now.put("mat_" + i, have + "/" + req.amount() + (have >= req.amount() ? " \u2714" : ""));
        }

        float fraction = requirements.isEmpty() ? 0.0F : totalCompletion / requirements.size();
        now.put("progress_pct", Math.round(fraction * 100.0F) + "%");
        now.put("progress_fill", String.valueOf(Math.round((BAR_WIDTH - 2) * fraction)));

        List<ComponentUpdate> updates = new ArrayList<>();
        for (Map.Entry<String, String> entry : now.entrySet()) {
            if (entry.getValue().equals(session.lastSent().get(entry.getKey()))) continue;
            session.lastSent().put(entry.getKey(), entry.getValue());

            boolean isBar = entry.getKey().equals("progress_fill");
            boolean complete = fraction >= 1.0F;
            updates.add(new ComponentUpdate(entry.getKey(), isBar
                ? Map.of(ComponentType.PROP_WIDTH, entry.getValue(),
                    ComponentType.PROP_COLOR, complete ? "#FF55FF55" : "#FFFFAA00")
                : Map.of(ComponentType.PROP_TEXT, entry.getValue(),
                    ComponentType.PROP_COLOR, colourFor(entry.getKey(), entry.getValue(), complete))));
        }

        if (!updates.isEmpty()) PandoricalApi.screens().update(player, session.screenId(), updates);
    }

    private static String colourFor(String componentId, String text, boolean complete) {
        if (componentId.equals("progress_pct")) return complete ? "#55FF55" : "#BBBBBB";
        return text.endsWith("\u2714") ? "#55FF55" : "#FFAA00";
    }
}
