package azbd.morpher.api;

import azbd.morpher.MorpherMod;
import azbd.morpher.util.ResourceUtils;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmorModelRegistry {
    private static final Gson GSON = new Gson();
    private static final String RESOURCE_PATH = "morpher/armor_models";

    private static final Map<Identifier, Entry> CODE_ENTRIES = new ConcurrentHashMap<>();

    private static final Map<Identifier, Entry> JSON_ENTRIES = new ConcurrentHashMap<>();

    private static volatile boolean loaded = false;

    private ArmorModelRegistry() {
    }

    public static Optional<Entry> get(ItemStack stack) {
        return get(stack, null);
    }

    public static Optional<Entry> get(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        loadIfNeeded();
        Identifier itemId = Registries.ITEM.getId(stack.getItem());

        Entry codeEntry = CODE_ENTRIES.get(itemId);
        if (codeEntry != null) {
            return Optional.of(codeEntry);
        }

        return Optional.ofNullable(JSON_ENTRIES.get(itemId));
    }

    public static void register(Item item, Identifier model, Identifier texture, Identifier animation) {
        if (item == null || model == null || texture == null) return;
        register(Registries.ITEM.getId(item), model, texture, animation);
    }

    public static void register(Item item, Identifier model, Identifier texture) {
        register(item, model, texture, null);
    }

    public static void register(Identifier itemId, Identifier model, Identifier texture, Identifier animation) {
        if (itemId == null || model == null || texture == null) return;
        Identifier cleanId = ResourceUtils.safeIdentifier(itemId.getNamespace(), itemId.getPath());
        Identifier cleanModel = ResourceUtils.formatGeoIdentifier(model.toString());
        Identifier cleanTex = ResourceUtils.formatTextureIdentifier(texture.toString());
        Identifier cleanAnim = animation != null ? ResourceUtils.formatAnimationIdentifier(animation.toString()) : null;
        CODE_ENTRIES.put(cleanId, new Entry(cleanModel, cleanTex, cleanAnim));
    }

    public static void register(Identifier itemId, Identifier model, Identifier texture) {
        register(itemId, model, texture, null);
    }

    public static void register(String itemId, String model, String texture, String animation) {
        if (itemId == null || model == null || texture == null) return;
        Identifier id = ResourceUtils.safeIdentifier(itemId);
        Identifier m = ResourceUtils.formatGeoIdentifier(model);
        Identifier t = ResourceUtils.formatTextureIdentifier(texture);
        Identifier a = animation != null ? ResourceUtils.formatAnimationIdentifier(animation) : null;
        register(id, m, t, a);
    }

    public static void register(String itemId, String model, String texture) {
        register(itemId, model, texture, null);
    }

    public static void registerSet(String modId, String baseName, Item head, Item chest, Item legs, Item feet) {
        String cleanBase = ResourceUtils.cleanModelPath(baseName);
        String texPath = modId + ":textures/models/armor/" + cleanBase + ".png";

        if (head != null) {
            register(head, ResourceUtils.formatGeoIdentifier(modId + ":geo/" + cleanBase + "_head.geo.json"),
                    ResourceUtils.formatTextureIdentifier(texPath));
        }
        if (chest != null) {
            register(chest, ResourceUtils.formatGeoIdentifier(modId + ":geo/" + cleanBase + "_chest.geo.json"),
                    ResourceUtils.formatTextureIdentifier(texPath));
        }
        if (legs != null) {
            register(legs, ResourceUtils.formatGeoIdentifier(modId + ":geo/" + cleanBase + "_legs.geo.json"),
                    ResourceUtils.formatTextureIdentifier(texPath));
        }
        if (feet != null) {
            register(feet, ResourceUtils.formatGeoIdentifier(modId + ":geo/" + cleanBase + "_feet.geo.json"),
                    ResourceUtils.formatTextureIdentifier(texPath));
        }
    }

    public static void reload() {
        loaded = false;
        JSON_ENTRIES.clear();
        loadIfNeeded();
    }

    private static void loadIfNeeded() {
        if (loaded) {
            return;
        }

        synchronized (ArmorModelRegistry.class) {
            if (loaded) return;
            loaded = true;

            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null || client.getResourceManager() == null) {
                    loaded = false;
                    return;
                }
                ResourceManager resources = client.getResourceManager();
                Map<Identifier, Resource> files = resources.findResources(RESOURCE_PATH, id -> id.getPath().endsWith(".json"));

                for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
                    loadFile(file.getKey(), file.getValue());
                }

                if (!JSON_ENTRIES.isEmpty() || !CODE_ENTRIES.isEmpty()) {
                    MorpherMod.LOGGER.info("[Morpher] Se cargaron {} entradas JSON y {} entradas de código para armaduras custom.",
                            JSON_ENTRIES.size(), CODE_ENTRIES.size());
                }
            } catch (Exception e) {
                MorpherMod.LOGGER.error("[Morpher] Error al cargar modelos de armadura.", e);
            }
        }
    }

    private static void loadFile(Identifier fileId, Resource resource) {
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;

            JsonObject itemsObj = (root.has("items") && root.get("items").isJsonObject())
                    ? root.getAsJsonObject("items")
                    : root;

            for (Map.Entry<String, JsonElement> itemEntry : itemsObj.entrySet()) {
                if (!itemEntry.getValue().isJsonObject()) {
                    continue;
                }

                String rawKey = itemEntry.getKey();
                Identifier itemId = ResourceUtils.safeIdentifier(rawKey);
                if (itemId == null) {
                    MorpherMod.LOGGER.warn("[Morpher] ID de item inválido '{}' en {}.", rawKey, fileId);
                    continue;
                }

                JsonObject value = itemEntry.getValue().getAsJsonObject();
                Identifier model = readIdentifier(value, "geo", "model", "geometry");
                Identifier texture = readIdentifier(value, "texture", "tex");
                Identifier animation = readIdentifier(value, "animation", "anim", "animations");

                if (model == null || texture == null) {
                    MorpherMod.LOGGER.warn("[Morpher] La entrada de armadura '{}' en {} necesita geo/model y texture.", itemId, fileId);
                    continue;
                }

                JSON_ENTRIES.put(itemId, new Entry(model, texture, animation));
            }
        } catch (Exception exception) {
            MorpherMod.LOGGER.error("[Morpher] No se pudo cargar el archivo de modelos de armadura {}.", fileId, exception);
        }
    }

    private static Identifier readIdentifier(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                String val = object.get(key).getAsString();
                if (val != null && !val.isBlank()) {
                    val = val.trim();
                    if (key.contains("geo") || key.contains("model")) {
                        return ResourceUtils.formatGeoIdentifier(val);
                    } else if (key.contains("tex")) {
                        return ResourceUtils.formatTextureIdentifier(val);
                    } else if (key.contains("anim")) {
                        return ResourceUtils.formatAnimationIdentifier(val);
                    } else {
                        return ResourceUtils.safeIdentifier(val);
                    }
                }
            }
        }
        return null;
    }

    public record Entry(Identifier model, Identifier texture, Identifier animation) {
    }
}
