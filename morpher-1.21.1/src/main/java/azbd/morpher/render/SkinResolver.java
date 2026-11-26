package azbd.morpher.render;

import azbd.morpher.morph.MorphData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class SkinResolver {

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Map<String, Identifier> cache = new ConcurrentHashMap<>();
    private static final Map<String, SkinTextures.Model> modelCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> pending = new ConcurrentHashMap<>();

    public static Identifier getSkin(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;

        String key = playerName.toLowerCase(Locale.ROOT);

        Identifier cached = cache.get(key);
        if (cached != null) {
            return cached.getPath().equals("empty") ? null : cached;
        }

        if (!VALID_NAME.matcher(playerName).matches()) {
            cache.put(key, Identifier.of("morpher", "empty"));
            return null;
        }

        if (pending.containsKey(key)) return null;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(playerName);
            if (entry != null) {
                SkinTextures tex = entry.getSkinTextures();
                if (tex != null && tex.texture() != null) {
                    cache.put(key, tex.texture());
                    if (tex.model() != null) {
                        modelCache.put(key, tex.model());
                    }
                    return tex.texture();
                }
            }
        }

        pending.put(key, true);
        Thread thread = new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(
                        "https://api.mojang.com/users/profiles/minecraft/" + playerName
                ).toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) {
                    cache.put(key, Identifier.of("morpher", "empty"));
                    pending.remove(key);
                    return;
                }

                JsonObject profileJson;
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                    profileJson = JsonParser.parseReader(reader).getAsJsonObject();
                }
                String uuidStr = profileJson.get("id").getAsString();

                HttpURLConnection conn2 = (HttpURLConnection) URI.create(
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr
                ).toURL().openConnection();
                conn2.setConnectTimeout(5000);
                conn2.setReadTimeout(5000);

                if (conn2.getResponseCode() != 200) {
                    cache.put(key, Identifier.of("morpher", "empty"));
                    pending.remove(key);
                    return;
                }

                JsonObject sessionJson;
                try (InputStreamReader reader = new InputStreamReader(conn2.getInputStream())) {
                    sessionJson = JsonParser.parseReader(reader).getAsJsonObject();
                }

                var props = sessionJson.getAsJsonArray("properties");
                for (var prop : props) {
                    JsonObject propObj = prop.getAsJsonObject();
                    if ("textures".equals(propObj.get("name").getAsString())) {
                        String value = propObj.get("value").getAsString();
                        UUID uuid = uuidFromUndashed(uuidStr);
                        GameProfile profile = new GameProfile(uuid, playerName);
                        profile.getProperties().put("textures", new Property("textures", value));

                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> {
                            PlayerSkinProvider skinProvider = client.getSkinProvider();
                            skinProvider.fetchSkinTextures(profile).thenAccept(skinTextures -> {
                                if (skinTextures != null && skinTextures.texture() != null) {
                                    cache.put(key, skinTextures.texture());
                                    if (skinTextures.model() != null) {
                                        modelCache.put(key, skinTextures.model());
                                    }
                                } else {
                                    cache.put(key, Identifier.of("morpher", "empty"));
                                }
                                pending.remove(key);
                            }).exceptionally(throwable -> {
                                cache.put(key, Identifier.of("morpher", "empty"));
                                pending.remove(key);
                                return null;
                            });
                        });
                        return;
                    }
                }

                cache.put(key, Identifier.of("morpher", "empty"));
                pending.remove(key);

            } catch (Exception e) {
                cache.put(key, Identifier.of("morpher", "empty"));
                pending.remove(key);
            }
        });
        thread.setName("SkinResolver-" + playerName);
        thread.setDaemon(true);
        thread.start();

        return null;
    }

    public static SkinTextures.Model getSkinModel(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        return modelCache.get(playerName.toLowerCase(Locale.ROOT));
    }

    public static String resolvePlayerModelPath(PlayerEntity player, MorphData.PlayerMorphState state) {
        if (state != null && state.model != null && !state.model.isEmpty()
                && !state.model.equalsIgnoreCase("player_wide")
                && !state.model.equalsIgnoreCase("player_slim")
                && !state.model.equalsIgnoreCase("default")
                && !state.model.equalsIgnoreCase("empty")) {
            return state.model;
        }

        if (state != null && state.skinPlayerName != null && !state.skinPlayerName.isEmpty()) {
            SkinTextures.Model model = getSkinModel(state.skinPlayerName);
            if (model == SkinTextures.Model.SLIM) {
                return "player_slim";
            } else if (model == SkinTextures.Model.WIDE) {
                return "player_wide";
            }
        }

        if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            SkinTextures textures = clientPlayer.getSkinTextures();
            if (textures != null && textures.model() == SkinTextures.Model.SLIM) {
                return "player_slim";
            }
        }

        return "player_wide";
    }

    public static void clearCache(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            cache.clear();
            modelCache.clear();
            pending.clear();
        } else {
            String key = playerName.toLowerCase(Locale.ROOT);
            cache.remove(key);
            modelCache.remove(key);
            pending.remove(key);
        }
    }

    private static UUID uuidFromUndashed(String undashed) {
        String dashed = undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"
        );
        return UUID.fromString(dashed);
    }
}
