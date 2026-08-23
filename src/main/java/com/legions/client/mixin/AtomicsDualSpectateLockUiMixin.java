package com.legions.client.mixin;

import com.legions.client.LegionsSpectateLock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "com.atomics.client.gui.AtomicsClientScreen")
public abstract class AtomicsDualSpectateLockUiMixin extends Screen {
    private static final int LEGIONS_LOCK_BUTTON_WIDTH = 36;
    private static final int LEGIONS_LOCK_BUTTON_GAP = 4;
    private static int lockedDualSpectateSliderRows;
    @Unique
    private final List<TextFieldWidget> legions_client$lockFields = new ArrayList<>();
    @Unique
    private final List<ButtonWidget> legions_client$lockButtons = new ArrayList<>();

    protected AtomicsDualSpectateLockUiMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void legions_client$clearDualSpectateLockButtons(CallbackInfo ci) {
        legions_client$lockFields.clear();
        legions_client$lockButtons.clear();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void legions_client$refreshDualSpectateLockButtons(DrawContext context, int mouseX, int mouseY, float delta,
                                                               CallbackInfo ci) {
        int count = Math.min(legions_client$lockFields.size(), legions_client$lockButtons.size());
        for (int i = 0; i < count; i++) {
            legions_client$lockButtons.get(i).setMessage(lockText(legions_client$lockFields.get(i).getText()));
        }
    }

    @Inject(method = "addTextField", at = @At("RETURN"), remap = false)
    private void legions_client$addDualSpectateLockButton(int x, int y, int width, String label, String value,
                                                          String placeholder, Consumer<String> setter,
                                                          CallbackInfoReturnable<TextFieldWidget> cir) {
        TextFieldWidget field = cir.getReturnValue();
        if (field == null || (!"Player One".equals(label) && !"Player Two".equals(label))) {
            return;
        }

        int fieldWidth = Math.max(80, width - LEGIONS_LOCK_BUTTON_WIDTH - LEGIONS_LOCK_BUTTON_GAP);
        field.setWidth(fieldWidth);
        ButtonWidget lockButton = ButtonWidget.builder(lockText(field.getText()), button -> {
            LegionsSpectateLock.toggleLockToPlayer(this.client, field.getText());
            button.setMessage(lockText(field.getText()));
        }).dimensions(x + fieldWidth + LEGIONS_LOCK_BUTTON_GAP, y, LEGIONS_LOCK_BUTTON_WIDTH, field.getHeight()).build();
        addDrawableChild(lockButton);
        legions_client$lockFields.add(field);
        legions_client$lockButtons.add(lockButton);
    }

    @Inject(method = "addDoubleSlider", at = @At("HEAD"), cancellable = true, remap = false)
    private void legions_client$hideLockedDualSpectateSliders(int x, int y, int width, String label,
                                                              double current, double min, double max, double step,
                                                              double defaultValue, @Coerce Object setter,
                                                              @Coerce Object formatter,
                                                              CallbackInfoReturnable<Object> cir) {
        Double forcedValue = lockedDualSpectateValue(label);
        if (forcedValue == null) {
            return;
        }

        callDoubleSetter(setter, forcedValue);
        cir.setReturnValue(null);
    }

    private static Double lockedDualSpectateValue(String label) {
        if ("Frame Padding".equals(label)) {
            lockedDualSpectateSliderRows = 3;
            return 2.5D;
        }
        if (lockedDualSpectateSliderRows <= 0) {
            return null;
        }

        Double value = switch (label) {
            case "Min Distance" -> 2.0D;
            case "Max Distance" -> 160.0D;
            case "Max Y Difference" -> 10.0D;
            default -> null;
        };
        if (value == null) {
            lockedDualSpectateSliderRows = 0;
        } else {
            lockedDualSpectateSliderRows--;
        }
        return value;
    }

    private static void callDoubleSetter(Object setter, double value) {
        if (setter == null) {
            return;
        }

        try {
            for (Method method : setter.getClass().getMethods()) {
                if (method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == double.class
                        && method.getReturnType() == void.class) {
                    method.invoke(setter, value);
                    return;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Text lockText(String playerName) {
        return Text.literal(LegionsSpectateLock.isLockedTo(playerName) ? "\uD83D\uDD12" : "\uD83D\uDD13");
    }
}
