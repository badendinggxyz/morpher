package azbd.morpher.morph;

import azbd.morpher.render.MorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MorphCache {

    private static final Map<UUID, MorphRenderer> renderers = new ConcurrentHashMap<>();

    public static MorphRenderer getOrCreateRenderer(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }

        return renderers.computeIfAbsent(playerUuid, uuid -> createRenderer());
    }

    public static void removeRenderer(UUID playerUuid) {
        if (playerUuid != null) {
            renderers.remove(playerUuid);
        }
    }

    public static MorphRenderer getRenderer(UUID playerUuid) {
        return playerUuid != null ? renderers.get(playerUuid) : null;
    }

    private static MorphRenderer createRenderer() {
        return azbd.morpher.render.RenderHelpers.createRenderer();
    }

    public static void clearAll() {
        renderers.clear();
    }
}
