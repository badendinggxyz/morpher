package azbd.morpher.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class ResourceUtils {
    private static final Map<Identifier, Boolean> LOADED_CACHE = new ConcurrentHashMap<>();

    private ResourceUtils() {}

    public static String sanitizeSegment(String input, boolean isPath) {
        if (input == null) return "";
        String s = input.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isPath) {
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/') {
                    sb.append(c);
                } else {
                    sb.append('_');
                }
            } else {
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                    sb.append(c);
                } else {
                    sb.append('_');
                }
            }
        }
        String result = sb.toString();
        if (isPath) {
            while (result.contains("//")) {
                result = result.replace("//", "/");
            }
            if (result.startsWith("/")) {
                result = result.substring(1);
            }
        }
        return result;
    }

    public static Identifier safeIdentifier(String namespace, String path) {
        String cleanNamespace = sanitizeSegment(namespace == null || namespace.isBlank() ? "morpher" : namespace, false);
        if (cleanNamespace.isEmpty()) cleanNamespace = "morpher";
        String cleanPath = sanitizeSegment(path, true);
        if (cleanPath.isEmpty()) cleanPath = "empty";

        Identifier parsed = Identifier.tryParse(cleanNamespace + ":" + cleanPath);
        if (parsed != null) {
            return parsed;
        }
        return Identifier.of("morpher", "geo/player_wide.geo.json");
    }

    public static Identifier safeIdentifier(String combined) {
        if (combined == null || combined.isBlank()) {
            return Identifier.of("morpher", "geo/player_wide.geo.json");
        }
        String s = combined.trim();
        int colon = s.indexOf(':');
        if (colon >= 0) {
            String ns = s.substring(0, colon);
            String path = s.substring(colon + 1);
            return safeIdentifier(ns, path);
        } else {
            return safeIdentifier("morpher", s);
        }
    }

    public static String cleanModelPath(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (s.startsWith("geo/")) {
            s = s.substring("geo/".length());
        }
        if (s.endsWith(".geo.json")) {
            s = s.substring(0, s.length() - ".geo.json".length());
        }
        return sanitizeSegment(s, true);
    }

    public static String cleanTexturePath(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (s.startsWith("textures/")) {
            s = s.substring("textures/".length());
        }
        if (s.endsWith(".png")) {
            s = s.substring(0, s.length() - ".png".length());
        }
        return sanitizeSegment(s, true);
    }

    public static String cleanAnimationPath(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        if (s.startsWith("animations/")) {
            s = s.substring("animations/".length());
        }
        if (s.endsWith(".animation.json")) {
            s = s.substring(0, s.length() - ".animation.json".length());
        }
        return sanitizeSegment(s, true);
    }

    public static Identifier formatGeoIdentifier(String input) {
        if (input == null || input.isBlank()) return safeIdentifier("morpher", "geo/player_wide.geo.json");
        String s = input.trim();
        String ns = "morpher";
        String path = s;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            ns = s.substring(0, colon);
            path = s.substring(colon + 1);
        }
        path = cleanModelPath(path);
        return safeIdentifier(ns, "geo/" + path + ".geo.json");
    }

    public static Identifier formatTextureIdentifier(String input) {
        if (input == null || input.isBlank()) return safeIdentifier("minecraft", "textures/entity/player/wide/steve.png");
        String s = input.trim();
        String ns = "morpher";
        String path = s;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            ns = s.substring(0, colon);
            path = s.substring(colon + 1);
        }
        path = cleanTexturePath(path);
        return safeIdentifier(ns, "textures/" + path + ".png");
    }

    public static Identifier formatAnimationIdentifier(String input) {
        if (input == null || input.isBlank()) return safeIdentifier("morpher", "animations/default.animation.json");
        String s = input.trim();
        String ns = "morpher";
        String path = s;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            ns = s.substring(0, colon);
            path = s.substring(colon + 1);
        }
        path = cleanAnimationPath(path);
        return safeIdentifier(ns, "animations/" + path + ".animation.json");
    }

    public static boolean isLoaded(Identifier id) {
        if (id == null) return false;
        Boolean cached = LOADED_CACHE.get(id);
        if (cached != null) return cached;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getResourceManager() == null) {
                return false;
            }
            boolean loaded = client.getResourceManager().getResource(id).isPresent();
            LOADED_CACHE.put(id, loaded);
            return loaded;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void invalidateLoadedCache() { LOADED_CACHE.clear(); }
}
