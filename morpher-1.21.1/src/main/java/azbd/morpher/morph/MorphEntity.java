package azbd.morpher.morph;

import azbd.morpher.morph.animations.*;
import azbd.morpher.render.MorphRenderer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.object.BakedAnimations;
import net.minecraft.util.Identifier;
import java.util.List;
import java.util.Map;

public class MorphEntity extends LivingEntity implements GeoReplacedEntity {
    private static final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.ConcurrentMap<String, Boolean>> ANIMATION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public MorphRenderer renderer;
    public String modelPath;
    public String animationPath;
    public String layerName;
    public PlayerEntity currentPlayer;
    public AnimationController<MorphEntity> emoteController;
    public CombatAnimations combatAnimations;
    public AnimationController<MorphEntity> poseController;
    public AnimationController<MorphEntity> rightArmController;
    public AnimationController<MorphEntity> leftArmController;
    public AnimationController<MorphEntity> globalIdleController;

    public String currentAnimation = "empty";
    public String currentEmote = "undefined";
    private long finishedEmoteTriggerId = Long.MIN_VALUE;

    public MorphEntity() {
        super(EntityType.PLAYER, (World) null);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    public boolean shouldPlayAnimsWhileGamePaused() {
        return true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
    }

    @Override
    public void tick() {
        super.tick();
    }

    public void setRenderer(MorphRenderer renderer, PlayerEntity player) {
        this.renderer      = renderer;
        this.currentPlayer = player;
    }

    public PlayerEntity getCurrentEntity() {
        return this.currentPlayer;
    }

    public String getEmote()           { return this.currentEmote; }
    public void setEmote(String emote) { this.currentEmote = emote; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        this.emoteController = new AnimationController<>(this, "emotes", 1, this::emotePredicate);
        controllers.add(this.emoteController);

        this.combatAnimations = new CombatAnimations(this);
        this.combatAnimations.registerController(controllers);

        this.poseController = MovementAnimations.register(this);
        controllers.add(this.poseController);

        this.globalIdleController = GlobalIdleAnimations.register(this);
        controllers.add(this.globalIdleController);

        this.rightArmController = ArmAnimations.registerRight(this);
        controllers.add(this.rightArmController);

        this.leftArmController = ArmAnimations.registerLeft(this);
        controllers.add(this.leftArmController);
    }

    private software.bernie.geckolib.animation.PlayState emotePredicate(software.bernie.geckolib.animation.AnimationState<MorphEntity> event) {
        PlayerEntity player = this.currentPlayer;
        if (player == null) return software.bernie.geckolib.animation.PlayState.STOP;

        if (this.animationPath == null || this.animationPath.isEmpty()) return software.bernie.geckolib.animation.PlayState.STOP;

        MorphData.PlayerMorphState data = MorphData.get(player);
        AnimationController<MorphEntity> controller = event.getController();

        String requestedAnim = data.forcedEmote;
        boolean loopMode = data.loopEmote;
        if (!loopMode && data.emoteTriggerId == finishedEmoteTriggerId) return software.bernie.geckolib.animation.PlayState.STOP;

        if (requestedAnim == null || requestedAnim.isEmpty() || requestedAnim.equals("empty")) {
            if (!this.currentAnimation.equals("empty")) {
                this.currentAnimation = "empty";
                this.currentEmote = "undefined";
                controller.forceAnimationReset();
                resetArmAndPoseControllers();
            }
            return software.bernie.geckolib.animation.PlayState.STOP;
        }

        if (!requestedAnim.equals(this.currentAnimation)) {
            this.currentAnimation = requestedAnim;
            this.currentEmote = requestedAnim;
            controller.forceAnimationReset();

            if (loopMode) {
                controller.setAnimation(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop(this.currentAnimation));
            } else {
                controller.setAnimation(software.bernie.geckolib.animation.RawAnimation.begin().thenPlay(this.currentAnimation));
            }
        }

        if (!loopMode && controller.hasAnimationFinished()) {
            finishedEmoteTriggerId = data.emoteTriggerId;
            this.currentAnimation = "empty";
            this.currentEmote = "undefined";
            resetArmAndPoseControllers();
            return software.bernie.geckolib.animation.PlayState.STOP;
        }

        return software.bernie.geckolib.animation.PlayState.CONTINUE;
    }

    @Override
    public EntityType<?> getReplacingEntityType() {
        return EntityType.PLAYER;
    }

    @Override
    public Iterable<ItemStack> getArmorItems() {
        return List.of();
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {}

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }

    public void resetArmAndPoseControllers(long id) {
        AnimatableManager<MorphEntity> manager = getAnimatableInstanceCache().getManagerForId(id);
        if (manager != null) {
            for (AnimationController<MorphEntity> controller : manager.getAnimationControllers().values()) {
                controller.forceAnimationReset();
            }
        }
    }

    public void resetArmAndPoseControllers() {
        if (this.currentPlayer != null) {
            resetArmAndPoseControllers(this.currentPlayer.getId());
        }
    }

    public String getModelPath()             { return this.modelPath; }
    public void setModelPath(String path)    { this.modelPath = path; }

    public String getAnimationPath()             { return this.animationPath; }
    public void setAnimationPath(String path)    { this.animationPath = path; }

    public String getLayerName()             { return this.layerName; }
    public void setLayerName(String name)    { this.layerName = name; }

    public boolean hasAnimation(String animName) {
        if (animName == null || animName.isEmpty()) return false;
        String pathKey = this.animationPath == null ? "" : this.animationPath;
        java.util.concurrent.ConcurrentMap<String, Boolean> pathCache = ANIMATION_CACHE.computeIfAbsent(pathKey, ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        Boolean cached = pathCache.get(animName);
        if (cached != null) return cached;
        try {
            Map<Identifier, BakedAnimations> map = GeckoLibCache.getBakedAnimations();
            if (map == null || map.isEmpty()) {
                pathCache.put(animName, false);
                return false;
            }

            String path = (this.animationPath == null || this.animationPath.isEmpty())
                    ? "default" : this.animationPath;
            if (path.endsWith(".animation.json")) {
                path = path.substring(0, path.length() - ".animation.json".length());
            } else if (path.endsWith(".json")) {
                path = path.substring(0, path.length() - ".json".length());
            }
            Identifier id = Identifier.of("morpher", "animations/" + path + ".animation.json");
            BakedAnimations baked = map.get(id);
            if (baked == null) {
                id = Identifier.of("morpher", "animations/default.animation.json");
                baked = map.get(id);
            }
            boolean found = baked != null && baked.getAnimation(animName) != null;
            pathCache.put(animName, found);
            return found;
        } catch (Exception e) {
            return false;
        }
    }

    public static void clearAnimationCache() { ANIMATION_CACHE.clear(); }
}
