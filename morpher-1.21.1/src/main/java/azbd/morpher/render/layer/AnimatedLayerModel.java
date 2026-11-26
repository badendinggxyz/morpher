package azbd.morpher.render.layer;

import azbd.morpher.util.ResourceUtils;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;

public class AnimatedLayerModel extends GeoModel<AnimatedLayerAnimatable> {

    private String layerName = "empty";
    private String textureName = "";

    public void setLayerName(String name) {
        this.layerName = (name == null || name.isEmpty()) ? "empty" : ResourceUtils.cleanModelPath(name);
    }

    public void setTextureName(String name) {
        this.textureName = (name == null) ? "" : ResourceUtils.cleanTexturePath(name);
    }

    @Override
    public Identifier getModelResource(AnimatedLayerAnimatable animatable) {
        String clean = ResourceUtils.cleanModelPath(layerName);
        Identifier id = ResourceUtils.safeIdentifier("morpher", "geo/" + clean + ".geo.json");
        return ResourceUtils.isLoaded(id) ? id : Identifier.of("morpher", "geo/player_wide.geo.json");
    }

    @Override
    public Identifier getTextureResource(AnimatedLayerAnimatable animatable) {
        String tex = (!textureName.isEmpty() && !"empty".equalsIgnoreCase(textureName)) ? textureName : layerName;
        String clean = ResourceUtils.cleanTexturePath(tex);
        Identifier id = ResourceUtils.safeIdentifier("morpher", "textures/" + clean + ".png");
        if (ResourceUtils.isLoaded(id)) return id;
        Identifier fallback = ResourceUtils.safeIdentifier("morpher", "textures/" + ResourceUtils.cleanTexturePath(layerName) + ".png");
        return ResourceUtils.isLoaded(fallback) ? fallback : Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    }

    @Override
    public Identifier getAnimationResource(AnimatedLayerAnimatable animatable) {
        String clean = ResourceUtils.cleanAnimationPath(layerName);
        Identifier id = ResourceUtils.safeIdentifier("morpher", "animations/" + clean + ".animation.json");
        return ResourceUtils.isLoaded(id) ? id : Identifier.of("morpher", "animations/default.animation.json");
    }

    @Override
    public BakedGeoModel getBakedModel(Identifier location) {
        try {
            if (location != null && ResourceUtils.isLoaded(location)) {
                BakedGeoModel baked = super.getBakedModel(location);
                if (baked != null) return baked;
            }
        } catch (Throwable t) {
        }
        return super.getBakedModel(Identifier.of("morpher", "geo/player_wide.geo.json"));
    }
}
