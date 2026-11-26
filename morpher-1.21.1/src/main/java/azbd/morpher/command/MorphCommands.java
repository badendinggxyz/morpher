package azbd.morpher.command;

import azbd.morpher.assets.AssetServer;
import azbd.morpher.morph.ForceModelManager;
import azbd.morpher.morph.MorphData;
import azbd.morpher.overlay.CharacterOverlay;
import azbd.morpher.overlay.OverlayManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import azbd.morpher.util.ResourceUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class MorphCommands {

    private static final float MIN_HAND_SIZE = 0.001f;
    private static final float MAX_HAND_SIZE = 3.0f;

    private static final Map<String, List<String>> fileCache = new ConcurrentHashMap<>();

    private static String resolveEmpty(String value) {
        return "empty".equalsIgnoreCase(value) ? "" : value;
    }

    private static List<String> getCachedFiles(Path dir, String ext) {
        String key = dir.toString() + "|" + ext;
        List<String> cached = fileCache.get(key);
        if (cached != null) return cached;
        List<String> values = new ArrayList<>();
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> files = Files.walk(dir)) {
                    files.filter(Files::isRegularFile)
                            .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(ext))
                            .map(p -> {
                                String rel = dir.relativize(p).toString().replace('\\', '/');
                                if (rel.toLowerCase(Locale.ROOT).endsWith(ext)) {
                                    rel = rel.substring(0, rel.length() - ext.length());
                                }
                                if (ext.contains("geo")) {
                                    return ResourceUtils.cleanModelPath(rel);
                                } else if (ext.contains("png")) {
                                    return ResourceUtils.cleanTexturePath(rel);
                                } else if (ext.contains("anim")) {
                                    return ResourceUtils.cleanAnimationPath(rel);
                                }
                                return rel.toLowerCase(Locale.ROOT);
                            })
                            .filter(s -> !s.isBlank())
                            .distinct()
                            .sorted()
                            .forEach(values::add);
                }
            }
        } catch (IOException ignored) {}
        fileCache.put(key, values);
        return values;
    }

    public static void invalidateSuggestionCache() {
        fileCache.clear();
    }

    private static final SuggestionProvider<ServerCommandSource> MODELS = (ctx, b) -> {
        b.suggest("empty");
        getCachedFiles(AssetServer.getDataPath().resolve("geo"), ".geo.json").forEach(b::suggest);
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> ARMOR_MODELS = (ctx, b) -> {
        b.suggest("empty");
        b.suggest("head");
        b.suggest("chest");
        b.suggest("legs");
        b.suggest("feet");
        getCachedFiles(AssetServer.getDataPath().resolve("geo"), ".geo.json").forEach(b::suggest);
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> TEXTURES = (ctx, b) -> {
        b.suggest("empty");
        getCachedFiles(AssetServer.getDataPath().resolve("textures"), ".png").forEach(b::suggest);
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> ANIMATIONS = (ctx, b) -> {
        b.suggest("empty");
        getCachedFiles(AssetServer.getDataPath().resolve("animations"), ".animation.json").forEach(b::suggest);
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> OVERLAYS = (ctx, b) -> {
        OverlayManager.getOverlayNames().forEach(b::suggest);
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> ACTIVE_LAYERS = (ctx, b) -> {
        try {
            java.util.Set<String> uniqueLayers = new java.util.HashSet<>();
            for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                MorphData.PlayerMorphState state = MorphData.get(p);
                if (!state.layerName.isEmpty() && !state.layerName.equals("empty")) {
                    uniqueLayers.add(state.layerName);
                }
                if (state.extraLayers != null) {
                    for (MorphData.ExtraLayer layer : state.extraLayers) {
                        if (!layer.name.isEmpty() && !layer.name.equals("empty")) {
                            uniqueLayers.add(layer.name);
                        }
                    }
                }
            }
            uniqueLayers.forEach(b::suggest);
        } catch (Exception ignored) {}
        return b.buildFuture();
    };


    private static final SuggestionProvider<ServerCommandSource> NUMBER_MODE = (ctx, b) -> {
        b.suggest("forward");
        b.suggest("back");
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> HAND_SIZES = (ctx, b) -> {
        b.suggest("1"); b.suggest("2"); b.suggest("3");
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> EMOTES = (ctx, b) -> {
        try {
            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "players");
            if (players.isEmpty()) return b.buildFuture();
            ServerPlayerEntity player = players.iterator().next();
            MorphData.PlayerMorphState state = MorphData.get(player);
            String animFile = state.animation.isEmpty() ? "default" : state.animation;
            Path animPath = AssetServer.getDataPath()
                    .resolve("animations")
                    .resolve(animFile + ".animation.json");
            if (Files.exists(animPath)) {
                String content = Files.readString(animPath);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("animations")) {
                    for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("animations").entrySet()) {
                        b.suggest(e.getKey());
                    }
                }
            }
        } catch (Exception ignored) {}
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> LAYERS_WITH_ANIM = (ctx, b) -> {
        try {
            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "players");
            Set<String> uniqueLayers = new LinkedHashSet<>();
            for (ServerPlayerEntity player : players) {
                MorphData.PlayerMorphState state = MorphData.get(player);
                if (!state.layerName.isEmpty() && !"empty".equalsIgnoreCase(state.layerName)) {
                    uniqueLayers.add(state.layerName);
                }
                if (state.extraLayers != null) {
                    for (MorphData.ExtraLayer layer : state.extraLayers) {
                        if (!layer.name.isEmpty() && !"empty".equalsIgnoreCase(layer.name)) {
                            uniqueLayers.add(layer.name);
                        }
                    }
                }
            }
            uniqueLayers.forEach(b::suggest);
        } catch (Exception ignored) {}
        return b.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> LAYER_ANIMS_FOR_LAYER = (ctx, b) -> {
        try {
            String layerName = ResourceUtils.cleanAnimationPath(StringArgumentType.getString(ctx, "layerName"));
            b.suggest("clear");
            Path animPath = AssetServer.getDataPath()
                    .resolve("animations")
                    .resolve(layerName + ".animation.json");
            if (!Files.exists(animPath)) {
                animPath = AssetServer.getDataPath()
                        .resolve("animations")
                        .resolve("default.animation.json");
            }
            if (Files.exists(animPath)) {
                String content = Files.readString(animPath);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("animations")) {
                    for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("animations").entrySet()) {
                        b.suggest(e.getKey());
                    }
                }
            }
        } catch (Exception ignored) {}
        return b.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("morpher")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("reload")
                                .executes(MorphCommands::cmdGlobalReload)
                        )
                        .then(CommandManager.literal("forceModel")
                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(MorphCommands::cmdGlobalForceModel)
                                )
                                .then(buildJoinPresetNode("JoinPreset"))
                        )
                        .then(CommandManager.literal("overlay")
                                .then(CommandManager.literal("reload")
                                        .executes(MorphCommands::cmdOverlaysReload)
                                )
                                .then(CommandManager.literal("create")
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .executes(MorphCommands::cmdOverlayCreateSelf)
                                        )
                                )
                                .then(CommandManager.argument("players", EntityArgumentType.players())
                                        .then(CommandManager.literal("set")
                                                .then(CommandManager.argument("overlay", StringArgumentType.string())
                                                        .suggests(OVERLAYS)
                                                        .executes(MorphCommands::cmdOverlaySet)
                                                )
                                        )
                                        .then(CommandManager.literal("armor")
                                                .then(CommandManager.literal("head")
                                                        .then(CommandManager.argument("modelName", StringArgumentType.string())
                                                                .suggests(ARMOR_MODELS)
                                                                .executes(ctx -> cmdSetArmorSlot(ctx, "head"))
                                                        )
                                                )
                                                .then(CommandManager.literal("chestplate")
                                                        .then(CommandManager.argument("modelName", StringArgumentType.string())
                                                                .suggests(ARMOR_MODELS)
                                                                .executes(ctx -> cmdSetArmorSlot(ctx, "chestplate"))
                                                        )
                                                )
                                                .then(CommandManager.literal("leggings")
                                                        .then(CommandManager.argument("modelName", StringArgumentType.string())
                                                                .suggests(ARMOR_MODELS)
                                                                .executes(ctx -> cmdSetArmorSlot(ctx, "leggings"))
                                                        )
                                                )
                                                .then(CommandManager.literal("boots")
                                                        .then(CommandManager.argument("modelName", StringArgumentType.string())
                                                                .suggests(ARMOR_MODELS)
                                                                .executes(ctx -> cmdSetArmorSlot(ctx, "boots"))
                                                        )
                                                )
                                                .then(CommandManager.literal("clear")
                                                        .executes(MorphCommands::cmdClearArmor)
                                                )
                                        )
                                        .then(CommandManager.literal("texturelayer")
                                                .then(CommandManager.argument("index", IntegerArgumentType.integer(0))
                                                        .executes(MorphCommands::cmdOverlayTextureLayer)
                                                )
                                        )
                                        .then(CommandManager.literal("emotemode")
                                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                                        .executes(MorphCommands::cmdOverlayEmoteMode)
                                                )
                                        )
                                )
                        )
                        .then(CommandManager.argument("players", EntityArgumentType.players())
                                .then(CommandManager.literal("clear")
                                        .executes(MorphCommands::cmdClear)
                                )
                                .then(CommandManager.literal("visible")
                                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(MorphCommands::cmdVisible)
                                        )
                                )
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.literal("model")
                                                .then(CommandManager.argument("modelName", StringArgumentType.string())
                                                        .suggests(MODELS)
                                                        .executes(MorphCommands::cmdSetModel)
                                                )
                                        )
                                        .then(CommandManager.literal("texture")
                                                .then(CommandManager.argument("textureName", StringArgumentType.string())
                                                        .suggests(TEXTURES)
                                                        .executes(MorphCommands::cmdSetTexture)
                                                )
                                        )
                                        .then(CommandManager.literal("anim")
                                                .then(CommandManager.argument("animName", StringArgumentType.string())
                                                        .suggests(ANIMATIONS)
                                                        .executes(MorphCommands::cmdSetAnim)
                                                )
                                        )
                                        .then(CommandManager.literal("layer")
                                                .then(CommandManager.argument("layerName", StringArgumentType.string())
                                                        .suggests(MODELS)
                                                        .executes(MorphCommands::cmdSetLayer)
                                                )
                                        )
                                        .then(CommandManager.literal("skin")
                                                .then(CommandManager.argument("skinPlayerName", StringArgumentType.string())
                                                        .executes(MorphCommands::cmdSetSkin)
                                                )
                                        )
                                        .then(CommandManager.literal("extralayer")
                                                .then(CommandManager.literal("add")
                                                        .then(CommandManager.argument("modelName", StringArgumentType.string())
                                                                .suggests(MODELS)
                                                                .executes(MorphCommands::cmdExtraLayerAdd)
                                                        )
                                                )
                                                .then(CommandManager.literal("remove")
                                                        .then(CommandManager.argument("targetLayer", StringArgumentType.greedyString())
                                                                .suggests(ACTIVE_LAYERS)
                                                                .executes(MorphCommands::cmdExtraLayerRemove)
                                                        )
                                                )
                                                .then(CommandManager.literal("list")
                                                        .executes(MorphCommands::cmdExtraLayerList)
                                                )
                                                .then(CommandManager.literal("clear")
                                                        .executes(MorphCommands::cmdExtraLayerClear)
                                                )
                                                .then(CommandManager.literal("texture")
                                                        .then(CommandManager.argument("targetLayer", StringArgumentType.string())
                                                                .suggests(ACTIVE_LAYERS)
                                                                .then(CommandManager.argument("newTexture", StringArgumentType.greedyString())
                                                                        .suggests(TEXTURES)
                                                                        .executes(MorphCommands::cmdExtraLayerSetTexture)
                                                                )
                                                        )
                                                )
                                        )
                                        .then(CommandManager.literal("scale")
                                                .then(CommandManager.argument("value", FloatArgumentType.floatArg(0.1f, 10.0f))
                                                        .executes(MorphCommands::cmdSetScale)
                                                )
                                                .then(CommandManager.literal("empty")
                                                        .executes(MorphCommands::cmdSetScaleDefault)
                                                )
                                                .then(CommandManager.literal("nametag")
                                                        .then(CommandManager.argument("offset", FloatArgumentType.floatArg(-10.0f, 10.0f))
                                                                .executes(MorphCommands::cmdNametagOffset)
                                                        )
                                                        .then(CommandManager.literal("empty")
                                                                .executes(MorphCommands::cmdNametagOffsetDefault)
                                                        )
                                                )
                                        )
                                        .then(CommandManager.literal("handsize")
                                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                                        .suggests(HAND_SIZES)
                                                        .executes(MorphCommands::cmdSetHandSize)
                                                )
                                        )
                                        .then(CommandManager.literal("number")
                                                .then(CommandManager.argument("text", StringArgumentType.string())
                                                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                                                .suggests(NUMBER_MODE)
                                                                .executes(MorphCommands::cmdSetNumber)
                                                                .then(CommandManager.argument("size", FloatArgumentType.floatArg(0.001f, 10.0f))
                                                                        .executes(MorphCommands::cmdSetNumberSize)
                                                                )
                                                        )
                                                )
                                        )
                                        .then(CommandManager.literal("layeranim")
                                                .then(CommandManager.argument("layerName", StringArgumentType.word())
                                                        .suggests(LAYERS_WITH_ANIM)
                                                        .then(CommandManager.argument("animName", StringArgumentType.string())
                                                                .suggests(LAYER_ANIMS_FOR_LAYER)
                                                                .executes(MorphCommands::cmdSetLayerAnim)
                                                        )
                                                        .then(CommandManager.literal("clear")
                                                                .executes(MorphCommands::cmdClearLayerAnim)
                                                        )
                                                )
                                        )
                                )
                                .then(CommandManager.literal("play")
                                        .then(CommandManager.argument("animName", StringArgumentType.string())
                                                .suggests(EMOTES)
                                                .executes(MorphCommands::cmdPlayDefault)
                                                .then(CommandManager.argument("loop", BoolArgumentType.bool())
                                                        .executes(MorphCommands::cmdPlay)
                                                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                                                .executes(MorphCommands::cmdPlayWithMode)
                                                        )
                                                )
                                        )
                                )
                                .then(CommandManager.literal("stop")
                                        .executes(MorphCommands::cmdStop)
                                )
                                .then(CommandManager.literal("layerhand")
                                        .executes(MorphCommands::cmdLayerHandToggle)
                                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                                .executes(MorphCommands::cmdLayerHand)
                                        )
                                )
                                .then(CommandManager.literal("armorhand")
                                        .executes(MorphCommands::cmdArmorHandToggle)
                                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                                .executes(MorphCommands::cmdArmorHand)
                                        )
                                )
                                .then(CommandManager.literal("modelhand")
                                        .executes(MorphCommands::cmdModelHandToggle)
                                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                                .executes(MorphCommands::cmdModelHand)
                                        )
                                )
                                .then(CommandManager.literal("cape")
                                        .executes(MorphCommands::cmdCapeToggle)
                                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                                .executes(MorphCommands::cmdCape)
                                        )
                                )
                        )
        );
    }

    private static int cmdGlobalReload(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        invalidateSuggestionCache();
        OverlayManager.reload();
        AssetServer.invalidateCache();
        ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .forEach(AssetServer::sendToPlayer);
        ctx.getSource().sendMessage(Text.literal("§a[Morpher] Recursos, overlays y modelos de armadura recargados correctamente."));
        return 1;
    }

    private static int cmdOverlaysReload(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        invalidateSuggestionCache();
        OverlayManager.reload();
        ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .forEach(MorphData::reapplyActiveOverlay);
        return 1;
    }

    private static int cmdOverlaySet(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "players");
            String key = StringArgumentType.getString(ctx, "overlay");
            int count = 0;
            for (ServerPlayerEntity player : players) {
                if (MorphData.applyOverlay(player, key)) count++;
                else ctx.getSource().sendError(Text.literal("Overlay no encontrado: " + key));
            }
            return count;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdOverlayTextureLayer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
            MorphData.PlayerMorphState state = MorphData.get(player);
            CharacterOverlay overlay = OverlayManager.getOverlay(state.activeOverlay);
            if (overlay == null || overlay.layer == null || index >= overlay.layer.textureCount()) {
                ctx.getSource().sendError(Text.literal("§c[Morpher] Índice de textura de capa inválido."));
                return 0;
            }
            state.layerTextureIndex = index;
            state.sync(player);
        }
        return 1;
    }

    private static int cmdOverlayEmoteMode(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(Locale.ROOT);
        if (!"additive".equals(mode) && !"forced".equals(mode)) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] emotemode debe ser 'additive' o 'forced'."));
            return 0;
        }
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
            MorphData.PlayerMorphState state = MorphData.get(player);
            state.emoteMode = mode;
            state.sync(player);
        }
        return 1;
    }

    private static int cmdClear(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.reset(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdVisible(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            boolean visible = BoolArgumentType.getBool(ctx, "value");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState state = MorphData.get(player);
                state.renderCustomModel = visible;
                state.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdSetModel(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String raw = StringArgumentType.getString(ctx, "modelName");
            String model = "empty".equalsIgnoreCase(raw) ? "player_wide" : cleanGeoName(raw);
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                s.model = model;
                s.renderCustomModel = true;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdSetTexture(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String texture = cleanTextureName(StringArgumentType.getString(ctx, "textureName"));
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                s.texture = texture;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdSetAnim(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String anim = cleanAnimName(StringArgumentType.getString(ctx, "animName"));
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                s.animation = anim;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdSetLayer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String layer = cleanGeoName(StringArgumentType.getString(ctx, "layerName"));
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                s.layerName = layer;
                s.layerEnabled = true;
                if (!layer.isEmpty()) {
                    s.renderCustomModel = true;
                }
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdSetScale(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            float scale = FloatArgumentType.getFloat(ctx, "value");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.scale = scale;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdSetNumber(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return doSetNumber(ctx, null);
    }

    private static int cmdSetNumberSize(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return doSetNumber(ctx, FloatArgumentType.getFloat(ctx, "size"));
    }

    private static int doSetNumber(CommandContext<ServerCommandSource> ctx, Float size) throws CommandSyntaxException {
        try {
            String rawText = StringArgumentType.getString(ctx, "text");
            String text = rawText.replace("&", "§");
            String mode = StringArgumentType.getString(ctx, "mode");
            boolean clearMode = rawText.isEmpty() || rawText.equalsIgnoreCase("ninguno") || rawText.equalsIgnoreCase("clear");

            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);

                if (!clearMode) {
                    boolean hasFront = false;
                    boolean hasBack = false;

                    String model = s.model.isEmpty() ? "player_wide" : s.model;
                    Path modelPath = AssetServer.getDataPath().resolve("geo").resolve(model + ".geo.json");
                    if (Files.exists(modelPath)) {
                        String content = Files.readString(modelPath);
                        if (content.contains("\"squid_number2\"")) hasFront = true;
                        if (content.contains("\"squid_number\"")) hasBack = true;
                    }

                    if (!s.layerName.isEmpty() && !s.layerName.equals("empty")) {
                        Path layerPath = AssetServer.getDataPath().resolve("geo").resolve(s.layerName + ".geo.json");
                        if (Files.exists(layerPath)) {
                            String content = Files.readString(layerPath);
                            if (content.contains("\"squid_number2\"")) hasFront = true;
                            if (content.contains("\"squid_number\"")) hasBack = true;
                        }
                    }

                    if (mode.equals("forward") && !hasFront) {
                        ctx.getSource().sendError(Text.literal("La carpeta squid_number2 (forward) no existe en el modelo de " + player.getName().getString()));
                        continue;
                    }
                    if (mode.equals("back") && !hasBack) {
                        ctx.getSource().sendError(Text.literal("La carpeta squid_number (back) no existe en el modelo de " + player.getName().getString()));
                        continue;
                    }
                }

                if (mode.equals("forward")) {
                    s.overlayNumberFront = clearMode ? "" : text;
                    if (size != null) s.overlayNumberSizeFront = size;
                }
                if (mode.equals("back")) {
                    s.overlayNumberBack = clearMode ? "" : text;
                    if (size != null) s.overlayNumberSizeBack = size;
                }

                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdPlayDefault(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return executePlay(ctx, false);
    }

    private static int cmdPlay(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return executePlay(ctx, BoolArgumentType.getBool(ctx, "loop"));
    }

    private static int cmdPlayWithMode(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String mode = StringArgumentType.getString(ctx, "mode");
        if (!"additive".equalsIgnoreCase(mode) && !"forced".equalsIgnoreCase(mode)) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] emotemode debe ser 'additive' o 'forced'."));
            return 0;
        }
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
            MorphData.PlayerMorphState state = MorphData.get(player);
            state.emoteMode = mode.toLowerCase(Locale.ROOT);
            state.forcedEmote = StringArgumentType.getString(ctx, "animName");
            state.loopEmote = BoolArgumentType.getBool(ctx, "loop");
            state.emoteTriggerId++;
            state.sync(player);
        }
        return 1;
    }

    private static int executePlay(CommandContext<ServerCommandSource> ctx, boolean loop) throws CommandSyntaxException {
        try {
            String emote = StringArgumentType.getString(ctx, "animName");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.forcedEmote = emote;
                s.emoteTriggerId++;
                s.loopEmote = loop;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdStop(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.forcedEmote = "empty";
                s.loopEmote = false;
                s.cancelAnimation = true;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdExtraLayerAdd(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String model = cleanGeoName(StringArgumentType.getString(ctx, "modelName"));
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                MorphData.ExtraLayer layer = new MorphData.ExtraLayer(model, "");
                s.extraLayers.add(layer);
                s.layerEnabled = true;
                s.renderCustomModel = true;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdExtraLayerRemove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String layerName = cleanGeoName(StringArgumentType.getString(ctx, "targetLayer"));
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                if (cleanGeoName(s.layerName).equalsIgnoreCase(layerName) || s.layerName.equalsIgnoreCase(layerName)) {
                    s.layerName = "";
                }
                if (s.extraLayers != null) {
                    for (int i = 0; i < s.extraLayers.size(); i++) {
                        String name = s.extraLayers.get(i).name;
                        if (name.equalsIgnoreCase(layerName) || cleanGeoName(name).equalsIgnoreCase(layerName)) {
                            s.extraLayers.remove(i);
                            break;
                        }
                    }
                }
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdExtraLayerList(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                int total = s.extraLayers.size() + (!s.layerName.isEmpty() ? 1 : 0);
                ctx.getSource().sendMessage(Text.literal("Layers de " + player.getName().getString() + ": " + total));
                if (!s.layerName.isEmpty()) {
                    ctx.getSource().sendMessage(Text.literal(" [principal] Model: " + s.layerName));
                }
                for (int i = 0; i < s.extraLayers.size(); i++) {
                    MorphData.ExtraLayer l = s.extraLayers.get(i);
                    ctx.getSource().sendMessage(Text.literal(" [" + i + "] Model: " + l.name + " | Tex: " + l.texture));
                }
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdExtraLayerClear(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                s.layerName = "";
                s.extraLayers.clear();
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdExtraLayerSetTexture(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String target = cleanGeoName(StringArgumentType.getString(ctx, "targetLayer"));
            String newTex = cleanTextureName(StringArgumentType.getString(ctx, "newTexture"));

            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                boolean found = false;
                if (s.extraLayers != null) {
                    for (MorphData.ExtraLayer layer : s.extraLayers) {
                        String cleanName = cleanGeoName(layer.name);
                        if (layer.name.equalsIgnoreCase(target) || cleanName.equalsIgnoreCase(target)) {
                            layer.texture = newTex;
                            found = true;
                            break;
                        }
                    }
                }
                if (!found && !s.layerName.isEmpty()) {
                    String cleanLayer = cleanGeoName(s.layerName);
                    if (s.layerName.equalsIgnoreCase(target) || cleanLayer.equalsIgnoreCase(target)) {
                        s.extraLayers.add(new MorphData.ExtraLayer(s.layerName, newTex));
                        s.layerName = "";
                    }
                }
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error al cambiar textura: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdGlobalForceModel(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            boolean force = BoolArgumentType.getBool(ctx, "value");
            ForceModelManager.setGlobalForceModel(ctx.getSource().getServer(), force);
            ctx.getSource().sendFeedback(() ->
                    Text.literal("forceModel global: " + (force ? "ACTIVADO" : "DESACTIVADO")), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("No se pudo cambiar forceModel: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdSetSkin(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String rawSkin = StringArgumentType.getString(ctx, "skinPlayerName");
            String skin = "empty".equalsIgnoreCase(rawSkin) || "clear".equalsIgnoreCase(rawSkin) ? "" : rawSkin;
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                s.skinPlayerName = skin;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdNametagOffset(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            float offset = FloatArgumentType.getFloat(ctx, "offset");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.nametagOffset = offset;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdSetScaleDefault(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            float scale = 0.9375f;
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.scale = scale;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdNametagOffsetDefault(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            float offset = 0.0f;
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.nametagOffset = offset;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdSetLayerAnim(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String layerName = StringArgumentType.getString(ctx, "layerName");
            String anim = resolveEmpty(StringArgumentType.getString(ctx, "animName"));
            if ("clear".equalsIgnoreCase(anim)) {
                return cmdClearLayerAnim(ctx);
            }
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                if (anim.isEmpty()) {
                    s.layerAnimations.remove(layerName);
                } else {
                    s.layerAnimations.put(layerName, anim);
                }
                s.layerAnimationVersion++;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdClearLayerAnim(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String layerName = StringArgumentType.getString(ctx, "layerName");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.layerAnimations.remove(layerName);
                s.layerAnimationVersion++;
                s.sync(player);
            }
            return 1;
        } catch (CommandSyntaxException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static int cmdLayerHand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.layerHand = enabled;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error en layerhand: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdSetHandSize(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Float value = parseHandSize(StringArgumentType.getString(ctx, "value"));
        if (value == null) return 0;
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
            MorphData.PlayerMorphState state = MorphData.get(player);
            state.handSize = value;
            state.sync(player);
        }
        return 1;
    }

    private static Float parseHandSize(String raw) {
        try {
            float value = Float.parseFloat(raw.trim().replace(',', '.'));
            if (Float.isNaN(value) || Float.isInfinite(value)) return null;
            value = Math.max(MIN_HAND_SIZE, Math.min(MAX_HAND_SIZE, value));
            return Math.round(value * 1000.0f) / 1000.0f;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int cmdModelHand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
            MorphData.PlayerMorphState state = MorphData.get(player);
            state.modelHand = enabled;
            state.sync(player);
        }
        return 1;
    }

    private static int cmdModelHandToggle(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
            MorphData.PlayerMorphState state = MorphData.get(player);
            state.modelHand = !state.modelHand;
            state.sync(player);
        }
        return 1;
    }

    private static int cmdCape(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
            MorphData.PlayerMorphState state = MorphData.get(player);
            state.capeEnabled = enabled;
            state.sync(player);
        }
        return 1;
    }

    private static int cmdCapeToggle(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
            MorphData.PlayerMorphState state = MorphData.get(player);
            state.capeEnabled = !state.capeEnabled;
            state.sync(player);
        }
        return 1;
    }

    private static int cmdLayerHandToggle(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.layerHand = !s.layerHand;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error en layerhand: " + e.getMessage()));
            return 0;
        }
    }

    private static String cleanGeoName(String name) {
        if (name == null) return "";
        String s = name.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return resolveEmpty(ResourceUtils.cleanModelPath(s));
    }

    private static String cleanTextureName(String name) {
        if (name == null) return "";
        String s = name.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return resolveEmpty(ResourceUtils.cleanTexturePath(s));
    }

    private static String cleanAnimName(String name) {
        if (name == null) return "";
        String s = name.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return resolveEmpty(ResourceUtils.cleanAnimationPath(s));
    }

    private static int cmdSetArmorSlot(CommandContext<ServerCommandSource> ctx, String slot) throws CommandSyntaxException {
        try {
            String rawModel = StringArgumentType.getString(ctx, "modelName");
            String model = cleanGeoName(rawModel);
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                switch (slot.toLowerCase()) {
                    case "head" -> s.equipHead = model;
                    case "chestplate" -> s.equipChest = model;
                    case "leggins", "leggings" -> s.equipLegs = model;
                    case "boots" -> s.equipFeet = model;
                }
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error en armor: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdClearArmor(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.activeOverlay = "";
                s.equipHead = "";
                s.equipChest = "";
                s.equipLegs = "";
                s.equipFeet = "";
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error al limpiar armadura: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdArmorHand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.armorHand = enabled;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error en armorhand: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdArmorHandToggle(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, "players")) {
                MorphData.PlayerMorphState s = MorphData.get(player);
                s.armorHand = !s.armorHand;
                s.sync(player);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error en armorhand: " + e.getMessage()));
            return 0;
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildJoinPresetNode(String name) {
        return CommandManager.literal(name)
                .then(CommandManager.literal("clear")
                        .executes(MorphCommands::cmdJoinPresetClear)
                )
                .then(CommandManager.literal("apply")
                        .executes(MorphCommands::cmdJoinPresetApplySelf)
                        .then(CommandManager.argument("players", EntityArgumentType.players())
                                .executes(MorphCommands::cmdJoinPresetApplyPlayers)
                        )
                )
                .then(CommandManager.literal("overlay")
                        .then(CommandManager.argument("overlay", StringArgumentType.string())
                                .suggests(OVERLAYS)
                                .executes(MorphCommands::cmdJoinPresetSetOverlay)
                        )
                )
                .then(CommandManager.literal("model")
                        .then(CommandManager.argument("modelName", StringArgumentType.string())
                                .suggests(MODELS)
                                .executes(MorphCommands::cmdJoinPresetSetModel)
                        )
                )
                .then(CommandManager.literal("texture")
                        .then(CommandManager.argument("textureName", StringArgumentType.greedyString())
                                .suggests(TEXTURES)
                                .executes(MorphCommands::cmdJoinPresetSetTexture)
                        )
                )
                .then(CommandManager.literal("anim")
                        .then(CommandManager.argument("animName", StringArgumentType.string())
                                .suggests(ANIMATIONS)
                                .executes(MorphCommands::cmdJoinPresetSetAnim)
                        )
                )
                .then(CommandManager.literal("layer")
                        .then(CommandManager.argument("layerName", StringArgumentType.string())
                                .suggests(MODELS)
                                .executes(MorphCommands::cmdJoinPresetSetLayer)
                        )
                )
                .then(CommandManager.literal("layeranim")
                        .then(CommandManager.argument("layerName", StringArgumentType.string())
                                .then(CommandManager.argument("animName", StringArgumentType.string())
                                        .executes(MorphCommands::cmdJoinPresetLayerAnim)
                                )
                        )
                )
                .then(CommandManager.literal("modelhand")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(MorphCommands::cmdJoinPresetModelHand)
                        )
                )
                .then(CommandManager.literal("handsize")
                        .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                .suggests(HAND_SIZES)
                                .executes(MorphCommands::cmdJoinPresetHandSize)
                        )
                )
                .then(CommandManager.literal("layerhand")
                        .executes(MorphCommands::cmdJoinPresetLayerHandToggle)
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(MorphCommands::cmdJoinPresetLayerHand)
                        )
                )
                .then(CommandManager.literal("armorhand")
                        .executes(MorphCommands::cmdJoinPresetArmorHandToggle)
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(MorphCommands::cmdJoinPresetArmorHand)
                        )
                )
                .then(CommandManager.literal("armor")
                        .then(CommandManager.literal("head")
                                .then(CommandManager.argument("modelName", StringArgumentType.string())
                                        .suggests(ARMOR_MODELS)
                                        .executes(ctx -> cmdJoinPresetSetArmor(ctx, "head"))
                                )
                        )
                        .then(CommandManager.literal("chestplate")
                                .then(CommandManager.argument("modelName", StringArgumentType.string())
                                        .suggests(ARMOR_MODELS)
                                        .executes(ctx -> cmdJoinPresetSetArmor(ctx, "chestplate"))
                                )
                        )
                        .then(CommandManager.literal("leggings")
                                .then(CommandManager.argument("modelName", StringArgumentType.string())
                                        .suggests(ARMOR_MODELS)
                                        .executes(ctx -> cmdJoinPresetSetArmor(ctx, "leggings"))
                                )
                        )
                        .then(CommandManager.literal("boots")
                                .then(CommandManager.argument("modelName", StringArgumentType.string())
                                        .suggests(ARMOR_MODELS)
                                        .executes(ctx -> cmdJoinPresetSetArmor(ctx, "boots"))
                                )
                        )
                        .then(CommandManager.literal("clear")
                                .executes(MorphCommands::cmdJoinPresetClearArmor)
                        )
                )
                .then(CommandManager.literal("extralayer")
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("modelName", StringArgumentType.string())
                                        .suggests(MODELS)
                                        .executes(MorphCommands::cmdJoinPresetExtraLayerAdd)
                                )
                        )
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("targetLayer", StringArgumentType.greedyString())
                                        .executes(MorphCommands::cmdJoinPresetExtraLayerRemove)
                                )
                        )
                        .then(CommandManager.literal("clear")
                                .executes(MorphCommands::cmdJoinPresetExtraLayerClear)
                        )
                        .then(CommandManager.literal("texture")
                                .then(CommandManager.argument("targetLayer", StringArgumentType.string())
                                        .then(CommandManager.argument("newTexture", StringArgumentType.greedyString())
                                                .suggests(TEXTURES)
                                                .executes(MorphCommands::cmdJoinPresetExtraLayerTexture)
                                        )
                                )
                        )
                );
    }


    private static int cmdJoinPresetClear(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
        mgr.getJoinPreset().clear();
        mgr.markDirty();
        ctx.getSource().sendMessage(Text.literal("§a[Morpher] JoinPreset limpiado correctamente."));
        return 1;
    }

    private static int cmdJoinPresetApplySelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            if (!mgr.getJoinPreset().hasPreset) {
                ctx.getSource().sendError(Text.literal("§c[Morpher] No hay ningún JoinPreset configurado."));
                return 0;
            }
            mgr.applyPresetTo(player);
            ctx.getSource().sendMessage(Text.literal("§a[Morpher] Te has aplicado el JoinPreset."));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetApplyPlayers(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            if (!mgr.getJoinPreset().hasPreset) {
                ctx.getSource().sendError(Text.literal("§c[Morpher] No hay ningún JoinPreset configurado."));
                return 0;
            }
            int count = 0;
            for (ServerPlayerEntity p : EntityArgumentType.getPlayers(ctx, "players")) {
                mgr.applyPresetTo(p);
                count++;
            }
            ctx.getSource().sendMessage(Text.literal("§a[Morpher] JoinPreset aplicado a " + count + " jugador(es)."));
            return count;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetSetOverlay(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String key = StringArgumentType.getString(ctx, "overlay");
            CharacterOverlay overlay = OverlayManager.getOverlay(key);
            if (overlay == null) {
                ctx.getSource().sendError(Text.literal("§c[Morpher] Overlay no encontrado: " + key));
                return 0;
            }
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().setFromOverlay(key, overlay);
            mgr.markDirty();
            ctx.getSource().sendMessage(Text.literal("§a[Morpher] JoinPreset configurado con overlay '" + key + "'."));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetSetModel(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String raw = StringArgumentType.getString(ctx, "modelName");
            String model = cleanGeoName(raw);
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().model = model;
            mgr.getJoinPreset().hasPreset = true;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetSetTexture(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String raw = cleanTextureName(StringArgumentType.getString(ctx, "textureName"));
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().texture = raw;
            mgr.getJoinPreset().hasPreset = true;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetSetAnim(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String raw = cleanAnimName(StringArgumentType.getString(ctx, "animName"));
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().animation = raw;
            mgr.getJoinPreset().hasPreset = true;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetSetLayer(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String raw = cleanGeoName(StringArgumentType.getString(ctx, "layerName"));
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().layerName = raw;
            mgr.getJoinPreset().hasPreset = true;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetLayerHand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().layerHand = enabled;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetLayerAnim(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ForceModelManager.JoinPresetData preset = ForceModelManager.get(ctx.getSource().getServer()).getJoinPreset();
        preset.layerName = cleanGeoName(StringArgumentType.getString(ctx, "layerName"));
        preset.layerAnimation = resolveEmpty(StringArgumentType.getString(ctx, "animName"));
        preset.hasPreset = true;
        ForceModelManager.get(ctx.getSource().getServer()).markDirty();
        return 1;
    }

    private static int cmdJoinPresetModelHand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ForceModelManager manager = ForceModelManager.get(ctx.getSource().getServer());
        manager.getJoinPreset().modelHand = BoolArgumentType.getBool(ctx, "enabled");
        manager.getJoinPreset().hasPreset = true;
        manager.markDirty();
        return 1;
    }

    private static int cmdJoinPresetHandSize(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ForceModelManager manager = ForceModelManager.get(ctx.getSource().getServer());
        Float value = parseHandSize(StringArgumentType.getString(ctx, "value"));
        if (value == null) return 0;
        manager.getJoinPreset().handSize = value;
        manager.getJoinPreset().hasPreset = true;
        manager.markDirty();
        return 1;
    }

    private static int cmdJoinPresetLayerHandToggle(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().layerHand = !mgr.getJoinPreset().layerHand;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetArmorHand(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().armorHand = enabled;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetArmorHandToggle(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().armorHand = !mgr.getJoinPreset().armorHand;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetSetArmor(CommandContext<ServerCommandSource> ctx, String slot) throws CommandSyntaxException {
        try {
            String raw = cleanGeoName(StringArgumentType.getString(ctx, "modelName"));
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            ForceModelManager.JoinPresetData p = mgr.getJoinPreset();
            switch (slot.toLowerCase()) {
                case "head" -> p.equipHead = raw;
                case "chestplate" -> p.equipChest = raw;
                case "leggins", "leggings" -> p.equipLegs = raw;
                case "boots" -> p.equipFeet = raw;
            }
            p.hasPreset = true;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetClearArmor(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            ForceModelManager.JoinPresetData p = mgr.getJoinPreset();
            p.equipHead = p.equipChest = p.equipLegs = p.equipFeet = "";
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetExtraLayerAdd(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String model = cleanGeoName(StringArgumentType.getString(ctx, "modelName"));
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().extraLayers.add(new MorphData.ExtraLayer(model, ""));
            mgr.getJoinPreset().hasPreset = true;
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetExtraLayerRemove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String target = cleanGeoName(StringArgumentType.getString(ctx, "targetLayer"));
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            for (int i = 0; i < mgr.getJoinPreset().extraLayers.size(); i++) {
                if (mgr.getJoinPreset().extraLayers.get(i).name.equalsIgnoreCase(target)) {
                    mgr.getJoinPreset().extraLayers.remove(i);
                    break;
                }
            }
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetExtraLayerClear(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            mgr.getJoinPreset().extraLayers.clear();
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdJoinPresetExtraLayerTexture(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            String target = cleanGeoName(StringArgumentType.getString(ctx, "targetLayer"));
            String tex = cleanTextureName(StringArgumentType.getString(ctx, "newTexture"));
            ForceModelManager mgr = ForceModelManager.get(ctx.getSource().getServer());
            for (MorphData.ExtraLayer el : mgr.getJoinPreset().extraLayers) {
                if (el.name.equalsIgnoreCase(target)) {
                    el.texture = tex;
                    break;
                }
            }
            mgr.markDirty();
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int cmdOverlayCreateSelf(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            String name = StringArgumentType.getString(ctx, "name");
            return createOverlayFromPlayer(ctx.getSource(), player, name);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("§c[Morpher] Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int createOverlayFromPlayer(ServerCommandSource src, ServerPlayerEntity player, String name) {
        MorphData.PlayerMorphState state = MorphData.get(player);
        if (state == null) {
            src.sendError(Text.literal("§c[Morpher] No se pudo obtener el estado de " + player.getName().getString()));
            return 0;
        }

        CharacterOverlay overlay = new CharacterOverlay();
        overlay.name = name;
        overlay.model = state.model.isEmpty() ? "player_wide" : state.model;
        overlay.texture = state.texture;
        overlay.animation = state.animation.isEmpty() ? "default" : state.animation;
        overlay.layer_hand = state.layerHand;
        overlay.armor_hand = state.armorHand;

        if (!state.layerName.isEmpty() && !state.layerName.equals("empty")) {
            overlay.layer = new CharacterOverlay.LayerDef();
            overlay.layer.enabled = state.layerEnabled;
            overlay.layer.model = state.layerName;
        }

        if (state.extraLayers != null && !state.extraLayers.isEmpty()) {
            overlay.extra_layers = new ArrayList<>();
            for (MorphData.ExtraLayer el : state.extraLayers) {
                overlay.extra_layers.add(new CharacterOverlay.ExtraLayerDef(el.name, el.texture));
            }
        }

        if (!state.equipHead.isEmpty() || !state.equipChest.isEmpty() || !state.equipLegs.isEmpty() || !state.equipFeet.isEmpty()) {
            overlay.equipment = new CharacterOverlay.Equipment();
            overlay.equipment.head = state.equipHead;
            overlay.equipment.chest = state.equipChest;
            overlay.equipment.legs = state.equipLegs;
            overlay.equipment.feet = state.equipFeet;
        }

        boolean ok = OverlayManager.saveOverlay(name, overlay);
        if (ok) {
            invalidateSuggestionCache();
            src.sendMessage(Text.literal("§aOverlay \"" + name + "\" creado correctamente"));
            return 1;
        } else {
            src.sendError(Text.literal("§c[Morpher] Falló al guardar el overlay '" + name + "'."));
            return 0;
        }
    }
}
