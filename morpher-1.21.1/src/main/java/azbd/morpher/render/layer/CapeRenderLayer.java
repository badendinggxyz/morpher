package azbd.morpher.render.layer;

import azbd.morpher.MorpherMod;
import azbd.morpher.morph.MorphEntity;
import azbd.morpher.morph.MorphData;
import azbd.morpher.render.MorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

public class CapeRenderLayer extends GeoRenderLayer<MorphEntity> {

    private ModelPart wideCloak;
    private ModelPart slimCloak;
    private boolean modelsLoaded = false;
    private boolean modelErrorLogged = false;

    public CapeRenderLayer(GeoRenderer<MorphEntity> renderer) {
        super(renderer);
    }

    private boolean ensureModelsLoaded() {
        if (this.modelsLoaded) return true;
        try {
            var loader = MinecraftClient.getInstance().getEntityModelLoader();
            if (loader == null) return false;

            this.wideCloak = loader.getModelPart(EntityModelLayers.PLAYER).getChild("cloak");
            this.slimCloak = loader.getModelPart(EntityModelLayers.PLAYER_SLIM).getChild("cloak");
            this.modelsLoaded = true;
            return true;
        } catch (Exception e) {
            if (!this.modelErrorLogged) {
                MorpherMod.LOGGER.error("[Morpher] Failed to load vanilla player cloak ModelPart from EntityModelLoader", e);
                this.modelErrorLogged = true;
            }
            return false;
        }
    }

    @Override
    public void renderForBone(MatrixStack poseStack, MorphEntity animatableEntity, GeoBone bone,
                              RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {

        if (!"cape".equals(bone.getName())) return;

        MorphRenderer morphRenderer = (MorphRenderer) this.getRenderer();
        if (morphRenderer.isRenderingLayer()) return;

        PlayerEntity player = animatableEntity.getCurrentEntity();
        if (player == null) return;
        if (!MorphData.get(player).capeEnabled) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        if (!morphRenderer.getVisibility().shouldRenderLayer(clientPlayer)) return;

        if (!clientPlayer.isPartVisible(PlayerModelPart.CAPE)) return;

        SkinTextures skinTextures = clientPlayer.getSkinTextures();
        if (skinTextures == null) return;
        Identifier capeTexture = skinTextures.capeTexture();
        if (capeTexture == null) return;

        ItemStack chestStack = clientPlayer.getEquippedStack(EquipmentSlot.CHEST);
        if (chestStack.getItem() instanceof ElytraItem) return;

        if (!ensureModelsLoaded()) return;

        boolean isSlim = skinTextures.model() == SkinTextures.Model.SLIM;
        ModelPart cloak = isSlim ? this.slimCloak : this.wideCloak;
        if (cloak == null) return;

        double interpX = MathHelper.lerp(partialTick, clientPlayer.prevCapeX, clientPlayer.capeX)
                - MathHelper.lerp(partialTick, clientPlayer.prevX, clientPlayer.getX());
        double interpY = MathHelper.lerp(partialTick, clientPlayer.prevCapeY, clientPlayer.capeY)
                - MathHelper.lerp(partialTick, clientPlayer.prevY, clientPlayer.getY());
        double interpZ = MathHelper.lerp(partialTick, clientPlayer.prevCapeZ, clientPlayer.capeZ)
                - MathHelper.lerp(partialTick, clientPlayer.prevZ, clientPlayer.getZ());

        float bodyYaw = MathHelper.lerpAngleDegrees(partialTick, clientPlayer.prevBodyYaw, clientPlayer.bodyYaw);
        double bodyYawRad = bodyYaw * 0.017453292F;
        double sinYaw = MathHelper.sin((float) bodyYawRad);
        double cosYaw = -MathHelper.cos((float) bodyYawRad);

        float capeSwingY = (float) interpY * 10.0F;
        capeSwingY = MathHelper.clamp(capeSwingY, -6.0F, 32.0F);

        float capeSwingXZ = (float) (interpX * sinYaw + interpZ * cosYaw) * 100.0F;
        capeSwingXZ = MathHelper.clamp(capeSwingXZ, 0.0F, 150.0F);

        float capeSideSwing = (float) (interpX * cosYaw - interpZ * sinYaw) * 100.0F;
        capeSideSwing = MathHelper.clamp(capeSideSwing, -20.0F, 20.0F);

        float strideDist = MathHelper.lerp(partialTick, clientPlayer.prevStrideDistance, clientPlayer.strideDistance);
        capeSwingY += MathHelper.sin(MathHelper.lerp(partialTick, clientPlayer.prevHorizontalSpeed, clientPlayer.horizontalSpeed) * 6.0F) * 32.0F * strideDist;

        if (clientPlayer.isInSneakingPose()) {
            capeSwingY += 25.0F;
        }

        poseStack.push();
        try {
            RenderUtil.translateToPivotPoint(poseStack, bone);

            poseStack.scale(-1.0F, -1.0F, 1.0F);

            poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0F + capeSwingXZ / 2.0F + capeSwingY));
            poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(capeSideSwing / 2.0F));
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - capeSideSwing / 2.0F));

            cloak.resetTransform();
            VertexConsumer consumer = bufferSource.getBuffer(RenderLayer.getEntitySolid(capeTexture));
            cloak.render(poseStack, consumer, packedLight, OverlayTexture.DEFAULT_UV);
        } finally {
            poseStack.pop();
        }
    }
}
