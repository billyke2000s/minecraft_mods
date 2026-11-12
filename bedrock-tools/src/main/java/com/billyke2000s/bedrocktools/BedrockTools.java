package com.billyke2000s.bedrocktools;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-side only. The tools are netherite items with data components, defined by the recipes and
 * loot table in this jar, so players need nothing installed. Their bedrock look comes from a small
 * resource pack sent on join (alongside any other server packs).
 */
@Mod(BedrockTools.MODID)
public final class BedrockTools {
    public static final String MODID = "bedrocktools";
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes(("bedrocktools:" + PackInfo.SHA1).getBytes());

    public BedrockTools() {
        NeoForge.EVENT_BUS.addListener(BedrockTools::onLogin);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.connection.send(new ClientboundResourcePackPushPacket(PACK_ID, PackInfo.URL, PackInfo.SHA1, false,
                    Optional.of(Component.literal("Bedrock tools textures"))));
        }
    }
}
