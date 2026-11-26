package azbd.morpher.render.handlers;

import azbd.morpher.api.ArmorModelRegistry;
import azbd.morpher.model.ArmorModel;
import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import azbd.morpher.util.ResourceUtils;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.util.math.MathHelper;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.List;
import java.util.Optional;
import java.util.IdentityHashMap;
import java.util.Map;

public class ArmorRenderHandler {

    private final MorphRenderer renderer;
    private final ArmorModel headArmor = new ArmorModel(EquipmentSlot.HEAD);
    private final ArmorModel chestArmor = new ArmorModel(EquipmentSlot.CHEST);
    private final ArmorModel legsArmor = new ArmorModel(EquipmentSlot.LEGS);
    private final ArmorModel feetArmor = new ArmorModel(EquipmentSlot.FEET);
    private final ArmorModel elytraModel = ArmorModel.createElytra();

    private final azbd.morpher.model.OverlayModel headOverlay = new azbd.morpher.model.OverlayModel();
    private final azbd.morpher.model.OverlayModel chestOverlay = new azbd.morpher.model.OverlayModel();
    private final azbd.morpher.model.OverlayModel legsOverlay = new azbd.morpher.model.OverlayModel();
    private final azbd.morpher.model.OverlayModel feetOverlay = new azbd.morpher.model.OverlayModel();

    private final ArmorAutoHandler autoHandler;
    private boolean isRendering = false;
    private final Map<GeoBone, float[]> boneStates = new IdentityHashMap<>();

    public ArmorRenderHandler(MorphRenderer renderer) {
        this.renderer = renderer;
        this.autoHandler = new ArmorAutoHandler(renderer);
    }

    public void renderAllArmor(MatrixStack matrices, MorphEntity entity,
                               VertexConsumerProvider buffers, float partialTick,
                               int light, int overlay) {
        if (isRendering) return;

        PlayerEntity player = renderer.getCurrentEntity();
        if (player == null) return;

        MorphData.PlayerMorphState data = MorphData.get(player);
        headOverlay.setOverlayName(data.equipHead);
        chestOverlay.setOverlayName(data.equipChest);
        legsOverlay.setOverlayName(data.equipLegs);
        feetOverlay.setOverlayName(data.equipFeet);

        ItemStack headStack = player.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack bodyStack = player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack legsStack = player.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack feetStack = player.getEquippedStack(EquipmentSlot.FEET);

        try {
            isRendering = true;

            renderSlotPiece(matrices, entity, buffers, partialTick, light, overlay, player, headStack, EquipmentSlot.HEAD, headOverlay, headArmor);
            renderSlotPiece(matrices, entity, buffers, partialTick, light, overlay, player, bodyStack, EquipmentSlot.CHEST, chestOverlay, chestArmor);
            renderSlotPiece(matrices, entity, buffers, partialTick, light, overlay, player, legsStack, EquipmentSlot.LEGS, legsOverlay, legsArmor);
            renderSlotPiece(matrices, entity, buffers, partialTick, light, overlay, player, feetStack, EquipmentSlot.FEET, feetOverlay, feetArmor);

            if (!bodyStack.isEmpty() && bodyStack.getItem() instanceof ElytraItem) {
                renderElytra(matrices, entity, buffers, partialTick, light, overlay, elytraModel);
            }
        } finally {
            isRendering = false;
        }
    }

    private void renderSlotPiece(MatrixStack matrices, MorphEntity entity,
                                 VertexConsumerProvider buffers, float partialTick,
                                 int light, int overlay, PlayerEntity player,
                                 ItemStack armorStack, EquipmentSlot slot,
                                 azbd.morpher.model.OverlayModel overlayModel,
                                 ArmorModel vanillaFallback) {
        if (armorStack == null || armorStack.isEmpty()) return;

        Optional<ArmorModelRegistry.Entry> customArmor = ArmorModelRegistry.get(armorStack, slot);

        if (overlayModel.hasOverlay() && overlayModel.modelExists(entity)) {
            renderOverlayPiece(matrices, entity, buffers, partialTick, light, overlay,
                    overlayModel, vanillaFallback, armorStack, customArmor.orElse(null), slot);
            return;
        }

        if (customArmor.isPresent() && renderCustomArmorPiece(matrices, entity, buffers, partialTick, light, overlay, customArmor.get(), armorStack, slot)) {
            return;
        }

        if (renderExternalArmorForSlot(matrices, player, entity, buffers, partialTick, light, overlay, armorStack, slot)) {
            return;
        }

        renderArmor(matrices, entity, buffers, partialTick, light, overlay, armorStack, vanillaFallback, slot);
    }

