package azbd.morpher.render.handlers;

import net.minecraft.entity.player.PlayerEntity;

public class VisibilityHandler {

    private static final float VISIBLE           = 1.0F;
    private static final float INVISIBLE_VISIBLE_TO_VIEWER = 0.15F;
    private static final float INVISIBLE_HIDDEN_FROM_VIEWER = 0.0F;

    public float calculateAlpha(PlayerEntity target, PlayerEntity viewer) {
        if (!target.isInvisible()) return VISIBLE;
        return viewer != null && !target.isInvisibleTo(viewer)
                ? INVISIBLE_VISIBLE_TO_VIEWER
                : INVISIBLE_HIDDEN_FROM_VIEWER;
    }

    public float getBodyAlpha(PlayerEntity target, PlayerEntity viewer) {
        return calculateAlpha(target, viewer);
    }

    public boolean shouldRenderArmor(PlayerEntity target) {
        return true;
    }

    public boolean shouldRenderLayer(PlayerEntity target) {
        return true;
    }
}
