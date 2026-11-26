package azbd.morpher.model;

import azbd.morpher.morph.MorphEntity;
import azbd.morpher.util.ResourceUtils;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class OverlayModel extends GeoModel<MorphEntity> {

    private String overlayName;

    public OverlayModel() {
        this.overlayName = null;
    }

    public void setOverlayName(String name) {
        if (name != null) {
            String trimmed = name.trim();
            this.overlayName = (!trimmed.isEmpty() && !"empty".equalsIgnoreCase(trimmed)) ? trimmed : null;
        } else {
            this.overlayName = null;
        }
    }

    public String getOverlayName() {
        return overlayName;
    }

    public boolean hasOverlay() {
        return overlayName != null;
    }

    @Override
    public Identifier getModelResource(MorphEntity entity) {
        if (overlayName == null) return null;
        if (overlayName.contains(":")) {
            return ResourceUtils.formatGeoIdentifier(overlayName);
        }
        String clean = ResourceUtils.cleanModelPath(overlayName);
        return ResourceUtils.safeIdentifier("morpher", "geo/" + clean + ".geo.json");
    }

    @Override
    public Identifier getTextureResource(MorphEntity entity) {
        if (overlayName == null) return null;
        if (overlayName.contains(":")) {
            return ResourceUtils.formatTextureIdentifier(overlayName);
        }
        String clean = ResourceUtils.cleanTexturePath(overlayName);
        return ResourceUtils.safeIdentifier("morpher", "textures/" + clean + ".png");
    }

    @Override
    public Identifier getAnimationResource(MorphEntity entity) {
        return Identifier.of("morpher", "animations/default.animation.json");
    }

    public boolean modelExists(MorphEntity entity) {
        if (overlayName == null) return false;
        try {
            Identifier modelId = getModelResource(entity);
            return ResourceUtils.isLoaded(modelId);
        } catch (Exception e) {
            return false;
        }
    }
}
