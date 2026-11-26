package azbd.morpher.mixin;

import azbd.morpher.api.ArmorModelRegistry;
import azbd.morpher.model.ArmorModel;
import azbd.morpher.model.ArmorModelCreator;
import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import azbd.morpher.util.ResourceUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(HeldItemRenderer.class)
public abstract class ArmorRenderMixin {

    @Shadow @Final
    private MinecraftClient client;

    @Unique
    private final Map<UUID, MorphRenderer> armorRendererCache = new ConcurrentHashMap<>();
    @Unique
    private final azbd.morpher.model.OverlayModel morpher$chestOverlayModel = new azbd.morpher.model.OverlayModel();
    @Unique
    private final ArmorModel morpher$chestArmorModel = new ArmorModel(EquipmentSlot.CHEST);
    @Unique
    private final Map<ArmorModelRegistry.Entry, ArmorModelCreator> morpher$customArmorModels = new ConcurrentHashMap<>();

    @Inject(method = "renderArmHoldingItem", at = @At("TAIL"))
    private void morpher$onRenderArmArmor(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                                          float equipProgress, float swingProgress, Arm arm, CallbackInfo ci) {

        AbstractClientPlayerEntity player = client.player;
        if (player == null) return;

        MorphData.PlayerMorphState state = MorphData.get(player);
        if (!state.renderCustomModel || state.isDead || player.isSpectator() || !state.armorHand) return;

        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!(chestStack.getItem() instanceof ArmorItem)) return;
        ArmorItem armorItem = (ArmorItem) chestStack.getItem();

        String materialName = getMaterialName(armorItem);

