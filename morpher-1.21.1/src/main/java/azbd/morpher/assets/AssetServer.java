package azbd.morpher.assets;

import azbd.morpher.MorpherMod;
import azbd.morpher.morph.MorphData;
import azbd.morpher.overlay.OverlayManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AssetServer {

    public static final int FRAGMENT_SIZE = 32768;

    private static volatile byte[] zipCache      = null;
    private static volatile long   checksumCache = 0L;
    private static boolean receiverRegistered = false;

    private static final Path DATA_DIR = Path.of("morpher");

    private static synchronized void ensureZip() throws IOException {
        if (zipCache == null) {
            byte[] built = buildZip();
            long csum = 0L;
            if (built.length > 0) {
                CRC32 crc = new CRC32();
                crc.update(built);
                csum = crc.getValue();
            }
            checksumCache = csum;
            zipCache = built;
        }
    }

    public static void sendToPlayer(ServerPlayerEntity player) {
        try {
            ensureDirectories();
            ensureZip();
            sendPayload(player, new AssetReceiver.HashPayload(checksumCache));

        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] Error sending assets to {}", player.getName().getString(), e);
        }
    }

    public static void sendToHandler(ServerConfigurationNetworkHandler handler) {
        try {
            ensureDirectories();
            ensureZip();
            ServerConfigurationNetworking.send(handler, new AssetReceiver.HashPayload(checksumCache));
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] Could not send configuration assets.", e);
        }
    }

    private static void sendZipToHandler(ServerConfigurationNetworkHandler handler) {
        try {
            ensureZip();
            sendConfigurationZip(handler, zipCache, checksumCache);
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] Could not send requested configuration assets.", e);
        }
    }

    private static void sendConfigurationZip(ServerConfigurationNetworkHandler handler, byte[] zip, long checksum) {
        if (zip == null) return;
        int totalFragments = (int) Math.ceil((double) zip.length / FRAGMENT_SIZE);
        ServerConfigurationNetworking.send(handler, new AssetReceiver.InicioPayload(zip.length, checksum));
        for (int i = 0; i < totalFragments; i++) {
            int offset = i * FRAGMENT_SIZE;
            int length = Math.min(FRAGMENT_SIZE, zip.length - offset);
            byte[] fragment = new byte[length];
            System.arraycopy(zip, offset, fragment, 0, length);
            ServerConfigurationNetworking.send(handler, new AssetReceiver.FragmentoPayload(i, fragment));
        }
        ServerConfigurationNetworking.send(handler, new AssetReceiver.FinPayload(totalFragments, checksum));
    }

    public static synchronized void invalidateCache() {
        zipCache      = null;
        checksumCache = 0L;
    }

    public static void initialize() {
        ensureDirectories();
        OverlayManager.initialize();

        if (receiverRegistered) return;
        receiverRegistered = true;

        ServerConfigurationNetworking.registerGlobalReceiver(AssetReceiver.SolicitarZipPayload.ID,
                (payload, context) -> context.server().execute(() -> sendZipToHandler(context.networkHandler())));
        ServerPlayNetworking.registerGlobalReceiver(AssetReceiver.SolicitarZipPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayerEntity player = context.player();
                    if (player != null && !player.isDisconnected()) sendZip(player, zipCache, checksumCache);
                }));

        ServerPlayNetworking.registerGlobalReceiver(
                AssetReceiver.DescargaCompletaPayload.ID,
                (payload, context) ->
                        context.server().execute(() -> {
                            ServerPlayerEntity player = context.player();
                            if (player != null && !player.isDisconnected()) {
                                MorphData.PlayerMorphState state = MorphData.get(player);
                                if (state != null) {
                                    state.sync(player);
                                    MorphData.syncAllPlayersInWorld(player);
                                    MorphData.refreshModel(player);
                                }
                            }
                        })
        );
    }

    private static void ensureDirectories() {
        try {
            Files.createDirectories(DATA_DIR);
            Files.createDirectories(DATA_DIR.resolve("textures"));
            Files.createDirectories(DATA_DIR.resolve("geo"));
            Files.createDirectories(DATA_DIR.resolve("animations"));
            Files.createDirectories(DATA_DIR.resolve("overlays"));
        } catch (IOException e) {
            MorpherMod.LOGGER.error("[Morpher] Failed to initialize directories", e);
        }
    }

    public static Path getDataPath() {
        return DATA_DIR;
    }

    private static byte[] buildZip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            addDir(zip, DATA_DIR.resolve("textures"),   "assets/morpher/textures");
            addDir(zip, DATA_DIR.resolve("geo"),        "assets/morpher/geo");
            addDir(zip, DATA_DIR.resolve("animations"), "assets/morpher/animations");
        }
        return out.toByteArray();
    }

    private static void addDir(ZipOutputStream zip, Path dir, String zipPrefix) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return;

        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String rel   = dir.relativize(file).toString().replace('\\', '/');
                    String entry = zipPrefix + "/" + rel;
                    zip.putNextEntry(new ZipEntry(entry));
                    Files.copy(file, zip);
                    zip.closeEntry();
                } catch (IOException e) {
                    MorpherMod.LOGGER.error("[Morpher] Failed to add file to ZIP: {}", file, e);
                }
            });
        }
    }

    private static void sendZip(ServerPlayerEntity player, byte[] data, long checksum) {
        int total     = data.length;
        int fragments = (int) Math.ceil((double) total / FRAGMENT_SIZE);

        sendPayload(player, new AssetReceiver.InicioPayload(total, checksum));

        for (int i = 0; i < fragments; i++) {
            int offset  = i * FRAGMENT_SIZE;
            int size    = Math.min(FRAGMENT_SIZE, total - offset);
            byte[] frag = new byte[size];
            System.arraycopy(data, offset, frag, 0, size);

            sendPayload(player, new AssetReceiver.FragmentoPayload(i, frag));
        }

        sendPayload(player, new AssetReceiver.FinPayload(fragments, checksum));
    }

    private static void sendPayload(ServerPlayerEntity player, net.minecraft.network.packet.CustomPayload payload) {
        if (player == null || player.isDisconnected()) return;
        try {
            ServerPlayNetworking.send(player, payload);
        } catch (Exception e) {
            MorpherMod.LOGGER.warn("[Morpher] Could not send asset payload to {}.", player.getName().getString());
        }
    }
}
