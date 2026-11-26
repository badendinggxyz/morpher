package azbd.morpher.overlay;

import java.util.ArrayList;
import java.util.List;

public class CharacterOverlay {

    public String name       = "";
    public String model      = "";
    public String texture    = "";
    public String animation  = "";
    public String emote_mode = "additive";
    public Boolean layer_hand = null;
    public Boolean layerhand  = null;
    public Boolean armor_hand = null;
    public Boolean armorhand  = null;
    public LayerDef layer    = null;

    public static class LayerDef {
        public boolean enabled = true;
        public String model = "";
        public String texture = "";
        public List<String> textures = new ArrayList<>();

        public String getTexture(int index) {
            if (index <= 0 || texture == null) return texture != null ? texture : "";
            int listIndex = index - 1;
            return textures == null || listIndex >= textures.size() ? texture : textures.get(listIndex);
        }

        public int textureCount() {
            return 1 + (textures != null ? textures.size() : 0);
        }
    }

    public List<ExtraLayerDef> extra_layers = null;
    public List<ExtraLayerDef> extralayers  = null;

    public static class ExtraLayerDef {
        public String model;
        public String name;
        public String texture = "";

        public ExtraLayerDef() {}

        public ExtraLayerDef(String model, String texture) {
            this.model = model;
            this.texture = texture;
        }

        public String getModelName() {
            if (model != null && !model.isEmpty()) return model;
            if (name != null && !name.isEmpty()) return name;
            return "";
        }
    }

    public Equipment equipment = null;

    public static class Equipment {
        public String head  = "";
        public String chest = "";
        public String legs  = "";
        public String feet  = "";
    }

    public String getDisplayName(String fallbackKey) {
        return (name != null && !name.isEmpty()) ? name : fallbackKey;
    }

    public boolean isForcedEmoteMode() {
        return "forced".equalsIgnoreCase(emote_mode);
    }
}