        try {
            Optional<ArmorModelRegistry.Entry> customArmor = ArmorModelRegistry.get(chestStack, EquipmentSlot.CHEST);

            UUID playerUuid = player.getUuid();
            MorphRenderer renderer = armorRendererCache.computeIfAbsent(playerUuid, uuid -> createRenderer());
            if (renderer == null) throw new Exception("Error al crear renderer");

            renderer.setCurrentEntity(player);
            MorphEntity animatable = (MorphEntity) renderer.getAnimatable();
            if (animatable == null) throw new Exception("Animatable es null");
            animatable.setRenderer(renderer, player);

            azbd.morpher.model.OverlayModel chestOverlay = morpher$chestOverlayModel;
            chestOverlay.setOverlayName(state.equipChest);

            if (chestOverlay.hasOverlay() && chestOverlay.modelExists(animatable)) {
                renderOverlayArmorSafely(matrices, buffers, light, arm, player, chestStack, chestOverlay, customArmor.orElse(null));
                return;
            }

            if (customArmor.isPresent() && customArmorResourcesExist(customArmor.get())) {
                renderCustomArmorSafely(matrices, buffers, light, arm, player, chestStack, customArmor.get());
                return;
            }

            if (renderer.getArmorHandler().renderExternalArmorOnArm(
                    matrices, player, animatable, buffers, 1.0F, light,
                    OverlayTexture.DEFAULT_UV, chestStack, arm)) {
                return;
            }

            renderArmorSafely(matrices, buffers, light, arm, player, state, chestStack, materialName);
        } catch (Exception e) {
            azbd.morpher.util.LogOnce.warn("first-person-armor-" + e.getClass().getName(),
                    "[Morpher] First-person armor render failed.", e);
        }
    }

    @Unique
    private void renderArmorSafely(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                                   Arm arm, AbstractClientPlayerEntity player,
                                   MorphData.PlayerMorphState state, ItemStack chestStack,
                                   String materialName) throws Exception {

        UUID playerUuid = player.getUuid();

        MorphRenderer renderer = armorRendererCache.computeIfAbsent(playerUuid, uuid -> createRenderer());
        if (renderer == null) throw new Exception("Error al crear renderer");

        renderer.setCurrentEntity(player);
        MorphEntity animatable = (MorphEntity) renderer.getAnimatable();
        if (animatable == null) throw new Exception("Animatable es null");

        animatable.setRenderer(renderer, player);

        ArmorModel armorModel = morpher$chestArmorModel;
        armorModel.setCustomMaterial(materialName);

        try {
            armorModel.getBakedModel(armorModel.getModelResource(animatable));
        } catch (Exception e) {
            throw new Exception("Error al cargar modelo: " + materialName, e);
        }

        boolean isRightArm = arm == Arm.RIGHT;
        String  boneName   = isRightArm ? "right_arm" : "left_arm";

        Optional<GeoBone> armorBone = armorModel.getBone(boneName);
        if (!armorBone.isPresent()) throw new Exception("Hueso '" + boneName + "' no encontrado");

        int     packedLight   = light;
        int     damageOverlay = OverlayTexture.DEFAULT_UV;
        float[] armorColor    = calculateArmorColor(chestStack);

        int packedColor = (255 << 24)
                | ((int)(armorColor[0] * 255) << 16)
                | ((int)(armorColor[1] * 255) << 8)
                |  (int)(armorColor[2] * 255);

        matrices.push();
        matrices.translate(-0.0125F, 1.5F, -0.012F);
        matrices.multiply(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(0.0F)
        ));

        GeoBone armorGeoBone = armorBone.get();
        resetBoneToInitialSnapshot(armorGeoBone);

        java.util.List<Identifier> allLayers = ArmorModel.resolveAllLayerTextures(chestStack, EquipmentSlot.CHEST);
        if (allLayers.size() > 1) {
            boolean first = true;
            for (Identifier layerTex : allLayers) {
                RenderLayer armorTexture = RenderLayer.getArmorCutoutNoCull(layerTex);
                int color = first ? packedColor : 0xFFFFFFFF;
                renderer.renderRecursively(
                        matrices, animatable, armorGeoBone,
                        armorTexture, buffers, ItemRenderer.getArmorGlintConsumer(
                                buffers, armorTexture, first && chestStack.hasGlint()),
                        true, 1.0F, packedLight, damageOverlay, color);
                first = false;
            }
        } else {
            Identifier armorTexId = ArmorModel.resolveArmorTexture(chestStack, EquipmentSlot.CHEST, materialName);
            if (armorTexId != null) {
                RenderLayer armorTexture = RenderLayer.getArmorCutoutNoCull(armorTexId);
                renderer.renderRecursively(
                        matrices, animatable, armorGeoBone,
                        armorTexture, buffers, ItemRenderer.getArmorGlintConsumer(
                                buffers, armorTexture, chestStack.hasGlint()),
                        true, 1.0F, packedLight, damageOverlay, packedColor);
            }
        }

        if (chestStack.getItem() instanceof ArmorItem armorItem) {
            renderArmTrim(renderer, matrices, animatable, armorGeoBone, buffers,
                    chestStack, armorItem, packedLight, damageOverlay);
        }

        matrices.pop();
    }

    @Unique
    private void renderOverlayArmorSafely(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                                          Arm arm, AbstractClientPlayerEntity player,
                                          ItemStack chestStack, azbd.morpher.model.OverlayModel overlayModel,
                                          ArmorModelRegistry.Entry customArmorEntry) throws Exception {
        UUID playerUuid = player.getUuid();

        MorphRenderer renderer = armorRendererCache.computeIfAbsent(playerUuid, uuid -> createRenderer());
        if (renderer == null) throw new Exception("Error al crear renderer");

        renderer.setCurrentEntity(player);
        MorphEntity animatable = (MorphEntity) renderer.getAnimatable();
        if (animatable == null) throw new Exception("Animatable es null");

        animatable.setRenderer(renderer, player);

        Identifier modelId = overlayModel.getModelResource(animatable);
        if (!ResourceUtils.isLoaded(modelId)) throw new Exception("Overlay model no cargado: " + modelId);

        software.bernie.geckolib.cache.object.BakedGeoModel bakedModel = overlayModel.getBakedModel(modelId);
        if (bakedModel == null) throw new Exception("No se pudo hornear overlay model: " + modelId);

        boolean isRightArm = arm == Arm.RIGHT;
        String boneName = isRightArm ? "right_arm" : "left_arm";

        Optional<GeoBone> armorBone = bakedModel.getBone(boneName)
                .or(() -> bakedModel.getBone(isRightArm ? "armorRightArm" : "armorLeftArm"))
                .filter(b -> b instanceof GeoBone).map(b -> (GeoBone) b);

        if (!armorBone.isPresent()) throw new Exception("Hueso de brazo no encontrado en overlay model");

        Identifier textureId = overlayModel.getTextureResource(animatable);
        if (textureId == null || !ResourceUtils.isLoaded(textureId)) {
            if (customArmorEntry != null && ResourceUtils.isLoaded(customArmorEntry.texture())) {
                textureId = customArmorEntry.texture();
            } else {
                textureId = ArmorModel.resolveArmorTexture(chestStack, EquipmentSlot.CHEST, null);
            }
        }
        if (textureId == null || !ResourceUtils.isLoaded(textureId)) return;

        RenderLayer armorTexture = RenderLayer.getArmorCutoutNoCull(textureId);

        int packedLight = light;
        int damageOverlay = OverlayTexture.DEFAULT_UV;
        float[] armorColor = calculateArmorColor(chestStack);

        int packedColor = (255 << 24)
                | ((int) (armorColor[0] * 255) << 16)
                | ((int) (armorColor[1] * 255) << 8)
                | (int) (armorColor[2] * 255);

        matrices.push();
        matrices.translate(-0.0125F, 1.5F, -0.012F);
        matrices.multiply(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(0.0F)
        ));

        GeoBone armorGeoBone = armorBone.get();
        resetBoneToInitialSnapshot(armorGeoBone);

        renderer.renderRecursively(
                matrices, animatable, armorGeoBone,
                armorTexture, buffers, ItemRenderer.getArmorGlintConsumer(
                        buffers, armorTexture, chestStack.hasGlint()),
                true, 1.0F, packedLight, damageOverlay, packedColor);

        if (chestStack.getItem() instanceof ArmorItem armorItem) {
            renderArmTrim(renderer, matrices, animatable, armorGeoBone, buffers,
                    chestStack, armorItem, packedLight, damageOverlay);
        }

        matrices.pop();
    }

    @Unique
    private void renderCustomArmorSafely(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                                         Arm arm, AbstractClientPlayerEntity player,
                                         ItemStack chestStack, ArmorModelRegistry.Entry entry) throws Exception {
        UUID playerUuid = player.getUuid();

        MorphRenderer renderer = armorRendererCache.computeIfAbsent(playerUuid, uuid -> createRenderer());
        if (renderer == null) throw new Exception("Error al crear renderer");

        renderer.setCurrentEntity(player);
        MorphEntity animatable = (MorphEntity) renderer.getAnimatable();
        if (animatable == null) throw new Exception("Animatable es null");

        animatable.setRenderer(renderer, player);

        ArmorModelCreator armorModel = morpher$customArmorModels.computeIfAbsent(entry, ArmorModelCreator::new);
        armorModel.getBakedModel(armorModel.getModelResource(animatable));

        boolean isRightArm = arm == Arm.RIGHT;
        String boneName = isRightArm ? "right_arm" : "left_arm";

        Optional<GeoBone> armorBone = armorModel.getBone(boneName)
                .or(() -> armorModel.getBone(isRightArm ? "armorRightArm" : "armorLeftArm"))
                .filter(b -> b instanceof GeoBone).map(b -> (GeoBone) b);

        if (!armorBone.isPresent()) throw new Exception("Hueso '" + boneName + "' no encontrado");

        RenderLayer armorTexture = RenderLayer.getArmorCutoutNoCull(
                armorModel.getTextureResource(animatable));

        int packedLight = light;
        int damageOverlay = OverlayTexture.DEFAULT_UV;
        float[] armorColor = calculateArmorColor(chestStack);

        int packedColor = (255 << 24)
                | ((int) (armorColor[0] * 255) << 16)
                | ((int) (armorColor[1] * 255) << 8)
                | (int) (armorColor[2] * 255);

        matrices.push();
        matrices.translate(-0.0125F, 1.5F, -0.012F);
        matrices.multiply(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(180.0F),
                (float) Math.toRadians(0.0F)
        ));

        GeoBone armorGeoBone = armorBone.get();
        resetBoneToInitialSnapshot(armorGeoBone);

        renderer.renderRecursively(
                matrices, animatable, armorGeoBone,
                armorTexture, buffers, ItemRenderer.getArmorGlintConsumer(
                        buffers, armorTexture, chestStack.hasGlint()),
                true, 1.0F, packedLight, damageOverlay, packedColor);

        if (chestStack.getItem() instanceof ArmorItem armorItem) {
            renderArmTrim(renderer, matrices, animatable, armorGeoBone, buffers,
                    chestStack, armorItem, packedLight, damageOverlay);
        }

        matrices.pop();
    }

    @Unique
    private boolean customArmorResourcesExist(ArmorModelRegistry.Entry entry) {
        if (entry == null) return false;
        return ResourceUtils.isLoaded(entry.model()) && ResourceUtils.isLoaded(entry.texture());
    }

    @Unique
    private void resetBoneToInitialSnapshot(GeoBone bone) {
        var init = bone.getInitialSnapshot();
        bone.setRotX(init.getRotX()); bone.setRotY(init.getRotY()); bone.setRotZ(init.getRotZ());
        bone.markRotationAsChanged();
        bone.setPosX(init.getOffsetX()); bone.setPosY(init.getOffsetY()); bone.setPosZ(init.getOffsetZ());
        bone.markPositionAsChanged();
        bone.setScaleX(init.getScaleX()); bone.setScaleY(init.getScaleY()); bone.setScaleZ(init.getScaleZ());
        bone.markScaleAsChanged();
        for (GeoBone child : bone.getChildBones()) resetBoneToInitialSnapshot(child);
    }

    @Unique
    private float[] calculateArmorColor(ItemStack armorStack) {
        float[] color = new float[]{1.0F, 1.0F, 1.0F};
        int leatherColor = getLeatherColor(armorStack);
        if (leatherColor != -1) {
            color[0] = (float)(leatherColor >> 16 & 0xFF) / 255.0F;
            color[1] = (float)(leatherColor >>  8 & 0xFF) / 255.0F;
            color[2] = (float)(leatherColor        & 0xFF) / 255.0F;
        }
        return color;
    }

    @Unique
    private int getLeatherColor(ItemStack stack) {
        return azbd.morpher.render.RenderHelpers.getLeatherColor(stack);
    }

    @Unique
    private void renderArmTrim(MorphRenderer renderer, MatrixStack matrices,
                               MorphEntity animatable, GeoBone armorGeoBone,
                               VertexConsumerProvider buffers, ItemStack chestStack,
                               ArmorItem armorItem, int packedLight, int damageOverlay) {
        try {
            ArmorTrim trim = chestStack.get(DataComponentTypes.TRIM);
            if (trim == null) return;

            Identifier textureId = trim.getGenericModelId(armorItem.getMaterial());
            boolean decal = trim.getPattern().value().decal();
            SpriteIdentifier sprite = new SpriteIdentifier(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE, textureId);
            RenderLayer renderLayer = TexturedRenderLayers.getArmorTrims(decal);
            VertexConsumer buffer = sprite.getVertexConsumer(buffers, ignored -> renderLayer, false);

            matrices.push();
            renderer.renderRecursively(
                    matrices, animatable, armorGeoBone,
                    renderLayer, buffers, buffer,
                    true, 1.0F, packedLight, damageOverlay, 0xFFFFFFFF);
            matrices.pop();
        } catch (Exception ignored) {
        }
    }

    @Unique
    private MorphRenderer createRenderer() {
        return azbd.morpher.render.RenderHelpers.createRenderer();
    }

    @Unique
    private String getMaterialName(ArmorItem armorItem) {
        RegistryEntry<ArmorMaterial> material = armorItem.getMaterial();

        String id = material.getKey()
                .map(k -> k.getValue().getPath())
                .orElse(null);

        if (id == null) return null;

        return switch (id) {
            case "leather"   -> "leather";
            case "chainmail" -> "chainmail";
            case "iron"      -> "iron";
            case "gold"      -> "gold";
            case "diamond"   -> "diamond";
            case "netherite" -> "netherite";
            default          -> null;
        };
    }
}
