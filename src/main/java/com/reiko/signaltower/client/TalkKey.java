package com.reiko.signaltower.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.item.WalkieTalkieItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * v0.64.0: the Talk key (V by default; Options → Controls → Key Binds, under Reiko's Redstone
 * Signal Tower). While a Walkie-Talkie is in either hand it opens the talk box.
 */
public final class TalkKey {
    public static final KeyMapping TALK = new KeyMapping("key." + SignalTower.MOD_ID + ".talk", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories." + SignalTower.MOD_ID);

    private TalkKey() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TALK);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (TALK.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) {
                continue;
            }
            InteractionHand hand = minecraft.player.getMainHandItem().getItem() instanceof WalkieTalkieItem ? InteractionHand.MAIN_HAND
                    : minecraft.player.getOffhandItem().getItem() instanceof WalkieTalkieItem ? InteractionHand.OFF_HAND : null;
            if (hand == null) {
                minecraft.player.displayClientMessage(Component.translatable("message." + SignalTower.MOD_ID + ".walkie_hold"), true);
            } else {
                minecraft.setScreen(new TalkScreen(hand));
            }
        }
    }
}
