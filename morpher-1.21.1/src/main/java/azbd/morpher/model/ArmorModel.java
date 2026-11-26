package azbd.morpher.model;

import azbd.morpher.morph.MorphEntity;
import azbd.morpher.util.ResourceUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ArmorModel extends GeoModel<MorphEntity> {
    private static final java.util.Map<String, Optional<Identifier>> ARMOR_TEXTURE_CACHE = new ConcurrentHashMap<>();

    private final ModelType type;
    private final EquipmentSlot slot;
    private String layerName         = "empty";
    private String customMaterialName = null;

    public ArmorModel(EquipmentSlot slot) {
        this.type = ModelType.ARMOR;
        this.slot = slot;
    }

    public static ArmorModel createElytra() { return new ArmorModel(ModelType.ELYTRA); }
    public static ArmorModel createLayer()  { return new ArmorModel(ModelType.LAYER);  }

    private ArmorModel(ModelType type) {
        this.type = type;
        this.slot = null;
    }

    public void setLayerName(String name) {
        if (type == ModelType.LAYER) this.layerName = ResourceUtils.cleanModelPath(name);
    }

    public void setCustomMaterial(String materialName) {
        if (type == ModelType.ARMOR) {
            this.customMaterialName = (materialName == null || materialName.isEmpty()) ? null : ResourceUtils.cleanTexturePath(materialName);
        }
    }

    @Override
    public Identifier getModelResource(MorphEntity entity) {
        return switch (type) {
            case ARMOR  -> getArmorModel();
            case ELYTRA -> Identifier.of("morpher", "geo/elytra.geo.json");
            case LAYER  -> ResourceUtils.safeIdentifier("morpher", "geo/" + ResourceUtils.cleanModelPath(layerName) + ".geo.json");
        };
    }

    @Override
    public Identifier getTextureResource(MorphEntity entity) {
        return switch (type) {
            case ARMOR  -> getArmorTexture(entity);
            case ELYTRA -> getElytraTexture(entity);
            case LAYER  -> ResourceUtils.safeIdentifier("morpher", "textures/" + ResourceUtils.cleanTexturePath(layerName) + ".png");
        };
    }

    @Override
    public Identifier getAnimationResource(MorphEntity entity) {
        return Identifier.of("morpher", "animations/default.animation.json");
    }

    private Identifier getArmorModel() {
        if (slot == null) return null;
        return switch (slot) {
            case HEAD  -> Identifier.of("morpher", "geo/head.geo.json");
            case CHEST -> Identifier.of("morpher", "geo/chest.geo.json");
            case LEGS  -> Identifier.of("morpher", "geo/legs.geo.json");
            case FEET  -> Identifier.of("morpher", "geo/feet.geo.json");
            default    -> null;
        };
    }

    private Identifier getArmorTexture(MorphEntity entity) {
        if (slot == null) return null;
        PlayerEntity player = entity.getCurrentEntity();
        if (player == null) return null;

        ItemStack armorStack = player.getEquippedStack(slot);
        return resolveArmorTexture(armorStack, slot, customMaterialName);
    }

    public static Identifier resolveArmorTexture(ItemStack armorStack, EquipmentSlot slot, String customMaterial) {
        if (armorStack == null || armorStack.isEmpty() || slot == null) return null;
        Identifier itemId = Registries.ITEM.getId(armorStack.getItem());
        NbtComponent customData = armorStack.get(DataComponentTypes.CUSTOM_DATA);
        String override = customData != null && customData.contains("ArmorTexture")
                ? customData.copyNbt().getString("ArmorTexture") : "";
        String cacheKey = itemId + "|" + slot + "|" + customMaterial + "|" + override;
        Optional<Identifier> cached = ARMOR_TEXTURE_CACHE.get(cacheKey);
        if (cached != null) return cached.orElse(null);
        Identifier resolved = resolveArmorTextureUncached(armorStack, slot, customMaterial);
        ARMOR_TEXTURE_CACHE.put(cacheKey, Optional.ofNullable(resolved));
        return resolved;
    }

    private static Identifier resolveArmorTextureUncached(ItemStack armorStack, EquipmentSlot slot, String customMaterial) {
        if (armorStack == null || armorStack.isEmpty() || slot == null) return null;

        String layerSuffix = (slot == EquipmentSlot.LEGS) ? "layer_2" : "layer_1";

        if (customMaterial != null && !customMaterial.isEmpty()) {
            return ResourceUtils.safeIdentifier("minecraft",
                    "textures/models/armor/" + customMaterial + "_" + layerSuffix + ".png");
        }

        Item item = armorStack.getItem();
        if (item == Items.TURTLE_HELMET) {
            return Identifier.of("minecraft", "textures/models/armor/turtle_layer_1.png");
        }

        if (!(item instanceof ArmorItem armorItem)) return null;

        Identifier itemId = Registries.ITEM.getId(item);
        String itemNamespace = itemId.getNamespace();
        String itemPath = itemId.getPath();

        NbtComponent customData = armorStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            if (customData.contains("ArmorTexture")) {
                String tex = customData.copyNbt().getString("ArmorTexture");
                if (!tex.isEmpty()) {
                    return ResourceUtils.safeIdentifier(tex);
                }
            }
        }

        String baseItemName = itemPath;
        for (String suffix : List.of("_helmet", "_chestplate", "_leggings", "_boots", "_armor")) {
            if (baseItemName.endsWith(suffix)) {
                baseItemName = baseItemName.substring(0, baseItemName.length() - suffix.length());
                break;
            }
        }

        if (!"minecraft".equals(itemNamespace)) {
            Identifier modCandidate1 = ResourceUtils.safeIdentifier(itemNamespace,
                    "textures/models/armor/" + baseItemName + "_" + layerSuffix + ".png");
            if (textureExists(modCandidate1)) return modCandidate1;

            Identifier modCandidate2 = ResourceUtils.safeIdentifier(itemNamespace,
                    "textures/models/armor/" + itemPath + "_" + layerSuffix + ".png");
            if (textureExists(modCandidate2)) return modCandidate2;

            String humanoidFolder = (slot == EquipmentSlot.LEGS) ? "humanoid_leggings" : "humanoid";
            Identifier modCandidate3 = ResourceUtils.safeIdentifier(itemNamespace,
                    "textures/entity/equipment/" + humanoidFolder + "/" + baseItemName + ".png");
            if (textureExists(modCandidate3)) return modCandidate3;
        }

        try {
            RegistryEntry<ArmorMaterial> materialEntry = armorItem.getMaterial();
            ArmorMaterial mat = materialEntry.value();
            List<ArmorMaterial.Layer> layers = mat.layers();
            if (layers != null && !layers.isEmpty()) {
                for (ArmorMaterial.Layer layer : layers) {
                    Identifier layerTex = layer.getTexture(slot == EquipmentSlot.LEGS);
                    if (layerTex != null) {
                        if (!"minecraft".equals(itemNamespace)) {
                            Identifier modMirrored = ResourceUtils.safeIdentifier(itemNamespace, layerTex.getPath());
                            if (textureExists(modMirrored)) return modMirrored;
                        }

                        if (textureExists(layerTex)) {
                            return layerTex;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            RegistryEntry<ArmorMaterial> materialEntry = armorItem.getMaterial();
            if (materialEntry.getKey().isPresent()) {
                Identifier matKeyId = materialEntry.getKey().get().getValue();
                String matNs = matKeyId.getNamespace();
                String matPath = matKeyId.getPath();

                Identifier matCandidate = ResourceUtils.safeIdentifier(matNs,
                    "textures/models/armor/" + matPath + "_" + layerSuffix + ".png");
                if (textureExists(matCandidate)) return matCandidate;

                if (!"minecraft".equals(itemNamespace)) {
                    Identifier matModCandidate = ResourceUtils.safeIdentifier(itemNamespace,
                            "textures/models/armor/" + matPath + "_" + layerSuffix + ".png");
                    if (textureExists(matModCandidate)) return matModCandidate;
                }
            }
        } catch (Throwable ignored) {
        }

        String vanillaMat = getVanillaMaterialName(armorItem);
        if (vanillaMat != null) {
            if (!"minecraft".equals(itemNamespace)) {
                Identifier modVanillaCandidate = ResourceUtils.safeIdentifier(itemNamespace,
                        "textures/models/armor/" + vanillaMat + "_" + layerSuffix + ".png");
                if (textureExists(modVanillaCandidate)) return modVanillaCandidate;
            }

            Identifier vanillaCandidate = Identifier.of("minecraft",
                    "textures/models/armor/" + vanillaMat + "_" + layerSuffix + ".png");
            if (textureExists(vanillaCandidate)) return vanillaCandidate;
        }

        try {
            Identifier soundId = armorItem.getMaterial().value().equipSound().value().getId();
            String soundPath = soundId.getPath();
            int lastUnderscore = soundPath.lastIndexOf('_');
            if (lastUnderscore >= 0) {
                String guessedName = soundPath.substring(lastUnderscore + 1);
                Identifier candidate = ResourceUtils.safeIdentifier(soundId.getNamespace(),
                        "textures/models/armor/" + guessedName + "_" + layerSuffix + ".png");
                if (textureExists(candidate)) return candidate;

                candidate = Identifier.of("minecraft",
                        "textures/models/armor/" + guessedName + "_" + layerSuffix + ".png");
                if (textureExists(candidate)) return candidate;
            }
        } catch (Throwable ignored) {}

        return Identifier.of("minecraft", "textures/models/armor/iron_" + layerSuffix + ".png");
    }

    private static String getVanillaMaterialName(ArmorItem armorItem) {
        try {
            String id = armorItem.getMaterial().getKey()
                    .map(k -> k.getValue().getPath())
                    .orElse("");

            if (!id.isEmpty()) {
                if (id.contains("leather")) return "leather";
                if (id.contains("chainmail") || id.contains("chain")) return "chainmail";
                if (id.contains("iron")) return "iron";
                if (id.contains("gold")) return "gold";
                if (id.contains("diamond")) return "diamond";
                if (id.contains("netherite")) return "netherite";
            }
        } catch (Throwable ignored) {}

        try {
            String key = armorItem.getMaterial().value().equipSound().value().getId().getPath();
            if (key.contains("leather"))   return "leather";
            if (key.contains("chain"))     return "chainmail";
            if (key.contains("iron"))      return "iron";
            if (key.contains("gold"))      return "gold";
            if (key.contains("diamond"))   return "diamond";
            if (key.contains("netherite")) return "netherite";
        } catch (Throwable ignored) {}

        return null;
    }

    private static boolean textureExists(Identifier id) {
        return ResourceUtils.isLoaded(id);
    }

    public static List<Identifier> resolveAllLayerTextures(ItemStack armorStack, EquipmentSlot slot) {
        if (armorStack == null || armorStack.isEmpty() || slot == null) return List.of();
        if (!(armorStack.getItem() instanceof ArmorItem armorItem)) return List.of();

        try {
            ArmorMaterial mat = armorItem.getMaterial().value();
            List<ArmorMaterial.Layer> layers = mat.layers();
            if (layers == null || layers.size() <= 1) return List.of();

            String itemNamespace = Registries.ITEM.getId(armorStack.getItem()).getNamespace();
            boolean isLegs = slot == EquipmentSlot.LEGS;
            List<Identifier> result = new ArrayList<>();

            for (ArmorMaterial.Layer layer : layers) {
                Identifier tex = layer.getTexture(isLegs);
                if (tex == null) continue;

                if (!"minecraft".equals(itemNamespace)) {
                    Identifier modTex = ResourceUtils.safeIdentifier(itemNamespace, tex.getPath());
                    if (textureExists(modTex)) {
                        result.add(modTex);
                        continue;
                    }
                }

                if (textureExists(tex)) {
                    result.add(tex);
                }
            }

            return result.size() > 1 ? result : List.of();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static void clearArmorTextureCache() { ARMOR_TEXTURE_CACHE.clear(); }

    private Identifier getElytraTexture(MorphEntity entity) {
        PlayerEntity player = entity.getCurrentEntity();
        if (player == null) {
            return Identifier.of("minecraft", "textures/entity/elytra.png");
        }

        ItemStack chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
        NbtComponent customData = chestStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null && customData.contains("ElytraTexture")) {
            String customTexture = customData.copyNbt().getString("ElytraTexture");
            if (!customTexture.isEmpty()) {
                Identifier parsed = ResourceUtils.safeIdentifier(customTexture);
                if (textureExists(parsed)) {
                    return parsed;
                }
            }
        }

        return Identifier.of("minecraft", "textures/entity/elytra.png");
    }
}
