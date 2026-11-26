package azbd.morpher.morph;

import azbd.morpher.MorpherMod;
import azbd.morpher.overlay.CharacterOverlay;
import azbd.morpher.util.ResourceUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.ArrayList;
import java.util.List;

public class ForceModelManager extends PersistentState {

    private static final String DATA_NAME = "morpher_force_model";

    private boolean forceModelEnabled = false;
    private final JoinPresetData joinPreset = new JoinPresetData();

    public ForceModelManager() {}

    public boolean isEnabled() {
        return forceModelEnabled;
    }

    public void setEnabled(boolean enabled) {
        this.forceModelEnabled = enabled;
        markDirty();
    }

    public JoinPresetData getJoinPreset() {
        return joinPreset;
    }

    public static class JoinPresetData {
        public boolean hasPreset = false;
        public String overlay = "";
        public String model = "";
        public String texture = "";
        public String animation = "";
        public String layerName = "";
        public boolean layerHand = true;
        public boolean armorHand = true;
        public boolean modelHand = true;
        public float handSize = 1.0f;
        public String layerAnimation = "";
        public String equipHead = "";
        public String equipChest = "";
        public String equipLegs = "";
        public String equipFeet = "";
        public List<MorphData.ExtraLayer> extraLayers = new ArrayList<>();

        public void clear() {
            hasPreset = false;
            overlay = "";
            model = "";
            texture = "";
            animation = "";
            layerName = "";
            layerHand = true;
            armorHand = true;
            modelHand = true;
            handSize = 1.0f;
            layerAnimation = "";
            equipHead = "";
            equipChest = "";
            equipLegs = "";
            equipFeet = "";
            extraLayers.clear();
        }

        public void setFromOverlay(String overlayName, CharacterOverlay co) {
            hasPreset = true;
            overlay = overlayName;
            model = co.model != null ? ResourceUtils.cleanModelPath(co.model) : "";
            texture = co.texture != null ? ResourceUtils.cleanTexturePath(co.texture) : "";
            animation = co.animation != null ? ResourceUtils.cleanAnimationPath(co.animation) : "";
            if (co.layer != null && co.layer.model != null) {
                layerName = ResourceUtils.cleanModelPath(co.layer.model);
            } else {
                layerName = "";
            }
            if (co.layer_hand != null) layerHand = co.layer_hand;
            else if (co.layerhand != null) layerHand = co.layerhand;
            else layerHand = true;

            if (co.armor_hand != null) armorHand = co.armor_hand;
            else if (co.armorhand != null) armorHand = co.armorhand;
            else armorHand = true;

            if (co.equipment != null) {
                equipHead = co.equipment.head != null ? co.equipment.head : "";
                equipChest = co.equipment.chest != null ? co.equipment.chest : "";
                equipLegs = co.equipment.legs != null ? co.equipment.legs : "";
                equipFeet = co.equipment.feet != null ? co.equipment.feet : "";
            } else {
                equipHead = equipChest = equipLegs = equipFeet = "";
            }
            extraLayers.clear();
            List<CharacterOverlay.ExtraLayerDef> extras = co.extra_layers != null ? co.extra_layers : co.extralayers;
            if (extras != null) {
                for (CharacterOverlay.ExtraLayerDef def : extras) {
                    extraLayers.add(new MorphData.ExtraLayer(ResourceUtils.cleanModelPath(def.getModelName()), def.texture != null ? ResourceUtils.cleanTexturePath(def.texture) : ""));
                }
            }
        }

        public void writeNbt(NbtCompound nbt) {
            nbt.putBoolean("hasPreset", hasPreset);
            nbt.putString("overlay", overlay);
            nbt.putString("model", model);
            nbt.putString("texture", texture);
            nbt.putString("animation", animation);
            nbt.putString("layerName", layerName);
            nbt.putBoolean("layerHand", layerHand);
            nbt.putBoolean("armorHand", armorHand);
            nbt.putBoolean("modelHand", modelHand);
            nbt.putFloat("handSize", handSize);
            nbt.putString("layerAnimation", layerAnimation);
            nbt.putString("equipHead", equipHead);
            nbt.putString("equipChest", equipChest);
            nbt.putString("equipLegs", equipLegs);
            nbt.putString("equipFeet", equipFeet);
            NbtList list = new NbtList();
            for (MorphData.ExtraLayer el : extraLayers) {
                list.add(el.toNbt());
            }
            nbt.put("extraLayers", list);
        }

