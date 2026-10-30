package com.oreladder;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FpsPayload(int fps) implements CustomPayload {
    public static final CustomPayload.Id<FpsPayload> ID =
            new CustomPayload.Id<>(Identifier.of(OreLadder.MOD_ID, "fps"));
    public static final PacketCodec<RegistryByteBuf, FpsPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, FpsPayload::fps, FpsPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
