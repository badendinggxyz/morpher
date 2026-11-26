package azbd.morpher.overlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import azbd.morpher.MorpherMod;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class OverlayManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path DATA_DIR    = Path.of("morpher");
    private static final Path OVERLAYS_DIR = DATA_DIR.resolve("overlays");

    private static volatile Map<String, CharacterOverlay> overlays = new LinkedHashMap<>();

    public static void initialize() {
        ensureFolderStructure();
        ensureExampleOverlay();
        reload();
    }

    public static void ensureFolderStructure() {
        Path[] dirs = {
                DATA_DIR,
                DATA_DIR.resolve("geo"),
                DATA_DIR.resolve("textures"),
                DATA_DIR.resolve("animations"),
                OVERLAYS_DIR
        };

        for (Path dir : dirs) {
            if (!Files.exists(dir)) {
                try {
                    Files.createDirectories(dir);
                } catch (Exception e) {
                    MorpherMod.LOGGER.error("[Morpher] No se pudo crear la carpeta: {}", dir, e);
                }
            }
        }
    }

    private static void ensureExampleOverlay() {
        Path example = OVERLAYS_DIR.resolve("example.json");
        if (Files.exists(example)) {
            return;
        }

        CharacterOverlay overlay = new CharacterOverlay();
        overlay.name = "Example";
        overlay.model = "player_wide";
        overlay.texture = "";
        overlay.animation = "default";
        overlay.layer = new CharacterOverlay.LayerDef();
        overlay.layer.enabled = false;
        overlay.equipment = new CharacterOverlay.Equipment();

        try {
            Files.writeString(example, GSON.toJson(overlay), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] No se pudo crear el overlay de ejemplo en {}.", example, e);
        }
    }

    public static void reload() {
        if (!Files.exists(OVERLAYS_DIR)) {
            ensureFolderStructure();
            overlays = new LinkedHashMap<>();
            return;
        }

        Map<String, CharacterOverlay> newOverlays = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(OVERLAYS_DIR)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> loadFile(p, newOverlays));
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] No se pudieron recargar los overlays.", e);
        }
        overlays = newOverlays;
    }

    private static void loadFile(Path path, Map<String, CharacterOverlay> targetMap) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String relative = OVERLAYS_DIR.relativize(path)
                    .toString()
                    .replace('\\', '/')
                    .replaceAll("\\.json$", "");

            CharacterOverlay overlay = GSON.fromJson(reader, CharacterOverlay.class);
            if (overlay != null) {
                targetMap.put(relative, overlay);
            }
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] No se pudo cargar el overlay: {}", path, e);
        }
    }

    public static CharacterOverlay getOverlay(String name) {
        return overlays.get(name);
    }

    public static boolean hasOverlay(String name) {
        return overlays.containsKey(name);
    }

    public static Collection<String> getOverlayNames() {
        return Collections.unmodifiableSet(overlays.keySet());
    }

    public static Map<String, CharacterOverlay> getAll() {
        return Collections.unmodifiableMap(overlays);
    }

    public static Path getOverlaysDir() {
        return OVERLAYS_DIR;
    }

    public static Path getDataDir() {
        return DATA_DIR;
    }

    public static boolean saveOverlay(String name, CharacterOverlay overlay) {
        if (name == null || overlay == null) return false;
        ensureFolderStructure();

        String safeName = name.trim();
        while (safeName.startsWith("/") || safeName.startsWith("\\")) {
            safeName = safeName.substring(1).trim();
        }
        if (safeName.isEmpty()) return false;

        safeName = safeName.replaceAll("[^a-zA-Z0-9_/\\\\-]", "");
        while (safeName.startsWith("/") || safeName.startsWith("\\")) {
            safeName = safeName.substring(1).trim();
        }
        if (safeName.isEmpty()) return false;

        String fileName = safeName.endsWith(".json") ? safeName : safeName + ".json";
        Path baseDir = OVERLAYS_DIR.toAbsolutePath().normalize();
        Path file = baseDir.resolve(fileName).normalize();

        if (!file.startsWith(baseDir)) {
            MorpherMod.LOGGER.warn("[Morpher] Intento de guardar overlay fuera de OVERLAYS_DIR: {}", file);
            return false;
        }

        try {
            if (file.getParent() != null && !Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(overlay), StandardCharsets.UTF_8);
            reload();
            return true;
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] No se pudo guardar el overlay en {}", file, e);
            return false;
        }
    }
}
