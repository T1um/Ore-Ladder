package com.oreladder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public class OreLadderClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(FpsPayload.ID,
                (payload, context) -> ClientState.fpsCap = payload.fps());

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(LangPayload.ID)) {
                String code = MinecraftClient.getInstance().options.language;
                ClientPlayNetworking.send(new LangPayload(code != null && code.toLowerCase().startsWith("ru") ? "ru" : "en"));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.fpsCap = 0);
    }
}
