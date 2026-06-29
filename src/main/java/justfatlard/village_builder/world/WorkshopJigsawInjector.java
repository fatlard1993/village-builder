package justfatlard.village_builder.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkshopJigsawInjector {
    private static final Logger LOGGER = LoggerFactory.getLogger("village-builder");

    private static final String JIGSAW_BLOCK_ID        = Identifier.fromNamespaceAndPath("minecraft", "jigsaw").toString();
    private static final String BUILDING_ENTRANCE_ID   = Identifier.fromNamespaceAndPath("minecraft", "building_entrance").toString();
    private static final String AIR_ID                 = Identifier.fromNamespaceAndPath("minecraft", "air").toString();

    private static final String[][] WORKSHOPS = {
        {"builders_workshop_plains",  "minecraft:village/plains/terminators"},
        {"builders_workshop_desert",  "minecraft:village/desert/terminators"},
        {"builders_workshop_savanna", "minecraft:village/savanna/terminators"},
        {"builders_workshop_taiga",   "minecraft:village/taiga/terminators"},
        {"builders_workshop_snowy",   "minecraft:village/snowy/terminators"},
    };

    public static void inject() {
        for (String[] entry : WORKSHOPS) {
            String name = entry[0];
            String pool = entry[1];
            String resourcePath = "/data/village-builder/structure/" + name + ".nbt";
            try {
                injectJigsaw(name, pool, resourcePath);
            } catch (Exception e) {
                LOGGER.error("[village-builder] Failed to inject jigsaw into {}: {}", name, e.getMessage());
            }
        }
    }

    private static void injectJigsaw(String name, String pool, String resourcePath) throws IOException {
        URL resourceUrl = WorkshopJigsawInjector.class.getResource(resourcePath);
        if (resourceUrl == null) {
            LOGGER.error("[village-builder] Resource not found: {}", resourcePath);
            return;
        }

        // Resolve to an on-disk path so we can write back
        Path filePath;
        try {
            filePath = Paths.get(resourceUrl.toURI());
        } catch (Exception e) {
            LOGGER.error("[village-builder] Cannot resolve {} to a file path: {}", resourcePath, e.getMessage());
            return;
        }

        // Read the NBT
        CompoundTag root;
        try (InputStream in = WorkshopJigsawInjector.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.error("[village-builder] Could not open stream for {}", resourcePath);
                return;
            }
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        // Read size
        ListTag sizeTag = root.getListOrEmpty("size");
        int sizeX = sizeTag.getIntOr(0, 0);
        int sizeY = sizeTag.getIntOr(1, 0);
        int sizeZ = sizeTag.getIntOr(2, 0);

        // Read palette
        ListTag palette = root.getListOrEmpty("palette");

        // Check whether a jigsaw block is already present
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag stateTag = palette.getCompoundOrEmpty(i);
            if (JIGSAW_BLOCK_ID.equals(stateTag.getStringOr("Name", ""))) {
                LOGGER.info("[village-builder] {} already has a jigsaw block — skipping", name);
                return;
            }
        }

        // Build the palette entry for the jigsaw block state
        CompoundTag jigsawState = new CompoundTag();
        jigsawState.putString("Name", JIGSAW_BLOCK_ID);
        CompoundTag props = new CompoundTag();
        props.putString("facing", "north");
        props.putString("orientation", "north_up");
        jigsawState.put("Properties", props);

        int paletteIndex = palette.size();
        palette.add(jigsawState);

        // Build the block position: center of north face, ground level
        int posX = sizeX / 2;
        int posY = 0;
        int posZ = 0;

        ListTag posTag = new ListTag();
        posTag.add(IntTag.valueOf(posX));
        posTag.add(IntTag.valueOf(posY));
        posTag.add(IntTag.valueOf(posZ));

        // Build the jigsaw block entity NBT
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", JIGSAW_BLOCK_ID);
        nbt.putString("pool", pool);
        nbt.putString("name", BUILDING_ENTRANCE_ID);
        nbt.putString("target", BUILDING_ENTRANCE_ID);
        nbt.putString("final_state", AIR_ID);
        nbt.putString("joint", "rollable");
        nbt.putString("placement_priority", "0");
        nbt.putString("selection_priority", "0");

        CompoundTag block = new CompoundTag();
        block.put("pos", posTag);
        block.putInt("state", paletteIndex);
        block.put("nbt", nbt);

        ListTag blocks = root.getListOrEmpty("blocks");
        blocks.add(block);

        // Write modified NBT back to disk
        try (OutputStream out = Files.newOutputStream(filePath)) {
            NbtIo.writeCompressed(root, out);
        }

        LOGGER.info(
            "[village-builder] Injected jigsaw block into {} at ({}, {}, {}) with pool {}",
            name, posX, posY, posZ, pool
        );
    }
}
