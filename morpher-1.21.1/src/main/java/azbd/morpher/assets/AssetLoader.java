package azbd.morpher.assets;

import azbd.morpher.MorpherMod;
import pers.solid.brrp.v1.api.RuntimeResourcePack;
import net.minecraft.util.Identifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AssetLoader {
    private static final int MAX_ENTRY_SIZE = 32 * 1024 * 1024;

    private static RuntimeResourcePack runtimePack;
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) return;
        runtimePack = RuntimeResourcePack.create(Identifier.of(MorpherMod.MOD_ID, "runtime"));
        runtimePack.setAllowsDuplicateResource(true);
        initialized = true;
    }

    public static RuntimeResourcePack getRuntimePack() {
        return runtimePack;
    }

    public static void clearResources() {
        if (runtimePack == null) return;
        runtimePack.clearResources();
        runtimePack.setAllowsDuplicateResource(true);
    }

    public static void loadFromZip(byte[] zipData) throws IOException {
        if (runtimePack == null) {
            MorpherMod.LOGGER.error("[Morpher] Error: pack no inicializado");
            return;
        }

        Map<String, byte[]> files = extractZip(zipData);

        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String path = entry.getKey().replace('\\', '/');
            byte[] data = entry.getValue();

            if (!path.startsWith("assets/morpher/")) continue;
            path = path.substring("assets/".length());

            String[] parts = path.split("/", 2);
            if (parts.length < 2) continue;

            String namespace = parts[0].toLowerCase(java.util.Locale.ROOT).trim();
            String subPath   = parts[1].toLowerCase(java.util.Locale.ROOT).trim();
            if (!namespace.equals("morpher") || !(subPath.startsWith("geo/") || subPath.startsWith("textures/") || subPath.startsWith("animations/"))) continue;
            if (!(subPath.endsWith(".json") || subPath.endsWith(".png"))) continue;

            try {
                Identifier id = Identifier.of(namespace, subPath);
                runtimePack.addAsset(id, data);
            } catch (Exception e) {
                MorpherMod.LOGGER.warn("[Morpher] Invalid asset identifier path: {}/{} ({})", namespace, subPath, e.getMessage());
            }
        }
    }

    private static Map<String, byte[]> extractZip(byte[] zipData) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        long totalExtracted = 0L;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) { zip.closeEntry(); continue; }
                byte[] data = readAllBytes(zip);
                totalExtracted += data.length;
                if (totalExtracted > 64L * 1024 * 1024) throw new IOException("Asset archive expands beyond size limit");
                files.put(entry.getName(), data);
                zip.closeEntry();
            }
        }
        return files;
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(chunk, 0, chunk.length)) != -1) {
            if (buffer.size() + bytesRead > MAX_ENTRY_SIZE) throw new IOException("Asset entry exceeds size limit");
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    public static boolean isReady() {
        return initialized && runtimePack != null;
    }
}
