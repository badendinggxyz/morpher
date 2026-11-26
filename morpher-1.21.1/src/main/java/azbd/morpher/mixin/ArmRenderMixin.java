package azbd.morpher.mixin;

import azbd.morpher.model.ArmorModel;
import azbd.morpher.model.PlayerModel;
import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.render.MorphRenderer;
import azbd.morpher.render.SkinResolver;
import azbd.morpher.util.ResourceUtils;
import azbd.morpher.util.LogOnce;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(PlayerEntityRenderer.class)
public abstract class ArmRenderMixin {

    @Unique private static final float MORPHER_HAND_SIZE_SHIFT = 0.25F;

    @Unique
    private final Map<UUID, MorphRenderer> armRendererCache = new ConcurrentHashMap<>();
    @Unique
    private final PlayerModel morpher$armPlayerModel = new PlayerModel();
    @Unique
    private final Map<String, ArmorModel> morpher$armLayerModels = new ConcurrentHashMap<>();

    @Inject(
            method = "renderArm",
            at = @At("HEAD"),
            cancellable = true
    )
    private void morpher$onRenderArm(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve,
                            CallbackInfo ci) {

        MorphData.PlayerMorphState state = MorphData.get(player);
        UUID playerUuid = player.getUuid();

        if (!state.renderCustomModel || state.isDead) {
            armRendererCache.remove(playerUuid);
            return;
        }

        if (!state.modelHand) return;

        try {
            MorphRenderer renderer = armRendererCache.computeIfAbsent(playerUuid, uuid -> createRenderer());
            if (renderer == null) return;

            renderer.setCurrentEntity(player);
            MorphEntity animatable = (MorphEntity) renderer.getAnimatable();
            if (animatable == null) return;

            animatable.setRenderer(renderer, player);

            String modelName = SkinResolver.resolvePlayerModelPath(player, state);

            Identifier modelId = ResourceUtils.safeIdentifier("morpher", "geo/" + ResourceUtils.cleanModelPath(modelName) + ".geo.json");
            if (!ResourceUtils.isLoaded(modelId)) {
                modelId = Identifier.of("morpher", "geo/player_wide.geo.json");
                if (!ResourceUtils.isLoaded(modelId)) {
                    LogOnce.warn("arm-model-missing", "[Morpher] First-person arm render is waiting for model assets.", null);
                    return;
                }
            }

            PlayerModel playerModel = morpher$armPlayerModel;
            playerModel.getBakedModel(modelId);

            ArmorModel layerModel = null;
            String layerName = state.layerName;
            if (!layerName.isEmpty() && !layerName.equals("empty")) {
                layerModel = morpher$armLayerModels.computeIfAbsent(layerName, name -> {
                    ArmorModel cachedModel = ArmorModel.createLayer();
                    cachedModel.setLayerName(name);
                    return cachedModel;
                });
                Identifier layerGeoId = ResourceUtils.safeIdentifier("morpher", "geo/" + ResourceUtils.cleanModelPath(layerName) + ".geo.json");
                if (ResourceUtils.isLoaded(layerGeoId)) {
                    layerModel.getBakedModel(layerGeoId);
                }
            }

            PlayerEntityRenderer vanillaRenderer = (PlayerEntityRenderer)(Object)this;
            net.minecraft.client.render.entity.model.PlayerEntityModel<?> playerEntityModel =
                    (net.minecraft.client.render.entity.model.PlayerEntityModel<?>) vanillaRenderer.getModel();

            boolean isRightArmCall = (arm == playerEntityModel.rightArm || arm == playerEntityModel.rightSleeve);
            String boneName = isRightArmCall ? "right_arm" : "left_arm";

            Optional<GeoBone> playerBone = playerModel.getBone(boneName);
            Optional<GeoBone> layerBone  = layerModel != null ? layerModel.getBone(boneName) : Optional.empty();

            if (!playerBone.isPresent()) return;

            RenderLayer playerTexture = getPlayerTexture(state, player);
            RenderLayer layerTexture  = layerName.isEmpty()
                    ? RenderLayer.getEntityTranslucent(resolvePlayerSkin(state, player))
                    : RenderLayer.getEntityTranslucent(ResourceUtils.formatTextureIdentifier(layerName));

            int packedLight   = light;
            int damageOverlay = OverlayTexture.DEFAULT_UV;

            matrices.push();
            try {
                float handShift = (state.handSize - 1.0F) * MORPHER_HAND_SIZE_SHIFT;
                if (handShift != 0.0F) matrices.translate(0.0F, handShift, 0.0F);
                matrices.translate(-0.0125F, 1.5F, -0.012F);
                matrices.multiply(new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(180.0F),
                        (float) Math.toRadians(180.0F),
                        (float) Math.toRadians(0.0F)
                ));

                GeoBone mainBone = playerBone.get();
                resetBoneToInitialSnapshot(mainBone);

                renderer.renderRecursively(
                        matrices, animatable, mainBone, playerTexture, buffers,
                        buffers.getBuffer(playerTexture), true, 1.0F,
                        packedLight, damageOverlay, 0xFFFFFFFF
                );

                if (state.layerHand && state.layerEnabled) {
                    if (layerBone.isPresent()) {
                        GeoBone layerGeoBone = layerBone.get();
                        resetBoneToInitialSnapshot(layerGeoBone);

                        renderer.renderRecursively(
                                matrices, animatable, layerGeoBone, layerTexture, buffers,
                                buffers.getBuffer(layerTexture), true, 1.0F,
                                packedLight, damageOverlay, 0xFFFFFFFF
                        );
                    }

                    if (state.extraLayers != null) {
                        for (MorphData.ExtraLayer extraLayer : state.extraLayers) {
                            if (extraLayer.name.isEmpty() || extraLayer.name.equals("empty")) continue;

                            ArmorModel extraLayerModel = morpher$armLayerModels.computeIfAbsent(extraLayer.name, name -> {
                                ArmorModel cachedModel = ArmorModel.createLayer();
                                cachedModel.setLayerName(name);
                                return cachedModel;
                            });
                            Identifier extModelId = ResourceUtils.safeIdentifier("morpher", "geo/" + ResourceUtils.cleanModelPath(extraLayer.name) + ".geo.json");

                            if (!ResourceUtils.isLoaded(extModelId)) continue;
                            extraLayerModel.getBakedModel(extModelId);

                            Optional<GeoBone> extLayerBone = extraLayerModel.getBone(boneName);
                            if (extLayerBone.isPresent()) {
                                Identifier extTexId;
                                if (!extraLayer.texture.isEmpty() && !extraLayer.texture.equals("empty")) {
                                    extTexId = ResourceUtils.safeIdentifier("morpher", "textures/" + ResourceUtils.cleanTexturePath(extraLayer.texture) + ".png");
                                } else {
                                    extTexId = ResourceUtils.safeIdentifier("morpher", "textures/" + ResourceUtils.cleanTexturePath(extraLayer.name) + ".png");
                                }

                                RenderLayer extLayerTexture = RenderLayer.getEntityTranslucent(extTexId);
                                GeoBone extGeoBone = extLayerBone.get();
                                resetBoneToInitialSnapshot(extGeoBone);

                                renderer.renderRecursively(
                                        matrices, animatable, extGeoBone, extLayerTexture, buffers,
                                        buffers.getBuffer(extLayerTexture), true, 1.0F,
                                        packedLight, damageOverlay, 0xFFFFFFFF
                                );
                            }
                        }
                    }
                }
            } finally {
                matrices.pop();
            }
            ci.cancel();
        } catch (Throwable t) {
            LogOnce.warn("arm-render-" + t.getClass().getName(), "[Morpher] First-person arm render failed; vanilla arm render will be used.", t);
        }
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
    private RenderLayer getPlayerTexture(MorphData.PlayerMorphState state,
                                         AbstractClientPlayerEntity player) {
        if (state.texture.isEmpty())
            return RenderLayer.getEntityTranslucent(resolvePlayerSkin(state, player));
        return RenderLayer.getEntityTranslucent(
                ResourceUtils.formatTextureIdentifier(state.texture));
    }

    @Unique
    private Identifier resolvePlayerSkin(MorphData.PlayerMorphState state,
                                        AbstractClientPlayerEntity player) {
        if (state.skinPlayerName != null && !state.skinPlayerName.isEmpty()) {
            Identifier skin = SkinResolver.getSkin(state.skinPlayerName);
            if (skin != null) return skin;
        }
        return player.getSkinTextures().texture();
    }

    @Unique
    private MorphRenderer createRenderer() {
        return azbd.morpher.render.RenderHelpers.createRenderer();
    }
}
