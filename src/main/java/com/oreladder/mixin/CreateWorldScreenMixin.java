package com.oreladder.mixin;

import com.oreladder.ModeState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Добавляет на экран создания мира кнопку выбора сложности мода Ore Ladder. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    protected CreateWorldScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void oreladder$addModeButton(CallbackInfo ci) {
        ButtonWidget button = ButtonWidget.builder(oreladder$label(), b -> {
            ModeState.pending = ModeState.pending == 0 ? 1 : 0;
            b.setMessage(oreladder$label());
        }).dimensions(this.width - 226, 6, 220, 20).build();
        this.addDrawableChild(button);
    }

    private static Text oreladder$label() {
        String code = MinecraftClient.getInstance().options.language;
        boolean ru = code != null && code.toLowerCase().startsWith("ru");
        if (ModeState.pending == 1) {
            return Text.literal(ru ? "Ore Ladder: Очень Экстримальный!" : "Ore Ladder: VERY EXTREME!")
                    .formatted(Formatting.RED);
        }
        return Text.literal(ru ? "Ore Ladder: Нормальный" : "Ore Ladder: Normal").formatted(Formatting.GREEN);
    }
}
