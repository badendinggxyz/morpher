package azbd.morpher.assets;

import azbd.morpher.MorpherMod;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayerConfigurationTask;

import java.util.function.Consumer;

public final class MorphConfigurationTask implements ServerPlayerConfigurationTask {

    public static final Key KEY = new Key(MorpherMod.MOD_ID + ":load_assets");
    private final ServerConfigurationNetworkHandler handler;

    public MorphConfigurationTask(ServerConfigurationNetworkHandler handler) {
        this.handler = handler;
    }

    @Override
    public void sendPacket(Consumer<Packet<?>> sender) {
        AssetServer.sendToHandler(handler);
        MorpherMod.scheduleTask(600, () -> {
            try {
                handler.completeTask(KEY);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public Key getKey() {
        return KEY;
    }
}
