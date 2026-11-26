package azbd.morpher.morph.animations;

import azbd.morpher.morph.MorphEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import azbd.morpher.morph.MorphData;

public class MovementAnimations {
    private static final RawAnimation IDLE       = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK       = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN        = RawAnimation.begin().thenLoop("sprint");
    private static final RawAnimation SNEAKING   = RawAnimation.begin().thenLoop("sneaking");
    private static final RawAnimation SNEAK_WALK = RawAnimation.begin().thenLoop("sneaking.walk");
    private static final RawAnimation SWIM       = RawAnimation.begin().thenLoop("swimming");
    private static final RawAnimation SIT        = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation SLEEP      = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation ELYTRA     = RawAnimation.begin().thenLoop("elytra");
    private static final RawAnimation ELYTRAS    = RawAnimation.begin().thenLoop("elytras");


    private static final float MOVE_THRESHOLD = 0.01f;

    public static AnimationController<MorphEntity> register(MorphEntity me) {
        return new AnimationController<>(me, "poses", 4, as -> {
            PlayerEntity player = me.currentPlayer;
            if (player == null) return PlayState.STOP;

            if (me.animationPath == null || me.animationPath.isEmpty()) return PlayState.STOP;

            if (MorphData.get(player).isForcedEmoteMode() && !me.currentAnimation.equals("empty")) return PlayState.STOP;

            as.getController().setAnimationSpeed(1.0);

            ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
            boolean isFallFlying = chest.getItem() instanceof ElytraItem && player.isFallFlying();
            boolean isSleeping = player.isSleeping();
            EntityPose pose = player.getPose();
            boolean isSwimming = player.isSwimming() || pose == EntityPose.SWIMMING;
            boolean isSitting = player.hasVehicle() || pose == EntityPose.SITTING;
            boolean isSneaking = player.isSneaking() || pose == EntityPose.CROUCHING;

            float limbSpeed = Math.min(player.limbAnimator.getSpeed(0f), 1f);
            boolean isMoving = limbSpeed > MOVE_THRESHOLD;

            if (isFallFlying) {
                if (me.hasAnimation("elytra"))  return as.setAndContinue(ELYTRA);
                if (me.hasAnimation("elytras")) return as.setAndContinue(ELYTRAS);
            }

            if (isSleeping) {
                if (me.hasAnimation("sleep")) return as.setAndContinue(SLEEP);
            }

            if (isSwimming) {
                if (me.hasAnimation("swimming")) return as.setAndContinue(SWIM);
            }

            if (isSitting) {
                if (me.hasAnimation("sit")) return as.setAndContinue(SIT);
            }

            if (isSneaking) {
                if (isMoving) {
                    if (me.hasAnimation("sneaking.walk")) return as.setAndContinue(SNEAK_WALK);
                    if (me.hasAnimation("sneaking"))      return as.setAndContinue(SNEAKING);
                    if (me.hasAnimation("walk"))          return as.setAndContinue(WALK);
                } else {
                    if (me.hasAnimation("sneaking"))      return as.setAndContinue(SNEAKING);
                }
            }

            if (isMoving) {
                if (player.isSprinting() && me.hasAnimation("sprint")) {
                    return as.setAndContinue(RUN);
                }
                if (me.hasAnimation("walk")) {
                    return as.setAndContinue(WALK);
                }
            }

            if (me.hasAnimation("idle")) {
                return as.setAndContinue(IDLE);
            }

            return PlayState.STOP;
        });
    }
}
