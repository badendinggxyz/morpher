package azbd.morpher.render.handlers;

import net.minecraft.entity.EquipmentSlot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeoBoneUtils {
    private static final Set<String> SLOT_BONES = Set.of(
            "armorHead", "head", "armorBody", "body", "armorRightArm", "right_arm", "rightArm",
            "armorLeftArm", "left_arm", "leftArm", "armorRightLeg", "right_leg", "rightLeg",
            "armorLeftLeg", "left_leg", "leftLeg", "armorRightBoot", "right_boot", "rightBoot",
            "armorLeftBoot", "left_boot", "leftBoot", "armorRightFoot", "right_foot", "rightFoot",
            "armorLeftFoot", "left_foot", "leftFoot", "armorBipedRightFoot", "armorBipedLeftFoot");

    private GeoBoneUtils() {}

    public static void saveAndReset(List<? extends GeoBone> bones, Map<GeoBone, float[]> states, boolean makeVisible) {
        for (GeoBone bone : bones) {
            states.put(bone, new float[]{bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                    bone.getPosX(), bone.getPosY(), bone.getPosZ(), bone.getScaleX(), bone.getScaleY(), bone.getScaleZ(),
                    bone.getPivotX(), bone.getPivotY(), bone.getPivotZ(), bone.isHidden() ? 1f : 0f});
            var initial = bone.getInitialSnapshot();
            bone.setRotX(initial.getRotX()); bone.setRotY(initial.getRotY()); bone.setRotZ(initial.getRotZ()); bone.markRotationAsChanged();
            bone.setPosX(initial.getOffsetX()); bone.setPosY(initial.getOffsetY()); bone.setPosZ(initial.getOffsetZ()); bone.markPositionAsChanged();
            bone.setScaleX(initial.getScaleX()); bone.setScaleY(initial.getScaleY()); bone.setScaleZ(initial.getScaleZ()); bone.markScaleAsChanged();
            if (makeVisible) bone.setHidden(false);
            saveAndReset(bone.getChildBones(), states, makeVisible);
        }
    }

    public static void restore(List<? extends GeoBone> bones, Map<GeoBone, float[]> states) {
        restore(bones, states, false);
    }

    public static void restore(List<? extends GeoBone> bones, Map<GeoBone, float[]> states, boolean makeVisible) {
        for (GeoBone bone : bones) {
            float[] s = states.remove(bone);
            if (s != null) {
                bone.setRotX(s[0]); bone.setRotY(s[1]); bone.setRotZ(s[2]); bone.markRotationAsChanged();
                bone.setPosX(s[3]); bone.setPosY(s[4]); bone.setPosZ(s[5]); bone.markPositionAsChanged();
                bone.setScaleX(s[6]); bone.setScaleY(s[7]); bone.setScaleZ(s[8]); bone.markScaleAsChanged();
                bone.setPivotX(s[9]); bone.setPivotY(s[10]); bone.setPivotZ(s[11]);
                bone.setHidden(makeVisible ? false : s[12] != 0f);
            }
            restore(bone.getChildBones(), states, makeVisible);
        }
    }

    public static void copyAnimations(BakedGeoModel src, List<? extends GeoBone> dstBones) {
        if (src == null || dstBones == null) return;
        for (GeoBone dst : dstBones) {
            src.getBone(dst.getName()).ifPresent(srcBone -> {
                dst.setPosX(srcBone.getPosX()); dst.setPosY(srcBone.getPosY()); dst.setPosZ(srcBone.getPosZ());
                dst.setRotX(srcBone.getRotX()); dst.setRotY(srcBone.getRotY()); dst.setRotZ(srcBone.getRotZ());
                dst.setScaleX(srcBone.getScaleX()); dst.setScaleY(srcBone.getScaleY()); dst.setScaleZ(srcBone.getScaleZ());
                dst.setPivotX(srcBone.getPivotX()); dst.setPivotY(srcBone.getPivotY()); dst.setPivotZ(srcBone.getPivotZ());
                dst.markRotationAsChanged(); dst.markPositionAsChanged(); dst.markScaleAsChanged();
            });
            copyAnimations(src, dst.getChildBones());
        }
    }

    public static void applyVisibilityForSlot(BakedGeoModel model, EquipmentSlot slot, boolean requireSlotBones) {
        if (requireSlotBones && SLOT_BONES.stream().noneMatch(name -> model.getBone(name).isPresent())) return;
        hideAll(model.topLevelBones());
        Set<String> active = switch (slot) {
            case HEAD -> Set.of("armorHead", "head");
            case CHEST -> Set.of("armorBody", "body", "armorRightArm", "right_arm", "armorLeftArm", "left_arm");
            case LEGS -> Set.of("armorRightLeg", "right_leg", "armorLeftLeg", "left_leg");
            case FEET -> Set.of("armorRightBoot", "right_boot", "rightBoot", "armorLeftBoot", "left_boot", "leftBoot",
                    "armorRightFoot", "right_foot", "rightFoot", "armorLeftFoot", "left_foot", "leftFoot",
                    "armorBipedRightFoot", "armorBipedLeftFoot");
            default -> Set.of();
        };
        for (String name : active) show(model, name, active);
    }

    private static void hideAll(List<? extends GeoBone> bones) {
        for (GeoBone bone : bones) { bone.setHidden(true); hideAll(bone.getChildBones()); }
    }

    private static void show(BakedGeoModel model, String name, Set<String> active) {
        model.getBone(name).ifPresent(bone -> {
            bone.setHidden(false);
            for (GeoBone parent = bone.getParent(); parent != null; parent = parent.getParent()) parent.setHidden(false);
            showChildren(bone, active);
        });
    }

    private static void showChildren(GeoBone bone, Set<String> active) {
        for (GeoBone child : bone.getChildBones()) {
            if (SLOT_BONES.contains(child.getName()) && !active.contains(child.getName())) continue;
            child.setHidden(false);
            showChildren(child, active);
        }
    }
}