    public boolean renderExternalArmorForSlot(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                              VertexConsumerProvider buffers, float partialTick,
                                              int light, int overlay, ItemStack stack, EquipmentSlot slot) {
        return this.autoHandler.renderForSlot(matrices, player, entity, buffers, partialTick,
                light, overlay, stack, slot);
    }

    public boolean renderExternalArmorOnArm(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                            VertexConsumerProvider buffers, float partialTick,
                                            int light, int overlay, ItemStack stack, Arm arm) {
        return this.autoHandler.renderOnArm(matrices, player, entity, buffers, partialTick,
                light, overlay, stack, arm);
    }

    private boolean renderCustomArmorPiece(MatrixStack matrices, MorphEntity entity,
                                           VertexConsumerProvider buffers, float partialTick,
                                           int light, int overlay, ArmorModelRegistry.Entry entry,
                                           ItemStack armorStack, EquipmentSlot slot) {
        try {
            azbd.morpher.model.ArmorModelCreator model = new azbd.morpher.model.ArmorModelCreator(entry);
            Identifier modelId = model.getModelResource(entity);
            Identifier textureId = model.getTextureResource(entity);
            if (!exists(modelId) || textureId == null || !exists(textureId)) {
                return false;
            }

            BakedGeoModel bakedModel = model.getBakedModel(modelId);
            if (bakedModel == null) return false;

            BakedGeoModel srcModel = renderer.getPlayerModel()
                    .getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(bakedModel.topLevelBones());
            try {
                copyRootAnimations(srcModel, bakedModel.topLevelBones());
                applyBoneVisibilityForSlot(bakedModel, slot);

                RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
                VertexConsumer buffer = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, armorStack.hasGlint());

                float r = 1.0F, g = 1.0F, b = 1.0F;
                int leatherColor = getLeatherColor(armorStack);
                if (leatherColor != -1) {
                    r = (leatherColor >> 16 & 255) / 255.0F;
                    g = (leatherColor >> 8 & 255) / 255.0F;
                    b = (leatherColor & 255) / 255.0F;
                }

                int packedColor = packRgb(r, g, b);

                renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                        buffer, false, partialTick, light, overlay, packedColor);
                renderArmorTrim(matrices, entity, buffers, partialTick, light, overlay, armorStack, slot, bakedModel);
            } finally {
                restoreSnapshot(bakedModel.topLevelBones());
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void renderOverlayPiece(MatrixStack matrices, MorphEntity entity,
                                    VertexConsumerProvider buffers, float partialTick,
                                    int light, int overlay, azbd.morpher.model.OverlayModel overlayModel,
                                    ArmorModel vanillaFallback, ItemStack armorStack,
                                    ArmorModelRegistry.Entry customArmorEntry,
                                    EquipmentSlot slot) {
        try {
            Identifier modelId = overlayModel.getModelResource(entity);
            if (!exists(modelId)) return;
            BakedGeoModel bakedModel = overlayModel.getBakedModel(modelId);
            if (bakedModel == null) return;

            Identifier textureId = overlayModel.getTextureResource(entity);
            if (textureId == null || !exists(textureId)) {
                if (customArmorEntry != null && exists(customArmorEntry.texture())) {
                    textureId = customArmorEntry.texture();
                } else {
                    textureId = vanillaFallback.getTextureResource(entity);
                }
            }
            if (textureId == null) return;

            BakedGeoModel srcModel = renderer.getPlayerModel()
                    .getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(bakedModel.topLevelBones());
            try {
                copyRootAnimations(srcModel, bakedModel.topLevelBones());
                applyBoneVisibilityForSlot(bakedModel, slot);

                boolean hasFoil = armorStack.hasGlint();
                RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
                VertexConsumer buffer = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, hasFoil);

                float r = 1.0F, g = 1.0F, b = 1.0F;
                int leatherColor = getLeatherColor(armorStack);
                if (leatherColor != -1) {
                    r = (leatherColor >> 16 & 255) / 255.0F;
                    g = (leatherColor >> 8 & 255) / 255.0F;
                    b = (leatherColor & 255) / 255.0F;
                }

                int packedColor = packRgb(r, g, b);

                renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                        buffer, false, partialTick, light, overlay, packedColor);
                renderArmorTrim(matrices, entity, buffers, partialTick, light, overlay, armorStack, slot, bakedModel);
            } finally {
                restoreSnapshot(bakedModel.topLevelBones());
            }
        } catch (Exception e) {
        }
    }

    private void renderArmor(MatrixStack matrices, MorphEntity entity,
                             VertexConsumerProvider buffers, float partialTick,
                             int light, int overlay, ItemStack armorStack,
                             ArmorModel model, EquipmentSlot slot) {
        try {
            Identifier modelId = model.getModelResource(entity);
            if (!exists(modelId)) return;
            BakedGeoModel bakedModel = model.getBakedModel(modelId);
            if (bakedModel == null) return;

            BakedGeoModel srcModel = renderer.getPlayerModel()
                    .getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(bakedModel.topLevelBones());
            try {
                copyRootAnimations(srcModel, bakedModel.topLevelBones());

                java.util.List<Identifier> allLayers = ArmorModel.resolveAllLayerTextures(armorStack, slot);
                if (allLayers.size() > 1) {
                    renderArmorLayers(matrices, entity, buffers, partialTick, light, overlay,
                            armorStack, bakedModel, allLayers);
                } else {
                    Identifier textureId = model.getTextureResource(entity);
                    if (textureId != null) {
                        boolean hasFoil = armorStack.hasGlint();
                        RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
                        VertexConsumer vc = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, hasFoil);

                        float r = 1.0F, g = 1.0F, b = 1.0F;
                        int leatherColor = getLeatherColor(armorStack);
                        if (leatherColor != -1) {
                            r = (leatherColor >> 16 & 255) / 255.0F;
                            g = (leatherColor >> 8 & 255) / 255.0F;
                            b = (leatherColor & 255) / 255.0F;
                        }
                        int packedColor = packRgb(r, g, b);

                        renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                                vc, false, partialTick, light, overlay, packedColor);
                    }
                }
                renderArmorTrim(matrices, entity, buffers, partialTick, light, overlay, armorStack, slot, bakedModel);
            } finally {
                restoreSnapshot(bakedModel.topLevelBones());
            }
        } catch (Exception e) {
            azbd.morpher.util.LogOnce.warn("armor-render-" + e.getClass().getName(), "[Morpher] Armor render failed.", e);
        }
    }

    private void renderArmorLayers(MatrixStack matrices, MorphEntity entity,
                                   VertexConsumerProvider buffers, float partialTick,
                                   int light, int overlay, ItemStack armorStack,
                                   BakedGeoModel bakedModel, java.util.List<Identifier> layerTextures) {
        boolean first = true;
        for (Identifier layerTex : layerTextures) {
            RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(layerTex);
            VertexConsumer vc = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer,
                    first && armorStack.hasGlint());

            int packedColor;
            if (first) {
                float r = 1.0F, g = 1.0F, b = 1.0F;
                int leatherColor = getLeatherColor(armorStack);
                if (leatherColor != -1) {
                    r = (leatherColor >> 16 & 255) / 255.0F;
                    g = (leatherColor >> 8 & 255) / 255.0F;
                    b = (leatherColor & 255) / 255.0F;
                }
                packedColor = packRgb(r, g, b);
            } else {
                packedColor = 0xFFFFFFFF;
            }

            renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                    vc, false, partialTick, light, overlay, packedColor);
            first = false;
        }
    }

    private void renderElytra(MatrixStack matrices, MorphEntity entity,
                              VertexConsumerProvider buffers, float partialTick,
                              int light, int overlay, ArmorModel elytraModel) {
        try {
            Identifier modelId = elytraModel.getModelResource(entity);
            if (!exists(modelId)) return;
            BakedGeoModel elytraGeoModel = elytraModel.getBakedModel(modelId);
            if (elytraGeoModel == null) return;

            Identifier textureId = elytraModel.getTextureResource(entity);
            RenderLayer renderLayer = elytraModel.getRenderType(entity, textureId);
            VertexConsumer buffer = buffers.getBuffer(renderLayer);

            BakedGeoModel srcModel = renderer.getPlayerModel().getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(elytraGeoModel.topLevelBones());
            try {
                copyRootAnimations(srcModel, elytraGeoModel.topLevelBones());

                PlayerEntity player = renderer.getCurrentEntity();
                Optional<GeoBone> rightWing = getBoneFromModel(elytraGeoModel, "right_wing");
                Optional<GeoBone> leftWing = getBoneFromModel(elytraGeoModel, "left_wing");
                if (player != null && rightWing.isPresent() && leftWing.isPresent()) {
                    animateWings(rightWing.get(), leftWing.get(), player, partialTick);
                }

                renderer.renderDirectly(matrices, entity, elytraGeoModel, renderLayer, buffers,
                        buffer, false, partialTick, light, overlay, 0xFFFFFFFF);
            } finally {
                restoreSnapshot(elytraGeoModel.topLevelBones());
            }
        } catch (Exception e) {
            azbd.morpher.util.LogOnce.warn("elytra-render-" + e.getClass().getName(), "[Morpher] Elytra render failed.", e);
        }
    }

    private void renderArmorTrim(MatrixStack matrices, MorphEntity entity,
                                 VertexConsumerProvider buffers, float partialTick,
                                 int light, int overlay, ItemStack armorStack,
                                 EquipmentSlot slot, BakedGeoModel bakedModel) {
        try {
            if (!(armorStack.getItem() instanceof ArmorItem armorItem)) return;

            ArmorTrim trim = armorStack.get(DataComponentTypes.TRIM);
            if (trim == null) return;

            boolean leggings = slot == EquipmentSlot.LEGS;
            Identifier textureId = leggings
                    ? trim.getLeggingsModelId(armorItem.getMaterial())
                    : trim.getGenericModelId(armorItem.getMaterial());
            boolean decal = trim.getPattern().value().decal();

            SpriteIdentifier sprite = new SpriteIdentifier(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE, textureId);
            RenderLayer renderLayer = TexturedRenderLayers.getArmorTrims(decal);
            VertexConsumer buffer = sprite.getVertexConsumer(buffers, ignored -> renderLayer, false);

            matrices.push();
            renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                    buffer, false, partialTick, light, overlay, 0xFFFFFFFF);
            matrices.pop();
        } catch (Exception ignored) {
        }
    }

    private void applyBoneVisibilityForSlot(BakedGeoModel bakedModel, EquipmentSlot slot) {
        GeoBoneUtils.applyVisibilityForSlot(bakedModel, slot, true);
    }

    private void animateWings(GeoBone rightWing, GeoBone leftWing, PlayerEntity player, float partialTick) {
        boolean flying = player.isFallFlying();
        if (flying) {
            float pitch = player.getPitch();
            float factor = MathHelper.clamp((pitch + 60.0F) / 120.0F, 0.0F, 1.0F);
            leftWing.setRotZ(MathHelper.lerp(factor, (float) Math.toRadians(-85.0F), (float) Math.toRadians(-165.0F)));
            leftWing.setRotX(MathHelper.lerp(factor, (float) Math.toRadians(160.0F), (float) Math.toRadians(180.0F)));
            rightWing.setRotZ(MathHelper.lerp(factor, (float) Math.toRadians(85.0F), (float) Math.toRadians(165.0F)));
            rightWing.setRotX(MathHelper.lerp(factor, (float) Math.toRadians(160.0F), (float) Math.toRadians(180.0F)));
        } else {
            leftWing.setRotZ((float) Math.toRadians(-165.0F));
            leftWing.setRotX((float) Math.toRadians(180.0F));
            rightWing.setRotZ((float) Math.toRadians(165.0F));
            rightWing.setRotX((float) Math.toRadians(180.0F));
        }
    }

    private Optional<GeoBone> getBoneFromModel(BakedGeoModel model, String name) {
        return model.getBone(name).filter(b -> b instanceof GeoBone).map(b -> (GeoBone) b);
    }

    private static int packRgb(float r, float g, float b) {
        return (255 << 24)
                | ((int) (r * 255) << 16)
                | ((int) (g * 255) << 8)
                | (int) (b * 255);
    }

    private int getLeatherColor(ItemStack stack) {
        return azbd.morpher.render.RenderHelpers.getLeatherColor(stack);
    }

    private boolean exists(Identifier id) {
        return ResourceUtils.isLoaded(id);
    }

    private void saveAndReset(List<? extends GeoBone> bones) {
        GeoBoneUtils.saveAndReset(bones, boneStates, false);
    }

    private void restoreSnapshot(List<? extends GeoBone> bones) {
        GeoBoneUtils.restore(bones, boneStates);
    }

    private void copyRootAnimations(BakedGeoModel src, List<? extends GeoBone> dstBones) {
        GeoBoneUtils.copyAnimations(src, dstBones);
    }

}
