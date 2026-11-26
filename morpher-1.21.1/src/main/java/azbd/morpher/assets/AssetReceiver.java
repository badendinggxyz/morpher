package azbd.morpher.assets;

import azbd.morpher.MorpherMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.zip.CRC32;

public class AssetReceiver {
    private static final int MAX_ZIP_SIZE = 64 * 1024 * 1024;
    private static volatile long loadedChecksum = -1L;

    public record HashPayload(long checksum) implements CustomPayload {
        public static final CustomPayload.Id<HashPayload> ID = new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "asset_hash"));
        public static final PacketCodec<PacketByteBuf, HashPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeLong(value.checksum()), buf -> new HashPayload(buf.readLong()));
        @Override public CustomPayload.Id<HashPayload> getId() { return ID; }
    }

    public record SolicitarZipPayload() implements CustomPayload {
        public static final CustomPayload.Id<SolicitarZipPayload> ID = new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "request_assets"));
        public static final PacketCodec<PacketByteBuf, SolicitarZipPayload> CODEC = PacketCodec.of(
                (value, buf) -> {}, buf -> new SolicitarZipPayload());
        @Override public CustomPayload.Id<SolicitarZipPayload> getId() { return ID; }
    }

    public record InicioPayload(int tamañoTotal, long checksum) implements CustomPayload {
        public static final CustomPayload.Id<InicioPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "inicio_descarga"));
        public static final PacketCodec<PacketByteBuf, InicioPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> { buf.writeInt(value.tamañoTotal()); buf.writeLong(value.checksum()); },
                        buf -> new InicioPayload(buf.readInt(), buf.readLong())
                );
        @Override public CustomPayload.Id<InicioPayload> getId() { return ID; }
    }

    public record FragmentoPayload(int indice, byte[] datos) implements CustomPayload {
        public static final CustomPayload.Id<FragmentoPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "fragmento"));
        public static final PacketCodec<PacketByteBuf, FragmentoPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> { buf.writeInt(value.indice()); buf.writeInt(value.datos().length); buf.writeBytes(value.datos()); },
                        buf -> { int idx = buf.readInt(); int len = buf.readInt(); if (len < 0 || len > AssetServer.FRAGMENT_SIZE) throw new IllegalArgumentException("Invalid asset fragment length"); byte[] d = new byte[len]; buf.readBytes(d); return new FragmentoPayload(idx, d); }
                );
        @Override public CustomPayload.Id<FragmentoPayload> getId() { return ID; }
    }

    public record FinPayload(int totalFragmentos, long checksum) implements CustomPayload {
        public static final CustomPayload.Id<FinPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "fin_descarga"));
        public static final PacketCodec<PacketByteBuf, FinPayload> CODEC =
                PacketCodec.of(
                        (value, buf) -> { buf.writeInt(value.totalFragmentos()); buf.writeLong(value.checksum()); },
                        buf -> new FinPayload(buf.readInt(), buf.readLong())
                );
        @Override public CustomPayload.Id<FinPayload> getId() { return ID; }
    }

    public record DescargaCompletaPayload() implements CustomPayload {
        public static final CustomPayload.Id<DescargaCompletaPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MorpherMod.MOD_ID, "descarga_completa"));
        public static final PacketCodec<PacketByteBuf, DescargaCompletaPayload> CODEC =
                PacketCodec.of((value, buf) -> {}, buf -> new DescargaCompletaPayload());
        @Override public CustomPayload.Id<DescargaCompletaPayload> getId() { return ID; }
    }

    private static final Object LOCK           = new Object();
    private static byte[]  buffer              = null;
    private static long    checksumEsperado    = 0L;
    private static boolean descargando         = false;

    public static void registerPayloads() {
        PayloadTypeRegistry.configurationS2C().register(InicioPayload.ID, InicioPayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(FragmentoPayload.ID, FragmentoPayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(FinPayload.ID, FinPayload.CODEC);
        PayloadTypeRegistry.configurationS2C().register(HashPayload.ID, HashPayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(DescargaCompletaPayload.ID, DescargaCompletaPayload.CODEC);
        PayloadTypeRegistry.configurationC2S().register(SolicitarZipPayload.ID, SolicitarZipPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(InicioPayload.ID,    InicioPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FragmentoPayload.ID, FragmentoPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FinPayload.ID,       FinPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HashPayload.ID, HashPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DescargaCompletaPayload.ID, DescargaCompletaPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SolicitarZipPayload.ID, SolicitarZipPayload.CODEC);
    }

    public static void register() {
        ClientConfigurationNetworking.registerGlobalReceiver(HashPayload.ID, (payload, ctx) -> handleHash(payload, true));
        ClientConfigurationNetworking.registerGlobalReceiver(InicioPayload.ID, (payload, ctx) -> handleInicio(payload));
        ClientConfigurationNetworking.registerGlobalReceiver(FragmentoPayload.ID, (payload, ctx) -> handleFragmento(payload));
        ClientConfigurationNetworking.registerGlobalReceiver(FinPayload.ID, (payload, ctx) -> handleFin(payload, ctx.client(), true));
        ClientPlayNetworking.registerGlobalReceiver(InicioPayload.ID,    (payload, ctx) -> handleInicio(payload));
        ClientPlayNetworking.registerGlobalReceiver(FragmentoPayload.ID, (payload, ctx) -> handleFragmento(payload));
        ClientPlayNetworking.registerGlobalReceiver(FinPayload.ID,       (payload, ctx) -> handleFin(payload, ctx.client(), false));
        ClientPlayNetworking.registerGlobalReceiver(HashPayload.ID, (payload, ctx) -> handleHash(payload, false));
    }

    private static void handleHash(HashPayload payload, boolean configurationPhase) {
        if (loadedChecksum == payload.checksum()) {
            confirmar(MinecraftClient.getInstance(), configurationPhase);
        } else if (configurationPhase) {
            ClientConfigurationNetworking.send(new SolicitarZipPayload());
        } else {
            ClientPlayNetworking.send(new SolicitarZipPayload());
        }
    }

    private static void handleInicio(InicioPayload payload) {
        synchronized (LOCK) {
            int total = payload.tamañoTotal();
            if (total <= 0 || total > MAX_ZIP_SIZE) {
                limpiarEstado();
                MorpherMod.LOGGER.warn("[Morpher] Rejected asset archive size {}", total);
                return;
            }
            buffer              = new byte[total];
            checksumEsperado    = payload.checksum();
            descargando         = true;
        }
    }

    private static void handleFragmento(FragmentoPayload payload) {
        synchronized (LOCK) {
            if (!descargando || buffer == null) return;
            int expectedFragments = (buffer.length + AssetServer.FRAGMENT_SIZE - 1) / AssetServer.FRAGMENT_SIZE;
            if (payload.indice() < 0 || payload.indice() >= expectedFragments) return;
            long offsetLong = (long) payload.indice() * AssetServer.FRAGMENT_SIZE;
            int expectedLength = (int) Math.min(AssetServer.FRAGMENT_SIZE, buffer.length - offsetLong);
            if (payload.datos().length == expectedLength && offsetLong + payload.datos().length <= buffer.length) {
                int offset = (int) offsetLong;
                System.arraycopy(payload.datos(), 0, buffer, offset, payload.datos().length);
            }
        }
    }

    private static void handleFin(FinPayload payload, MinecraftClient client, boolean configurationPhase) {
        byte[] zipData;
        long acceptedChecksum = payload.checksum();
        synchronized (LOCK) {
            if (!descargando || buffer == null) {
                confirmar(client, configurationPhase);
                return;
            }

            CRC32 crc = new CRC32();
            crc.update(buffer);
            long checksumCalculado = crc.getValue();
            int expectedFragments = (buffer.length + AssetServer.FRAGMENT_SIZE - 1) / AssetServer.FRAGMENT_SIZE;

            if (payload.totalFragmentos() != expectedFragments || checksumCalculado != payload.checksum() || checksumCalculado != checksumEsperado) {
                MorpherMod.LOGGER.error("[Morpher] Checksum mismatch (calc={}, expected={})", checksumCalculado, checksumEsperado);
                limpiarEstado();
                confirmar(client, configurationPhase);
                return;
            }

            zipData = buffer;
            limpiarEstado();
        }

        try {
            AssetLoader.clearResources();
            loadedChecksum = -1L;
            AssetLoader.loadFromZip(zipData);
            client.execute(() -> {
                try {
                    azbd.morpher.morph.MorphCache.clearAll();
                    client.reloadResources().whenComplete((ok, error) -> client.execute(() -> {
                        try {
                            if (error != null) MorpherMod.LOGGER.error("[Morpher] Resource reload failed after asset download", error);
                            else loadedChecksum = acceptedChecksum;
                            azbd.morpher.morph.MorphCache.clearAll();
                        } finally {
                            confirmar(client, configurationPhase);
                        }
                    }));
                } catch (Throwable error) {
                    MorpherMod.LOGGER.error("[Morpher] Could not start resource reload", error);
                    confirmar(client, configurationPhase);
                }
            });
        } catch (Exception e) {
            MorpherMod.LOGGER.error("[Morpher] Error loading assets from zip", e);
            confirmar(client, configurationPhase);
        }
    }

    private static void confirmar(MinecraftClient client, boolean configurationPhase) {
        try {
            if (configurationPhase) ClientConfigurationNetworking.send(new DescargaCompletaPayload());
            else ClientPlayNetworking.send(new DescargaCompletaPayload());
        } catch (Exception e) {
            MorpherMod.LOGGER.warn("[Morpher] Could not confirm asset download.", e);
        }
    }

    public static void limpiarEstado() {
        synchronized (LOCK) {
            buffer              = null;
            checksumEsperado    = 0L;
            descargando         = false;
        }
    }

}