        public void readNbt(NbtCompound nbt) {
            hasPreset = nbt.getBoolean("hasPreset");
            overlay = nbt.getString("overlay");
            model = nbt.getString("model");
            texture = nbt.getString("texture");
            animation = nbt.getString("animation");
            layerName = nbt.getString("layerName");
            layerHand = !nbt.contains("layerHand") || nbt.getBoolean("layerHand");
            armorHand = !nbt.contains("armorHand") || nbt.getBoolean("armorHand");
            modelHand = !nbt.contains("modelHand") || nbt.getBoolean("modelHand");
            handSize = nbt.contains("handSize") ? nbt.getFloat("handSize") : 1.0f;
            layerAnimation = nbt.contains("layerAnimation") ? nbt.getString("layerAnimation") : "";
            equipHead = nbt.getString("equipHead");
            equipChest = nbt.getString("equipChest");
            equipLegs = nbt.getString("equipLegs");
            equipFeet = nbt.getString("equipFeet");
            extraLayers.clear();
            if (nbt.contains("extraLayers", NbtElement.LIST_TYPE)) {
                NbtList list = nbt.getList("extraLayers", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    extraLayers.add(MorphData.ExtraLayer.fromNbt(list.getCompound(i)));
                }
            }
        }
    }

    public void applyPresetTo(ServerPlayerEntity player) {
        if (!joinPreset.hasPreset) return;
        MorphData.PlayerMorphState state = MorphData.get(player);
        if (state == null) return;

        if (!joinPreset.overlay.isEmpty()) {
            MorphData.applyOverlay(player, joinPreset.overlay);
        }

        if (!joinPreset.model.isEmpty()) {
            state.model = ResourceUtils.cleanModelPath(joinPreset.model);
        }
        if (!joinPreset.texture.isEmpty()) {
            state.texture = ResourceUtils.cleanTexturePath(joinPreset.texture);
        }
        if (!joinPreset.animation.isEmpty()) {
            state.animation = ResourceUtils.cleanAnimationPath(joinPreset.animation);
        }
        if (!joinPreset.layerName.isEmpty()) {
            state.layerName = ResourceUtils.cleanModelPath(joinPreset.layerName);
            state.layerEnabled = true;
        }
        state.layerHand = joinPreset.layerHand;
        state.armorHand = joinPreset.armorHand;
        state.modelHand = joinPreset.modelHand;
        state.handSize = joinPreset.handSize;
        if (!joinPreset.layerName.isEmpty()) {
            if (joinPreset.layerAnimation.isEmpty()) state.layerAnimations.remove(joinPreset.layerName);
            else state.layerAnimations.put(joinPreset.layerName, joinPreset.layerAnimation);
            state.layerAnimationVersion++;
        }
        if (!joinPreset.equipHead.isEmpty()) state.equipHead = joinPreset.equipHead;
        if (!joinPreset.equipChest.isEmpty()) state.equipChest = joinPreset.equipChest;
        if (!joinPreset.equipLegs.isEmpty()) state.equipLegs = joinPreset.equipLegs;
        if (!joinPreset.equipFeet.isEmpty()) state.equipFeet = joinPreset.equipFeet;

        if (!joinPreset.extraLayers.isEmpty()) {
            state.extraLayers.clear();
            for (MorphData.ExtraLayer el : joinPreset.extraLayers) {
                state.extraLayers.add(el.copy());
            }
            state.layerEnabled = true;
        }

        state.renderCustomModel = true;
        state.sync(player);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putBoolean("forceModel", forceModelEnabled);
        NbtCompound presetTag = new NbtCompound();
        joinPreset.writeNbt(presetTag);
        nbt.put("joinPreset", presetTag);
        return nbt;
    }

    private static ForceModelManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ForceModelManager state = new ForceModelManager();
        state.forceModelEnabled = nbt.getBoolean("forceModel");
        if (nbt.contains("joinPreset", NbtElement.COMPOUND_TYPE)) {
            state.joinPreset.readNbt(nbt.getCompound("joinPreset"));
        }
        return state;
    }

    private static final PersistentState.Type<ForceModelManager> TYPE = new PersistentState.Type<>(
            ForceModelManager::new,
            ForceModelManager::fromNbt,
            null
    );

    public static ForceModelManager get(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("MinecraftServer is required");
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) throw new IllegalStateException("Overworld is unavailable");
        PersistentStateManager manager = overworld.getPersistentStateManager();
        return manager.getOrCreate(TYPE, DATA_NAME);
    }

    public static boolean setGlobalForceModel(MinecraftServer server, boolean enabled) {
        ForceModelManager state = get(server);
        state.setEnabled(enabled);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            MorphData.PlayerMorphState ps = MorphData.get(player);
            if (enabled) {
                ps.renderCustomModel = true;
                if (state.getJoinPreset().hasPreset) {
                    state.applyPresetTo(player);
                }
            }
            ps.sync(player);
        }
        MorpherMod.LOGGER.info("[Morpher] Global forceModel set to {}.", enabled);
        return enabled;
    }
}
