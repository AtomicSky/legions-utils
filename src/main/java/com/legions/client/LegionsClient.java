package com.legions.client;

import com.legions.client.config.LegionsConfig;
import com.legions.client.gui.LegionsClientScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegionsClient implements ClientModInitializer {
    public static final String MOD_ID = "legions_utils";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static LegionsConfig CONFIG;

    private static final boolean ATOMICS_CLIENT_LOADED = FabricLoader.getInstance().isModLoaded("atomics_client");
    private static KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {
        CONFIG = LegionsConfig.load().normalize();
        LegionsRatingBackendCache.preloadAll();

        if (!ATOMICS_CLIENT_LOADED) {
            KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
            openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.legions_client.open_config",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    category
            ));
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey != null && openConfigKey.wasPressed()) {
                client.setScreen(new LegionsClientScreen(client.currentScreen));
            }
            LegionsAdaptivePerformance.tick(client);
            LegionsWorldBorder.tick(client);
            LegionsPingController.tick(client);
            LegionsSpectateLock.tick(client);
        });

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, parameters, timestamp) -> {
            if (LegionsPingController.shouldBlockIncomingPingText(message)) {
                return false;
            }
            if (!LegionsPingController.shouldCleanReceivedPingText(message)) {
                return true;
            }

            LegionsPingController.receiveChatPing(message, sender);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(LegionsPingController.cleanReceivedPingText(message));
            }
            return false;
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, parameters, timestamp) ->
                LegionsPingController.receiveChatPing(message, sender)
        );
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                !LegionsPingController.shouldBlockIncomingPingText(message)
        );
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (!LegionsPingController.shouldCleanReceivedPingText(message)) {
                return message;
            }

            LegionsPingController.receiveChatPing(message, null);
            return LegionsPingController.cleanReceivedPingText(message);
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> LegionsHud.renderHud(context));
    }

    public static void saveConfig() {
        CONFIG.normalize().save(LegionsConfig.configPath());
    }

    public static boolean enabled(MinecraftClient client) {
        return CONFIG != null && CONFIG.enabled && LegionsFeatures.isLegionsServer(client);
    }

    public static boolean hudVisible(MinecraftClient client) {
        return client != null && client.options != null && !client.options.hudHidden;
    }

    public static float uiScaleFactor() {
        return CONFIG == null ? 1.0f : Math.max(50, Math.min(200, CONFIG.uiScale)) / 100.0f;
    }

    public static boolean ratingNametagsEnabled(MinecraftClient client) {
        return CONFIG != null
                && CONFIG.enabled
                && CONFIG.ratingNametagsEnabled
                && (CONFIG.ratingNametagsIgnoreServerList || LegionsFeatures.isLegionsServer(client));
    }

    public static void setEnabled(boolean enabled) {
        if (CONFIG == null || CONFIG.enabled == enabled) {
            return;
        }
        CONFIG.enabled = enabled;
        if (!enabled) {
            LegionsWorldBorder.reset();
            LegionsAdaptivePerformance.reset();
        }
    }

    public static void setCustomWorldBorderEnabled(boolean enabled) {
        if (CONFIG == null || CONFIG.customWorldBorderEnabled == enabled) {
            return;
        }
        CONFIG.customWorldBorderEnabled = enabled;
        LegionsWorldBorder.reset();
    }

    public static boolean isAtomicsClientLoaded() {
        return ATOMICS_CLIENT_LOADED;
    }
}
