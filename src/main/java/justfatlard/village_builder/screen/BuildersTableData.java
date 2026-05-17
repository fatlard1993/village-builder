package justfatlard.village_builder.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record BuildersTableData(String planName, String needReason, List<BuildersTableData.MaterialInfo> materials, int builderCount, String constructionHint) {
   public static final StreamCodec<RegistryFriendlyByteBuf, BuildersTableData> CODEC = new StreamCodec<>() {
      @Override
      public BuildersTableData decode(RegistryFriendlyByteBuf buf) {
         String planName = buf.readUtf();
         String needReason = buf.readUtf();
         int size = Math.min(buf.readVarInt(), 256);
         List<BuildersTableData.MaterialInfo> materials = new ArrayList<>(size);
         for (int i = 0; i < size; i++) {
            materials.add(MaterialInfo.CODEC.decode(buf));
         }
         int builderCount = buf.readVarInt();
         String constructionHint = buf.readUtf();
         return new BuildersTableData(planName, needReason, materials, builderCount, constructionHint);
      }

      @Override
      public void encode(RegistryFriendlyByteBuf buf, BuildersTableData data) {
         buf.writeUtf(data.planName);
         buf.writeUtf(data.needReason);
         buf.writeVarInt(data.materials.size());
         for (BuildersTableData.MaterialInfo mat : data.materials) {
            MaterialInfo.CODEC.encode(buf, mat);
         }
         buf.writeVarInt(data.builderCount);
         buf.writeUtf(data.constructionHint);
      }
   };

   public static final BuildersTableData EMPTY = new BuildersTableData("", "", List.of(), 0, "");

   public record MaterialInfo(String itemId, int have, int need) {
      public static final StreamCodec<RegistryFriendlyByteBuf, BuildersTableData.MaterialInfo> CODEC = new StreamCodec<>() {
         @Override
         public MaterialInfo decode(RegistryFriendlyByteBuf buf) {
            return new MaterialInfo(buf.readUtf(), buf.readVarInt(), buf.readVarInt());
         }

         @Override
         public void encode(RegistryFriendlyByteBuf buf, MaterialInfo info) {
            buf.writeUtf(info.itemId);
            buf.writeVarInt(info.have);
            buf.writeVarInt(info.need);
         }
      };
   }
}
