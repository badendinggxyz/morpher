package azbd.morpher;

import azbd.morpher.api.ArmorModelRegistry;
import azbd.morpher.assets.AssetLoader;
import azbd.morpher.assets.AssetReceiver;
import azbd.morpher.render.handlers.MorphMolang;
import azbd.morpher.morph.MorphCache;
import azbd.morpher.morph.MorphData;
import pers.solid.brrp.v1.fabric.api.RRPCallback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class MorpherClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AssetLoader.initialize();

        RRPCallback.BEFORE_VANILLA.register(packs -> {
            if (AssetLoader.isReady()) {
                packs.add(AssetLoader.getRuntimePack());
            }
        });

        MorphData.registerClientSync();
        AssetReceiver.register();

        MorphMolang.initialize();

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of("morpher", "client_resource_listener");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        azbd.morpher.util.ResourceUtils.invalidateLoadedCache();
                        azbd.morpher.morph.MorphEntity.clearAnimationCache();
                        azbd.morpher.model.ArmorModel.clearArmorTextureCache();
                        ArmorModelRegistry.reload();
                        MorphCache.clearAll();
                    }
                }
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MorphCache.clearAll();
            AssetReceiver.limpiarEstado();
        });
    }
}
