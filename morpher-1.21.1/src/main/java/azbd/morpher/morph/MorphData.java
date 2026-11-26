package azbd.morpher.morph;

import azbd.morpher.MorpherMod;
import azbd.morpher.overlay.CharacterOverlay;
import azbd.morpher.overlay.OverlayManager;
import azbd.morpher.util.ResourceUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

public class MorphData {

    public record SyncPayload(UUID uuid, NbtCompound nbt) implements CustomPayload {
        public static final CustomPayload.Id<SyncPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "morph_sync"));
        public static final PacketCodec<PacketByteBuf, SyncPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> { buf.writeUuid(value.uuid()); buf.writeNbt(value.nbt()); },
                        buf -> new SyncPayload(buf.readUuid(), buf.readNbt())
                );
        @Override public CustomPayload.Id<SyncPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
    }

    private static final Map<UUID, PlayerMorphState> clientData  = new HashMap<>();
    private static final Map<UUID, PlayerMorphState> serverData  = new HashMap<>();
    private static final Map<UUID, PlayerMorphState> deathBackup = new HashMap<>();

    private static final class PlayerMorphPersistentState extends PersistentState {
        private static final String DATA_NAME = "morpher_player_morphs";
        private final Map<UUID, NbtCompound> players = new HashMap<>();
        private static final PersistentState.Type<PlayerMorphPersistentState> TYPE = new PersistentState.Type<>(
                PlayerMorphPersistentState::new, PlayerMorphPersistentState::fromNbt, null);

        private static PlayerMorphPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            PlayerMorphPersistentState state = new PlayerMorphPersistentState();
            NbtCompound entries = nbt.getCompound("players");
            for (String key : entries.getKeys()) {
                try { state.players.put(UUID.fromString(key), entries.getCompound(key).copy()); }
                catch (IllegalArgumentException ignored) { }
            }
            return state;
        }

        @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            NbtCompound entries = new NbtCompound();
            players.forEach((uuid, data) -> entries.put(uuid.toString(), data.copy()));
            nbt.put("players", entries);
            return nbt;
        }

        private static PlayerMorphPersistentState get(MinecraftServer server) {
            PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
            return manager.getOrCreate(TYPE, DATA_NAME);
        }

        private PlayerMorphState load(UUID uuid) {
            PlayerMorphState result = new PlayerMorphState();
            NbtCompound data = players.get(uuid);
            if (data != null) result.fromNbt(data.copy());
            return result;
        }

        private void save(UUID uuid, PlayerMorphState player) {
            players.put(uuid, player.toNbt());
            markDirty();
        }
    }

    public static class ExtraLayer {
        public String name = "";
        public String texture = "";

        public ExtraLayer() {}

        public ExtraLayer(String name, String texture) {
            this.name = name;
            this.texture = texture;
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("name", name);
            nbt.putString("texture", texture);
            return nbt;
        }

        public static ExtraLayer fromNbt(NbtCompound nbt) {
            ExtraLayer layer = new ExtraLayer();
            layer.name = nbt.getString("name");
            layer.texture = nbt.getString("texture");
            return layer;
        }

        public ExtraLayer copy() {
            return new ExtraLayer(name, texture);
        }
    }

    public static class PlayerMorphState {
        public String  model             = "";
        public String  texture           = "";
        public String  animation         = "";
        public boolean renderCustomModel = false;
        public boolean hasJoined         = false;
        public boolean isDead            = false;
        public String  forcedEmote       = "empty";
        public boolean loopEmote         = false;
        public boolean cancelAnimation   = false;
        public boolean instantEmote      = false;
        public String  emoteMode         = "additive";
        public long emoteTriggerId       = 0L;
        public String  layerName             = "";
        public boolean layerEnabled          = true;
        public int     layerTextureIndex     = 0;
        public String  activeLayerAnimation  = "";
        public int     layerAnimationVersion = 0;
        public Map<String, String> layerAnimations = new HashMap<>();
        public String  activeOverlay     = "";
        public String  equipHead         = "";
        public String  equipChest        = "";
        public String  equipLegs         = "";
        public String  equipFeet         = "";
        public float   scale             = 0.9375f;
        public String  overlayNumberFront     = "";
        public String  overlayNumberBack      = "";
        public float   overlayNumberSizeFront = 1.0f;
        public float   overlayNumberSizeBack  = 1.0f;
        public List<ExtraLayer> extraLayers   = new ArrayList<>();
        public String  skinPlayerName    = "";
        public float   nametagOffset     = 0.0f;
        public boolean layerHand         = true;
        public boolean armorHand         = true;
        public boolean capeEnabled       = true;
        public boolean modelHand         = true;
        public float   handSize          = 1.0f;

        public boolean isForcedEmoteMode() { return "forced".equalsIgnoreCase(emoteMode); }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("model",              model);
            nbt.putString("texture",            texture);
            nbt.putString("animation",          animation);
            nbt.putBoolean("hasJoined",         hasJoined);
            nbt.putBoolean("renderCustomModel", renderCustomModel);
            nbt.putString("forcedEmote",        forcedEmote);
            nbt.putBoolean("cancelAnimation",   cancelAnimation);
            nbt.putBoolean("instantEmote",      instantEmote);
            nbt.putString("emoteMode",          emoteMode);
            nbt.putString("layerName",             layerName);
            nbt.putBoolean("layerEnabled",         layerEnabled);
            nbt.putInt("layerTextureIndex",        layerTextureIndex);
            nbt.putString("activeLayerAnimation",  activeLayerAnimation);
            nbt.putInt("layerAnimationVersion",    layerAnimationVersion);
            NbtCompound animsTag = new NbtCompound();
            for (Map.Entry<String, String> entry : layerAnimations.entrySet()) {
                animsTag.putString(entry.getKey(), entry.getValue());
            }
            nbt.put("layerAnimations", animsTag);
            nbt.putBoolean("loopEmote",            loopEmote);
            nbt.putLong("emoteTriggerId", emoteTriggerId);
            nbt.putBoolean("isDead",            isDead);
            nbt.putString("activeOverlay",      activeOverlay);
            nbt.putString("equipHead",          equipHead);
            nbt.putString("equipChest",         equipChest);
            nbt.putString("equipLegs",          equipLegs);
            nbt.putString("equipFeet",          equipFeet);
            nbt.putFloat("scale",               scale);
            nbt.putString("overlayNumberFront", overlayNumberFront);
            nbt.putString("overlayNumberBack",  overlayNumberBack);
            nbt.putFloat("overlayNumberSizeFront", overlayNumberSizeFront);
            nbt.putFloat("overlayNumberSizeBack",  overlayNumberSizeBack);

            NbtList list = new NbtList();
            for (ExtraLayer layer : extraLayers) {
                list.add(layer.toNbt());
            }
            nbt.put("extraLayers", list);
            nbt.putString("skinPlayerName", skinPlayerName);
            nbt.putFloat("nametagOffset", nametagOffset);
            nbt.putBoolean("layerHand", layerHand);
            nbt.putBoolean("armorHand", armorHand);
            nbt.putBoolean("capeEnabled", capeEnabled);
            nbt.putBoolean("modelHand", modelHand);
            nbt.putFloat("handSize", handSize);

            return nbt;
        }

        public void fromNbt(NbtCompound nbt) {
            model             = nbt.getString("model");
            texture           = nbt.getString("texture");
            animation         = nbt.getString("animation");
            hasJoined         = nbt.getBoolean("hasJoined");
            renderCustomModel = nbt.getBoolean("renderCustomModel");
            forcedEmote       = nbt.getString("forcedEmote");
            cancelAnimation   = nbt.contains("cancelAnimation") && nbt.getBoolean("cancelAnimation");
            instantEmote      = nbt.contains("instantEmote") && nbt.getBoolean("instantEmote");
            emoteMode         = nbt.contains("emoteMode") ? nbt.getString("emoteMode") : "additive";
            layerName             = nbt.getString("layerName");
            layerEnabled          = !nbt.contains("layerEnabled") || nbt.getBoolean("layerEnabled");
            layerTextureIndex     = nbt.contains("layerTextureIndex") ? nbt.getInt("layerTextureIndex") : 0;
            activeLayerAnimation  = nbt.contains("activeLayerAnimation") ? nbt.getString("activeLayerAnimation") : "";
            layerAnimationVersion = nbt.contains("layerAnimationVersion") ? nbt.getInt("layerAnimationVersion") : 0;
            layerAnimations.clear();
            if (nbt.contains("layerAnimations", NbtElement.COMPOUND_TYPE)) {
                NbtCompound animsTag = nbt.getCompound("layerAnimations");
                for (String key : animsTag.getKeys()) {
                    layerAnimations.put(key, animsTag.getString(key));
                }
            }
            loopEmote             = nbt.getBoolean("loopEmote");
            emoteTriggerId        = nbt.getLong("emoteTriggerId");
            isDead            = nbt.getBoolean("isDead");
            activeOverlay     = nbt.contains("activeOverlay") ? nbt.getString("activeOverlay") : "";
            equipHead         = nbt.contains("equipHead") ? nbt.getString("equipHead") : "";
            equipChest        = nbt.contains("equipChest") ? nbt.getString("equipChest") : "";
            equipLegs         = nbt.contains("equipLegs") ? nbt.getString("equipLegs") : "";
            equipFeet         = nbt.contains("equipFeet") ? nbt.getString("equipFeet") : "";
            scale             = nbt.contains("scale") ? nbt.getFloat("scale") : 0.9375f;
            overlayNumberFront     = nbt.contains("overlayNumberFront") ? nbt.getString("overlayNumberFront") : "";
            overlayNumberBack      = nbt.contains("overlayNumberBack") ? nbt.getString("overlayNumberBack") : "";
            overlayNumberSizeFront = nbt.contains("overlayNumberSizeFront") ? nbt.getFloat("overlayNumberSizeFront") : 1.0f;
            overlayNumberSizeBack  = nbt.contains("overlayNumberSizeBack") ? nbt.getFloat("overlayNumberSizeBack") : 1.0f;

            extraLayers.clear();
            if (nbt.contains("extraLayers", NbtElement.LIST_TYPE)) {
                NbtList list = nbt.getList("extraLayers", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    extraLayers.add(ExtraLayer.fromNbt(list.getCompound(i)));
                }
            }
            skinPlayerName = nbt.contains("skinPlayerName") ? nbt.getString("skinPlayerName") : "";
            nametagOffset  = nbt.contains("nametagOffset") ? nbt.getFloat("nametagOffset") : 0.0f;
            layerHand      = !nbt.contains("layerHand") || nbt.getBoolean("layerHand");
            armorHand      = !nbt.contains("armorHand") || nbt.getBoolean("armorHand");
            capeEnabled    = !nbt.contains("capeEnabled") || nbt.getBoolean("capeEnabled");
            modelHand      = !nbt.contains("modelHand") || nbt.getBoolean("modelHand");
            handSize       = nbt.contains("handSize") ? nbt.getFloat("handSize") : 1.0f;
        }

        public void copyFrom(PlayerMorphState o) {
            model             = o.model;
            texture           = o.texture;
            animation         = o.animation;
            hasJoined         = o.hasJoined;
            renderCustomModel = o.renderCustomModel;
            forcedEmote       = o.forcedEmote;
            cancelAnimation   = o.cancelAnimation;
            instantEmote      = o.instantEmote;
            emoteMode         = o.emoteMode;
            layerName             = o.layerName;
            layerEnabled          = o.layerEnabled;
            layerTextureIndex     = o.layerTextureIndex;
            activeLayerAnimation  = o.activeLayerAnimation;
            layerAnimationVersion = o.layerAnimationVersion;
            layerAnimations.clear();
            layerAnimations.putAll(o.layerAnimations);
            loopEmote             = o.loopEmote;
            emoteTriggerId        = o.emoteTriggerId;
            isDead            = o.isDead;
            activeOverlay     = o.activeOverlay;
            equipHead         = o.equipHead;
            equipChest        = o.equipChest;
            equipLegs         = o.equipLegs;
            equipFeet         = o.equipFeet;
            scale             = o.scale;
            overlayNumberFront     = o.overlayNumberFront;
            overlayNumberBack      = o.overlayNumberBack;
            overlayNumberSizeFront = o.overlayNumberSizeFront;
            overlayNumberSizeBack  = o.overlayNumberSizeBack;

            extraLayers.clear();
            for (ExtraLayer layer : o.extraLayers) {
                extraLayers.add(layer.copy());
            }
            skinPlayerName = o.skinPlayerName;
            nametagOffset  = o.nametagOffset;
            layerHand      = o.layerHand;
            armorHand      = o.armorHand;
            capeEnabled    = o.capeEnabled;
            modelHand      = o.modelHand;
            handSize       = o.handSize;
        }

        public void sync(Entity entity) {
            if (entity.getWorld().isClient) return;
            if (!(entity instanceof ServerPlayerEntity player)) return;
            sendSyncPacket(player, this);
        }
    }

    private static void sendSyncPacket(ServerPlayerEntity player, PlayerMorphState state) {
        if (player == null || player.isDisconnected()) return;

        PlayerMorphPersistentState.get(player.getServer()).save(player.getUuid(), state);

        SyncPayload payload = new SyncPayload(player.getUuid(), state.toNbt());
        sendPayload(player, payload);
        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other != player) sendPayload(other, payload);
        }
    }

    public static void registerClientSync() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, ctx) -> {
            UUID uuid        = payload.uuid();
            NbtCompound nbt  = payload.nbt();
            ctx.client().execute(() -> {
                if (nbt != null) {
                    PlayerMorphState oldState = clientData.get(uuid);
                    PlayerMorphState state = new PlayerMorphState();
                    state.fromNbt(nbt);
                    clientData.put(uuid, state);

                    if (oldState != null && oldState.isDead && !state.isDead) {
                        azbd.morpher.render.MorphRenderer renderer = MorphCache.getRenderer(uuid);
                        if (renderer != null) {
                            MorphEntity morphEntity = (MorphEntity) renderer.getAnimatable();
                            if (morphEntity != null) {
                                morphEntity.resetArmAndPoseControllers();
                            }
                        }
                    }
                }
            });
        });
    }

    public static void handlePlayerJoin(ServerPlayerEntity player) {
        PlayerMorphState state = getOrCreate(player);
        state.forcedEmote = "empty";
        boolean needsVisibilityDelay = !state.hasJoined || state.isDead;
        if (needsVisibilityDelay) state.isDead = true;

        if (!state.hasJoined) {
            state.hasJoined = true;
            MinecraftServer server = player.getServer();
            if (server != null) {
                ForceModelManager forceMgr = ForceModelManager.get(server);
                if (forceMgr.isEnabled()) {
                    state.renderCustomModel = true;
                    forceMgr.applyPresetTo(player);
                }
            }
        }
        state.sync(player);
        if (needsVisibilityDelay) {
            MorpherMod.scheduleTask(5, () -> {
                PlayerMorphState s = getOrCreate(player);
                s.isDead = false;
                s.sync(player);
            });
        }
        syncAllPlayersInWorld(player);
    }

    public static void handlePlayerDeath(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PlayerMorphState current = getOrCreate(player);
        PlayerMorphState backup  = new PlayerMorphState();
        backup.copyFrom(current);
        deathBackup.put(uuid, backup);
        current.isDead      = true;
        current.forcedEmote = "empty";
        current.loopEmote   = false;
        current.sync(player);
        boolean instant = player.getWorld().getGameRules().getBoolean(GameRules.DO_IMMEDIATE_RESPAWN);
        if (instant) {
            MorpherMod.scheduleTask(2, () -> {
                if (player.isAlive()) {
                    PlayerMorphState s = getOrCreate(player);
                    s.isDead = false;
                    s.sync(player);
                }
            });
        }
    }

    public static void handlePlayerRespawn(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (deathBackup.containsKey(uuid)) {
            PlayerMorphState backup = deathBackup.get(uuid);
            MorpherMod.scheduleTask(5, () -> {
                PlayerMorphState s = getOrCreate(player);
                s.model             = backup.model;
                s.texture           = backup.texture;
                s.animation         = backup.animation;
                s.renderCustomModel = backup.renderCustomModel;
                s.layerName         = backup.layerName;
                s.layerEnabled          = backup.layerEnabled;
                s.layerTextureIndex     = backup.layerTextureIndex;
                s.activeLayerAnimation  = backup.activeLayerAnimation;
                s.layerAnimationVersion = backup.layerAnimationVersion;
                s.hasJoined         = backup.hasJoined;
                s.activeOverlay     = backup.activeOverlay;
                s.equipHead         = backup.equipHead;
                s.equipChest        = backup.equipChest;
                s.equipLegs         = backup.equipLegs;
                s.equipFeet         = backup.equipFeet;
                s.scale             = backup.scale;
                s.overlayNumberFront     = backup.overlayNumberFront;
                s.overlayNumberBack      = backup.overlayNumberBack;
                s.overlayNumberSizeFront = backup.overlayNumberSizeFront;
                s.overlayNumberSizeBack  = backup.overlayNumberSizeBack;

                s.extraLayers.clear();
                for (ExtraLayer layer : backup.extraLayers) {
                    s.extraLayers.add(layer.copy());
                }
                s.isDead            = false;
                s.forcedEmote       = "empty";
                s.loopEmote         = false;
                s.sync(player);
            });
            deathBackup.remove(uuid);
        } else {
            MorpherMod.scheduleTask(3, () -> {
                PlayerMorphState s = getOrCreate(player);
                s.isDead = false;
                s.sync(player);
            });
        }
        syncAllPlayersInWorld(player);
        MorpherMod.scheduleTask(15, () -> refreshModel(player));
    }

    public static void handlePlayerClone(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        PlayerMorphState oldState = getOrCreate(oldPlayer);
        PlayerMorphState newState = getOrCreate(newPlayer);
        newState.copyFrom(oldState);
        newState.isDead      = false;
        newState.forcedEmote = "empty";
        newState.loopEmote   = false;
        MorpherMod.scheduleTask(3, () -> newState.sync(newPlayer));
        syncAllPlayersInWorld(newPlayer);
    }

    public static void handleDimensionChange(ServerPlayerEntity player) {
        syncToTrackingPlayers(player);
        syncAllPlayersInWorld(player);
    }

    public static void syncToTrackingPlayers(ServerPlayerEntity player) {
        SyncPayload payload = new SyncPayload(player.getUuid(), getOrCreate(player).toNbt());
        for (ServerPlayerEntity viewer : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(player)) {
            sendPayload(viewer, payload);
        }
    }

    public static void syncToPlayer(ServerPlayerEntity subject, ServerPlayerEntity viewer) {
        sendPayload(viewer, new SyncPayload(subject.getUuid(), getOrCreate(subject).toNbt()));
    }

    public static void handlePlayerDisconnect(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        deathBackup.remove(uuid);
        PlayerMorphState state = getOrCreate(player);
        state.forcedEmote = "empty";
        state.isDead      = true;
        if (player.getServer() != null) {
            PlayerMorphPersistentState.get(player.getServer()).save(uuid, state);
        }
        SyncPayload payload = new SyncPayload(uuid, state.toNbt());
        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other != player) sendPayload(other, payload);
        }
    }

    public static boolean applyOverlay(ServerPlayerEntity player, String overlayKey) {
        CharacterOverlay overlay = OverlayManager.getOverlay(overlayKey);
        if (overlay == null) return false;
        PlayerMorphState state  = getOrCreate(player);
        state.model             = ResourceUtils.cleanModelPath(overlay.model);
        state.texture           = ResourceUtils.cleanTexturePath(overlay.texture);
        state.animation         = ResourceUtils.cleanAnimationPath(overlay.animation);
        state.renderCustomModel = true;
        state.activeOverlay     = overlayKey;
        state.emoteMode         = overlay.emote_mode != null ? overlay.emote_mode : "additive";
        if (overlay.layer != null) {
            state.layerName         = ResourceUtils.cleanModelPath(overlay.layer.model);
            state.layerEnabled      = overlay.layer.enabled;
            state.layerTextureIndex = 0;
        } else {
            state.layerName = "";
            state.layerEnabled = true;
        }
        if (overlay.equipment != null) {
            state.equipHead  = overlay.equipment.head  != null ? overlay.equipment.head  : "";
            state.equipChest = overlay.equipment.chest != null ? overlay.equipment.chest : "";
            state.equipLegs  = overlay.equipment.legs  != null ? overlay.equipment.legs  : "";
            state.equipFeet  = overlay.equipment.feet  != null ? overlay.equipment.feet  : "";
        } else {
            state.equipHead = state.equipChest = state.equipLegs = state.equipFeet = "";
        }
        state.extraLayers.clear();
        List<CharacterOverlay.ExtraLayerDef> extras = overlay.extra_layers != null ? overlay.extra_layers : overlay.extralayers;
        if (extras != null) {
            for (CharacterOverlay.ExtraLayerDef def : extras) {
                state.extraLayers.add(new ExtraLayer(ResourceUtils.cleanModelPath(def.getModelName()), ResourceUtils.cleanTexturePath(def.texture)));
            }
        }
        if (overlay.layer_hand != null) {
            state.layerHand = overlay.layer_hand;
        } else if (overlay.layerhand != null) {
            state.layerHand = overlay.layerhand;
        }
        if (overlay.armor_hand != null) {
            state.armorHand = overlay.armor_hand;
        } else if (overlay.armorhand != null) {
            state.armorHand = overlay.armorhand;
        }
        state.sync(player);
        return true;
    }

    public static void reapplyActiveOverlay(ServerPlayerEntity player) {
        PlayerMorphState state = getOrCreate(player);
        if (state.activeOverlay == null || state.activeOverlay.isEmpty()) return;
        applyOverlay(player, state.activeOverlay);
    }

    public static void refreshModel(Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        PlayerMorphState state = getOrCreate(player);
        if (!state.renderCustomModel || state.model.isEmpty()) return;
        String m = state.model, t = state.texture, a = state.animation, l = state.layerName;
        boolean r = state.renderCustomModel;
        state.renderCustomModel = false;
        state.sync(player);
        MorpherMod.scheduleTask(1, () -> {
            PlayerMorphState s = getOrCreate(player);
            s.model = m; s.texture = t; s.animation = a; s.layerName = l;
            s.renderCustomModel = r;
            s.sync(player);
        });
    }

    public static void reset(Entity entity) {
        if (entity.getWorld().isClient) return;
        if (!(entity instanceof ServerPlayerEntity player)) return;
        PlayerMorphState state  = getOrCreate(player);
        state.model             = "";
        state.texture           = "";
        state.animation         = "";
        state.renderCustomModel = false;
        state.forcedEmote       = "";
        state.layerName         = "";
        state.layerEnabled          = true;
        state.layerTextureIndex     = 0;
        state.activeLayerAnimation  = "";
        state.layerAnimationVersion = 0;
        state.loopEmote             = false;
        state.isDead            = false;
        state.activeOverlay     = "";
        state.equipHead         = "";
        state.equipChest        = "";
        state.equipLegs         = "";
        state.equipFeet         = "";
        state.scale             = 0.9375f;
        state.skinPlayerName    = "";
        state.layerHand         = true;
        state.armorHand         = true;
        state.capeEnabled       = true;
        state.modelHand         = true;
        state.handSize          = 1.0f;
        state.cancelAnimation   = false;
        state.instantEmote      = false;
        state.emoteMode         = "additive";
        state.nametagOffset     = 0.0f;
        state.overlayNumberFront     = "";
        state.overlayNumberBack      = "";
        state.overlayNumberSizeFront = 1.0f;
        state.overlayNumberSizeBack  = 1.0f;
        state.layerAnimations.clear();
        state.extraLayers.clear();
        state.sync(player);
    }

    public static void syncAllPlayersInWorld(Entity trigger) {
        if (!(trigger.getWorld() instanceof ServerWorld world)) return;
        for (ServerPlayerEntity p : world.getPlayers()) getOrCreate(p).sync(p);
    }

    private static PlayerMorphState getOrCreate(PlayerEntity player) {
        UUID uuid = player.getUuid();
        if (player.getWorld().isClient) return clientData.computeIfAbsent(uuid, k -> new PlayerMorphState());
        if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.getServer() != null) {
            return serverData.computeIfAbsent(uuid, k -> PlayerMorphPersistentState.get(serverPlayer.getServer()).load(k));
        }
        return serverData.computeIfAbsent(uuid, k -> new PlayerMorphState());
    }

    public static PlayerMorphState get(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return new PlayerMorphState();
        return getOrCreate(player);
    }

    public static void clearAllData() {
        clientData.clear();
        serverData.clear();
        deathBackup.clear();
    }

    private static void sendPayload(ServerPlayerEntity player, CustomPayload payload) {
        if (player == null || player.isDisconnected()) return;
        try {
            ServerPlayNetworking.send(player, payload);
        } catch (Exception e) {
            MorpherMod.LOGGER.warn("[Morpher] Could not sync morph data to {}.", player.getName().getString());
        }
    }
}
