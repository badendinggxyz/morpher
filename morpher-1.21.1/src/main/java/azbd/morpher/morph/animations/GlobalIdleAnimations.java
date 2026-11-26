package azbd.morpher.morph.animations;

import azbd.morpher.morph.MorphData;
import azbd.morpher.morph.MorphEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

public class GlobalIdleAnimations {

    private static final RawAnimation NORMAL_IDLE_GLOBAL  = RawAnimation.begin().thenLoop("idle_global");

    private static final float MOVE_THRESHOLD = 0.01f;

    public static AnimationController<MorphEntity> register(MorphEntity me) {
        return new AnimationController<>(me, "global_idle", 4, as -> {
            PlayerEntity player = me.currentPlayer;
            if (player == null) return PlayState.STOP;

            if (me.animationPath == null || me.animationPath.isEmpty()) return PlayState.STOP;

            if (MorphData.get(player).isForcedEmoteMode() && !me.currentAnimation.equals("empty")) return PlayState.STOP;

            if (player.isSleeping()) return PlayState.STOP;

            ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
            if (chest.getItem() instanceof ElytraItem && player.isFallFlying()) {
                return PlayState.STOP;
            }

            EntityPose pose = player.getPose();
            if (player.isSwimming() || pose == EntityPose.SWIMMING) return PlayState.STOP;
            if (player.hasVehicle() || pose == EntityPose.SITTING) return PlayState.STOP;

            boolean sneaking = player.isSneaking() || pose == EntityPose.CROUCHING;
            float limbSpeed = Math.min(player.limbAnimator.getSpeed(0f), 1f);
            boolean isMoving = limbSpeed > MOVE_THRESHOLD;

            if ((!sneaking && !isMoving) || !me.hasAnimation("idle_global")) {
                return PlayState.STOP;
            }
            as.getController().setAnimationSpeed(1.0);
            return as.setAndContinue(NORMAL_IDLE_GLOBAL);
        });
    }
}
