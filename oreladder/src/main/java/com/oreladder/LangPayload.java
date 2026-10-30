package com.oreladder;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент → сервер: язык игры игрока ("ru" / "en"). */
public record LangPayload(String lang) implements CustomPayload {
    public static final CustomPayload.Id<LangPayload> ID =
            new CustomPayload.Id<>(Identifier.of(OreLadder.MOD_ID, "lang"));
    public static final PacketCodec<RegistryByteBuf, LangPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, LangPayload::lang, LangPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
