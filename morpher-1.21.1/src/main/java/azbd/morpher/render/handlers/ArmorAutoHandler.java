package azbd.morpher.render.handlers;

import azbd.morpher.morph.MorphEntity;
import azbd.morpher.morph.MorphData;
import azbd.morpher.render.MorphRenderer;
import azbd.morpher.render.SkinResolver;
import azbd.morpher.util.ResourceUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ArmorAutoHandler {

    private final MorphRenderer renderer;
    private final Map<GeoBone, float[]> boneStates = new IdentityHashMap<>();

    public ArmorAutoHandler(MorphRenderer renderer) {
        this.renderer = renderer;
    }

    public boolean renderForSlot(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                 VertexConsumerProvider buffers, float partialTick,
                                 int light, int overlay, ItemStack stack, EquipmentSlot slot) {
        if (!(stack.getItem() instanceof GeoItem)) return false;
        return tryRenderGeoArmorFromOtherMod(matrices, player, entity, buffers,
                partialTick, light, overlay, stack, slot);
    }

    public boolean renderOnArm(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                               VertexConsumerProvider buffers, float partialTick,
                               int light, int overlay, ItemStack stack, Arm arm) {
        if (!(stack.getItem() instanceof GeoItem)) return false;
        return tryRenderGeoArmorFromOtherModOnArm(matrices, player, entity, buffers,
                partialTick, light, overlay, stack, arm);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean tryRenderGeoArmorFromOtherMod(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                                  VertexConsumerProvider buffers, float partialTick,
                                                  int light, int overlay, ItemStack stack, EquipmentSlot slot) {
        try {
            GeoRenderProvider renderProvider = GeoRenderProvider.of(stack);
            if (renderProvider == null) return false;

            BipedEntityModel<PlayerEntity> dummyBase = getDummyBaseModel();
            if (dummyBase == null) return false;

            BipedEntityModel<?> rawModel = renderProvider.getGeoArmorRenderer(player, stack, slot, dummyBase);
            if (!(rawModel instanceof GeoArmorRenderer<?>)) return false;

            GeoArmorRenderer<?> geoRenderer = (GeoArmorRenderer<?>) rawModel;
            GeoModel geoModel = geoRenderer.getGeoModel();
            if (geoModel == null) return false;

            GeoItem geoItem = (GeoItem) stack.getItem();
            Identifier modelId = geoModel.getModelResource(geoItem);
            Identifier textureId = geoModel.getTextureResource(geoItem);
            if (modelId == null || textureId == null) return false;

            BakedGeoModel bakedModel = geoModel.getBakedModel(modelId);
            if (bakedModel == null) return false;

            RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
            VertexConsumer buffer = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, stack.hasGlint());

            BakedGeoModel srcModel = renderer.getPlayerModel()
                    .getBakedModel(renderer.getPlayerModel().getModelResource(entity));

            saveAndReset(bakedModel.topLevelBones());
            try {
                copyPoseWorld(srcModel, bakedModel.topLevelBones());

                applyExternalVisibility(bakedModel, slot);

                renderer.renderDirectly(matrices, entity, bakedModel, renderLayer, buffers,
                        buffer, false, partialTick, light, overlay, 0xFFFFFFFF);
            } finally {
                restoreSnapshot(bakedModel.topLevelBones());
            }
            return true;
        } catch (Throwable t) {
            System.err.println("[ArmorAutoHandler] Error rendering external Geo armor in slot " + slot + ": " + t.getMessage());
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean tryRenderGeoArmorFromOtherModOnArm(MatrixStack matrices, PlayerEntity player, MorphEntity entity,
                                                       VertexConsumerProvider buffers, float partialTick,
                                                       int light, int overlay, ItemStack stack, Arm arm) {
        try {
            GeoRenderProvider renderProvider = GeoRenderProvider.of(stack);
            if (renderProvider == null) return false;

            BipedEntityModel<PlayerEntity> dummyBase = getDummyBaseModel();
            if (dummyBase == null) return false;

            BipedEntityModel<?> rawModel = renderProvider.getGeoArmorRenderer(player, stack, EquipmentSlot.CHEST, dummyBase);
            if (!(rawModel instanceof GeoArmorRenderer<?>)) return false;

            GeoArmorRenderer<?> geoRenderer = (GeoArmorRenderer<?>) rawModel;
            GeoModel geoModel = geoRenderer.getGeoModel();
            if (geoModel == null) return false;

            GeoItem geoItem = (GeoItem) stack.getItem();
            Identifier modelId = geoModel.getModelResource(geoItem);
            Identifier textureId = geoModel.getTextureResource(geoItem);
            if (modelId == null || textureId == null) return false;

            BakedGeoModel bakedModel = geoModel.getBakedModel(modelId);
            if (bakedModel == null) return false;

            GeoBone armBone = getBoneFromBakedModel(bakedModel, arm == Arm.RIGHT ? "right_arm" : "left_arm");
            if (armBone == null) return false;

            MorphData.PlayerMorphState state = MorphData.get(player);

            RenderLayer renderLayer = RenderLayer.getArmorCutoutNoCull(textureId);
            VertexConsumer buffer = ItemRenderer.getArmorGlintConsumer(buffers, renderLayer, stack.hasGlint());

            saveAndReset(bakedModel.topLevelBones());
            try {
                if (state.modelHand) {
                    BakedGeoModel morphModel = resolveMorphModel(player);
                    if (morphModel != null) {
                        copyPoseLocal(morphModel, bakedModel.topLevelBones());
                    }
                }

                matrices.push();
                if (state.modelHand) {
                    float handShift = (state.handSize - 1.0F) * 0.25F;
                    if (handShift != 0.0F) matrices.translate(0.0F, handShift, 0.0F);
                }
                matrices.translate(-0.0125F, 1.5F, -0.012F);
                matrices.multiply(new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(180.0F),
                        (float) Math.toRadians(180.0F),
                        0.0F));

                renderer.renderRecursively(
                        matrices, entity, armBone,
                        renderLayer, buffers, buffer,
                        true, partialTick, light, overlay, 0xFFFFFFFF);

                matrices.pop();
            } finally {
                restoreSnapshot(bakedModel.topLevelBones());
            }
            return true;
        } catch (Throwable t) {
            System.err.println("[ArmorAutoHandler] Error rendering external Geo armor on arm " + arm + ": " + t.getMessage());
            return false;
        }
    }

    private BakedGeoModel resolveMorphModel(PlayerEntity player) {
        MorphData.PlayerMorphState state = MorphData.get(player);
        String name = SkinResolver.resolvePlayerModelPath(player, state);
        Identifier id = ResourceUtils.safeIdentifier("morpher",
                "geo/" + ResourceUtils.cleanModelPath(name) + ".geo.json");
        if (!ResourceUtils.isLoaded(id)) {
            id = Identifier.of("morpher", "geo/player_wide.geo.json");
            if (!ResourceUtils.isLoaded(id)) return null;
        }
        return renderer.getPlayerModel().getBakedModel(id);
    }

    private void copyPoseWorld(BakedGeoModel src, List<? extends GeoBone> dstBones) {
        if (src == null || dstBones == null) return;

        Map<String, GeoBone> index = new HashMap<>();
        Map<GeoBone, Matrix4f> deltas = new IdentityHashMap<>();
        buildSrcDeltas(src.topLevelBones(), new Matrix4f(), new Matrix4f(), index, deltas);

        GeoBone anchor = index.get("body");
        if (anchor == null) anchor = index.get("root");
        Matrix4f anchorDelta = anchor != null ? deltas.get(anchor) : null;

        applyWorld(src, index, deltas, dstBones, new Matrix4f(), new Matrix4f(), anchorDelta, false);
    }

    private void buildSrcDeltas(List<? extends GeoBone> bones, Matrix4f parentCur, Matrix4f parentRest,
                                Map<String, GeoBone> index, Map<GeoBone, Matrix4f> deltas) {
        for (GeoBone bone : bones) {
            Matrix4f cur = new Matrix4f(parentCur).mul(localCurrent(bone));
            Matrix4f rest = new Matrix4f(parentRest).mul(localRest(bone));
            Matrix4f delta = new Matrix4f(cur).mul(new Matrix4f(rest).invert());
            deltas.put(bone, delta);
            index.putIfAbsent(boneKey(bone.getName()), bone);
            buildSrcDeltas(bone.getChildBones(), cur, rest, index, deltas);
        }
    }

    private void applyWorld(BakedGeoModel src, Map<String, GeoBone> index, Map<GeoBone, Matrix4f> deltas,
                            List<? extends GeoBone> dstBones, Matrix4f parentCur, Matrix4f parentRest,
                            Matrix4f anchorDelta, boolean covered) {
        for (GeoBone dst : dstBones) {
            Matrix4f rest = new Matrix4f(parentRest).mul(localRest(dst));

            GeoBone srcBone = src.getBone(dst.getName()).orElse(null);
            if (srcBone == null) srcBone = index.get(boneKey(dst.getName()));

            Matrix4f delta = null;
            if (srcBone != null) {
                delta = deltas.get(srcBone);
            } else if (!covered) {
                delta = anchorDelta;
            }

            if (delta != null) {
                Matrix4f local = new Matrix4f(parentCur).invert().mul(delta).mul(rest);
                applyLocalMatrix(dst, local);
            }

            Matrix4f cur = new Matrix4f(parentCur).mul(localCurrent(dst));
            applyWorld(src, index, deltas, dst.getChildBones(), cur, rest, anchorDelta,
                    covered || delta != null);
        }
    }

    private static Matrix4f buildLocal(float posX, float posY, float posZ,
                                       float rotX, float rotY, float rotZ,
                                       float scaleX, float scaleY, float scaleZ,
                                       float pivotX, float pivotY, float pivotZ) {
        return new Matrix4f()
                .translate(-posX / 16.0F, posY / 16.0F, posZ / 16.0F)
                .translate(pivotX / 16.0F, pivotY / 16.0F, pivotZ / 16.0F)
                .rotateZ(rotZ).rotateY(rotY).rotateX(rotX)
                .scale(scaleX, scaleY, scaleZ)
                .translate(-pivotX / 16.0F, -pivotY / 16.0F, -pivotZ / 16.0F);
    }

    private static Matrix4f localCurrent(GeoBone b) {
        return buildLocal(b.getPosX(), b.getPosY(), b.getPosZ(),
                b.getRotX(), b.getRotY(), b.getRotZ(),
                b.getScaleX(), b.getScaleY(), b.getScaleZ(),
                b.getPivotX(), b.getPivotY(), b.getPivotZ());
    }

    private static Matrix4f localRest(GeoBone b) {
        var i = b.getInitialSnapshot();
        return buildLocal(i.getOffsetX(), i.getOffsetY(), i.getOffsetZ(),
                i.getRotX(), i.getRotY(), i.getRotZ(),
                i.getScaleX(), i.getScaleY(), i.getScaleZ(),
                b.getPivotX(), b.getPivotY(), b.getPivotZ());
    }

    private static void applyLocalMatrix(GeoBone bone, Matrix4f m) {
        float sx = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
        float sy = (float) Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12());
        float sz = (float) Math.sqrt(m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22());
        if (sx < 1.0E-6F || sy < 1.0E-6F || sz < 1.0E-6F) return;

        float r00 = m.m00() / sx, r10 = m.m01() / sx, r20 = m.m02() / sx;
        float r11 = m.m11() / sy, r21 = m.m12() / sy;
        float r12 = m.m21() / sz, r22 = m.m22() / sz;

        float rotY = (float) Math.asin(Math.max(-1.0F, Math.min(1.0F, -r20)));
        float rotX, rotZ;
        if (Math.abs(r20) < 0.99999F) {
            rotX = (float) Math.atan2(r21, r22);
            rotZ = (float) Math.atan2(r10, r00);
        } else {
            rotX = (float) Math.atan2(-r12, r11);
            rotZ = 0.0F;
        }

        float px = bone.getPivotX() / 16.0F;
        float py = bone.getPivotY() / 16.0F;
        float pz = bone.getPivotZ() / 16.0F;

        float tx = m.m30() - px + (m.m00() * px + m.m10() * py + m.m20() * pz);
        float ty = m.m31() - py + (m.m01() * px + m.m11() * py + m.m21() * pz);
        float tz = m.m32() - pz + (m.m02() * px + m.m12() * py + m.m22() * pz);

        bone.setPosX(-tx * 16.0F);
        bone.setPosY(ty * 16.0F);
        bone.setPosZ(tz * 16.0F);
        bone.setRotX(rotX);
        bone.setRotY(rotY);
        bone.setRotZ(rotZ);
        bone.setScaleX(sx);
        bone.setScaleY(sy);
        bone.setScaleZ(sz);
        bone.markPositionAsChanged();
        bone.markRotationAsChanged();
        bone.markScaleAsChanged();
    }

    private void copyPoseLocal(BakedGeoModel src, List<? extends GeoBone> dstBones) {
        if (src == null || dstBones == null) return;
        Map<String, GeoBone> index = new HashMap<>();
        indexBones(src.topLevelBones(), index);
        copyLocalRecursive(src, index, dstBones);
    }

    private void indexBones(List<? extends GeoBone> bones, Map<String, GeoBone> index) {
        for (GeoBone bone : bones) {
            index.putIfAbsent(boneKey(bone.getName()), bone);
            indexBones(bone.getChildBones(), index);
        }
    }

    private void copyLocalRecursive(BakedGeoModel src, Map<String, GeoBone> index, List<? extends GeoBone> dstBones) {
        for (GeoBone dst : dstBones) {
            GeoBone s = src.getBone(dst.getName()).orElse(null);
            if (s == null) s = index.get(boneKey(dst.getName()));
            if (s != null) {
                var di = dst.getInitialSnapshot();
                var si = s.getInitialSnapshot();
                dst.setRotX(di.getRotX() + (s.getRotX() - si.getRotX()));
                dst.setRotY(di.getRotY() + (s.getRotY() - si.getRotY()));
                dst.setRotZ(di.getRotZ() + (s.getRotZ() - si.getRotZ()));
                dst.setPosX(di.getOffsetX() + (s.getPosX() - si.getOffsetX()));
                dst.setPosY(di.getOffsetY() + (s.getPosY() - si.getOffsetY()));
                dst.setPosZ(di.getOffsetZ() + (s.getPosZ() - si.getOffsetZ()));
                dst.setScaleX(di.getScaleX() * ratio(s.getScaleX(), si.getScaleX()));
                dst.setScaleY(di.getScaleY() * ratio(s.getScaleY(), si.getScaleY()));
                dst.setScaleZ(di.getScaleZ() * ratio(s.getScaleZ(), si.getScaleZ()));
                dst.markRotationAsChanged();
                dst.markPositionAsChanged();
                dst.markScaleAsChanged();
            }
            copyLocalRecursive(src, index, dst.getChildBones());
        }
    }

    private static float ratio(float current, float initial) {
        return Math.abs(initial) < 1.0E-6F ? 1.0F : current / initial;
    }

    private static String boneKey(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : new String[]{"armor", "biped"}) {
                if (s.startsWith(prefix) && s.length() > prefix.length()) {
                    s = s.substring(prefix.length());
                    changed = true;
                }
            }
        }
        s = s.replace("boots", "foot").replace("boot", "foot").replace("feet", "foot");
        if (s.equals("torso") || s.equals("chest")) s = "body";
        return s;
    }

    private static EquipmentSlot exactSlot(String key) {
        return switch (key) {
            case "head" -> EquipmentSlot.HEAD;
            case "body", "rightarm", "leftarm" -> EquipmentSlot.CHEST;
            case "rightleg", "leftleg" -> EquipmentSlot.LEGS;
            case "rightfoot", "leftfoot" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private void applyExternalVisibility(BakedGeoModel model, EquipmentSlot target) {
        hideForeignSlotBones(model.topLevelBones(), target);
    }

    private void hideForeignSlotBones(List<? extends GeoBone> bones, EquipmentSlot target) {
        for (GeoBone bone : bones) {
            EquipmentSlot own = exactSlot(boneKey(bone.getName()));
            if (own != null && own != target && !containsSlotBone(bone, target)) {
                bone.setHidden(true);
            } else {
                hideForeignSlotBones(bone.getChildBones(), target);
            }
        }
    }

    private boolean containsSlotBone(GeoBone bone, EquipmentSlot target) {
        for (GeoBone child : bone.getChildBones()) {
            if (exactSlot(boneKey(child.getName())) == target) return true;
            if (containsSlotBone(child, target)) return true;
        }
        return false;
    }

    private GeoBone getBoneFromBakedModel(BakedGeoModel bakedModel, String name) {
        if (name.equals("right_arm")) {
            return getBoneFromModel(bakedModel, "armorRightArm")
                    .orElse(getBoneFromModel(bakedModel, "right_arm").orElse(null));
        } else if (name.equals("left_arm")) {
            return getBoneFromModel(bakedModel, "armorLeftArm")
                    .orElse(getBoneFromModel(bakedModel, "left_arm").orElse(null));
        }
        return getBoneFromModel(bakedModel, name).orElse(null);
    }

    private Optional<GeoBone> getBoneFromModel(BakedGeoModel model, String name) {
        if (model == null) return Optional.empty();
        return model.getBone(name).filter(b -> b instanceof GeoBone).map(b -> (GeoBone) b);
    }

    private void saveAndReset(List<? extends GeoBone> bones) {
        GeoBoneUtils.saveAndReset(bones, boneStates, true);
    }

    private void restoreSnapshot(List<? extends GeoBone> bones) {
        GeoBoneUtils.restore(bones, boneStates, true);
    }

    private static BipedEntityModel<PlayerEntity> getDummyBaseModel() {
        try {
            ModelPart p = MinecraftClient.getInstance()
                    .getEntityModelLoader()
                    .getModelPart(EntityModelLayers.PLAYER_INNER_ARMOR);
            return new BipedEntityModel<>(p);
        } catch (Throwable t) {
            return null;
        }
    }

}