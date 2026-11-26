package azbd.morpher.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

public final class RenderHelpers {
    private RenderHelpers() {}

    public static MorphRenderer createRenderer() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            EntityRendererFactory.Context context = new EntityRendererFactory.Context(
                    client.getEntityRenderDispatcher(), client.getItemRenderer(), client.getBlockRenderManager(),
                    client.getEntityRenderDispatcher().getHeldItemRenderer(), client.getResourceManager(),
                    client.getEntityModelLoader(), client.textRenderer);
            return new MorphRenderer(context);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static int calculateLight(AbstractClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos pos = player.getBlockPos();
        int block = client.world.getLightLevel(LightType.BLOCK, pos);
        int sky = client.world.getLightLevel(LightType.SKY, pos);
        if (block == 0 && sky == 0) {
            BlockPos[] neighbors = {pos.up(), pos.north(), pos.south(), pos.east(), pos.west()};
            for (BlockPos neighbor : neighbors) {
                block = Math.max(block, client.world.getLightLevel(LightType.BLOCK, neighbor));
                sky = Math.max(sky, client.world.getLightLevel(LightType.SKY, neighbor));
            }
        }
        return LightmapTextureManager.pack(block, sky);
    }

    public static int getDamageOverlay(AbstractClientPlayerEntity player) {
        int hurtTime = player.hurtTime;
        if (hurtTime <= 0) return OverlayTexture.DEFAULT_UV;
        return OverlayTexture.packUv((int) ((hurtTime / 10.0F) * 3.0F), 3);
    }

    public static int getLeatherColor(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
            if (dyed != null) return dyed.rgb();
            try {
                if (armor.getMaterial().matchesKey(net.minecraft.item.ArmorMaterials.LEATHER.getKey().orElse(null))) return 0xA06540;
            } catch (Throwable ignored) {}
            try {
                if (armor.getMaterial().getKey().map(key -> key.getValue().getPath().contains("leather")).orElse(false)) return 0xA06540;
            } catch (Throwable ignored) {}
            try {
                if (armor.getMaterial().value().equipSound().value().getId().getPath().contains("leather")) return 0xA06540;
            } catch (Throwable ignored) {}
        }
        return -1;
    }
}
