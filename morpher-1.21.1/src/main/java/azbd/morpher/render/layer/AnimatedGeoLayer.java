package azbd.morpher.render.layer;

import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.overlay.CharacterOverlay;
import azbd.morpher.overlay.OverlayManager;
import azbd.morpher.render.MorphRenderer;
import azbd.morpher.render.handlers.GeoBoneUtils;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class AnimatedGeoLayer extends GeoRenderLayer<MorphEntity> {

    private final AnimatedLayerModel layerAnimModel = new AnimatedLayerModel();
    private final Map<String, AnimatedLayerAnimatable> layerAnimatables = new java.util.HashMap<>();
    private final Map<GeoBone, float[]> boneStateMap = new IdentityHashMap<>();

    public AnimatedGeoLayer(GeoRenderer<MorphEntity> entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public void render(MatrixStack poseStack, MorphEntity animatableEntity, BakedGeoModel playerBakedModel,
                       RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {

        MorphRenderer morphRenderer = (MorphRenderer) this.getRenderer();
        if (morphRenderer.isRenderingLayer()) return;

        PlayerEntity player = animatableEntity.getCurrentEntity();
        if (player == null) return;

        MorphData.PlayerMorphState state = MorphData.get(player);
        if (!state.layerEnabled) return;

        List<LayerEntry> allLayers = buildLayerList(state);
        if (allLayers.isEmpty()) return;

        String globalAnim = state.activeLayerAnimation;
        int version = state.layerAnimationVersion;

        for (LayerEntry entry : allLayers) {
            String layerName = entry.model;
            if (layerName == null || layerName.isEmpty() || "empty".equalsIgnoreCase(layerName)) continue;

            String activeAnim = state.layerAnimations.getOrDefault(layerName, globalAnim);
            layerAnimModel.setLayerName(layerName);
            layerAnimModel.setTextureName(entry.texture);
            AnimatedLayerAnimatable animatableLayer = layerAnimatables.computeIfAbsent(layerName, ignored -> new AnimatedLayerAnimatable());
            animatableLayer.setCurrentAnim(activeAnim, version);

            Identifier modelId  = layerAnimModel.getModelResource(animatableLayer);
            Identifier textureId = layerAnimModel.getTextureResource(animatableLayer);

            BakedGeoModel layerGeoModel = layerAnimModel.getBakedModel(modelId);
            if (layerGeoModel == null) continue;

            RenderLayer layerRenderType = RenderLayer.getEntityTranslucent(textureId);
            VertexConsumer layerConsumer = bufferSource.getBuffer(layerRenderType);

            saveAndReset(layerGeoModel.topLevelBones());
            copyPlayerBones(playerBakedModel, layerGeoModel.topLevelBones());

            if (!activeAnim.isEmpty() && !"none".equalsIgnoreCase(activeAnim) && !"empty".equalsIgnoreCase(activeAnim)) {
                try {
                    AnimationState<AnimatedLayerAnimatable> animState =
                            new AnimationState<>(animatableLayer, 0, 0, partialTick, false);
                    layerAnimModel.handleAnimations(animatableLayer, getLayerInstanceId(player, layerName), animState, partialTick);
                } catch (Exception ignored) {}
            }

            poseStack.push();
            try {
                morphRenderer.renderDirectly(poseStack, animatableEntity, layerGeoModel, layerRenderType,
                        bufferSource, layerConsumer, false, partialTick, packedLight, packedOverlay,
                        0xFFFFFFFF);
            } finally {
                poseStack.pop();
                restoreSnapshot(layerGeoModel.topLevelBones());
            }
        }
    }

    private static class LayerEntry {
        final String model;
        final String texture;
        LayerEntry(String model, String texture) {
            this.model = model;
            this.texture = texture;
        }
    }

    private List<LayerEntry> buildLayerList(MorphData.PlayerMorphState state) {
        List<LayerEntry> list = new ArrayList<>();
        if (state.layerName != null && !state.layerName.isEmpty() && !"empty".equalsIgnoreCase(state.layerName)) {
            String texture = "";
            CharacterOverlay overlay = OverlayManager.getOverlay(state.activeOverlay);
            if (overlay != null && overlay.layer != null) {
                texture = overlay.layer.getTexture(state.layerTextureIndex);
            }
            list.add(new LayerEntry(state.layerName, texture));
        }
        if (state.extraLayers != null) {
            for (MorphData.ExtraLayer el : state.extraLayers) {
                if (el.name != null && !el.name.isEmpty() && !"empty".equalsIgnoreCase(el.name)) {
                    list.add(new LayerEntry(el.name, el.texture));
                }
            }
        }
        return list;
    }

    private void saveAndReset(List<? extends GeoBone> bones) {
        GeoBoneUtils.saveAndReset(bones, boneStateMap, false);
    }

    private void restoreSnapshot(List<? extends GeoBone> bones) {
        GeoBoneUtils.restore(bones, boneStateMap);
    }

    private void copyPlayerBones(BakedGeoModel playerModel, List<? extends GeoBone> layerBones) {
        GeoBoneUtils.copyAnimations(playerModel, layerBones);
    }

    private long getLayerInstanceId(PlayerEntity player, String layerName) {
        long playerId = player == null ? 0L : player.getId();
        long layerId  = layerName == null ? 0L : layerName.hashCode();
        return (playerId << 32) ^ (layerId & 0xFFFFFFFFL);
    }
}
