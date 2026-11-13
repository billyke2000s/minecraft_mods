package com.billyke2000s.animatedmotd;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Animated server-list description.
 *
 * The server list only asks a server for its description when it loads or you press Refresh, so the
 * "animation" is a frame chosen from the clock at that moment: each refresh shows a different frame
 * (the shimmer moves along the title and the bottom line rotates through the messages).
 *
 * Edit config/animatedmotd.txt (written on first start) and restart to change it. Colour codes use §.
 */
@Mod(AnimatedMotd.MODID)
public final class AnimatedMotd {
    public static final String MODID = "animatedmotd";
    private static final Logger LOG = LogUtils.getLogger();

    private static final String DEFAULT_CONFIG = """
            # Animated MOTD - edit, then restart the server. Colour codes use the § sign.
            # How long each frame lasts, in milliseconds.
            interval=600
            # The word the shimmer sweeps across, and its normal and shimmer colours.
            title=SURVIVAL
            titleColour=§e§l
            shimmerColour=§f§l
            edgeColour=§6§l
            # Text before and after the title on the top line.
            before=§6§l⚔
            after= §6§l⚔ §8» §dModded 1.21.1
            # How many frames each bottom-line message stays up.
            messageFrames=8
            # Bottom-line messages, one per line, shown in turn.
            message=§aBuild, explore, survive §8• §bAdventure awaits!
            message=§7Crack a §8§lWhip §8• §7Fire a §6Flintlock
            message=§bmc.obskyra.stream §8• §aBring your mates
            message=§dModded 1.21.1 §8• §eBeta-style worlds
            """;

    private static long interval = 600;
    private static String title = "SURVIVAL";
    private static String titleColour = "§e§l";
    private static String shimmerColour = "§f§l";
    private static String edgeColour = "§6§l";
    private static String before = "§6§l⚔ ";
    private static String after = " §6§l⚔ §8» §dModded 1.21.1";
    private static int messageFrames = 8;
    private static final List<String> MESSAGES = new ArrayList<>();

    public AnimatedMotd() {
        load(FMLPaths.CONFIGDIR.get().resolve("animatedmotd.txt"));
    }

    private static void load(Path file) {
        try {
            if (!Files.exists(file)) {
                Files.writeString(file, DEFAULT_CONFIG, StandardCharsets.UTF_8);
            }
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (raw.isBlank() || raw.startsWith("#") || !raw.contains("=")) {
                    continue;
                }
                String key = raw.substring(0, raw.indexOf('=')).trim();
                String value = raw.substring(raw.indexOf('=') + 1);
                switch (key) {
                    case "interval" -> interval = Math.max(100, Long.parseLong(value.trim()));
                    case "title" -> title = value.trim();
                    case "titleColour" -> titleColour = value.trim();
                    case "shimmerColour" -> shimmerColour = value.trim();
                    case "edgeColour" -> edgeColour = value.trim();
                    case "before" -> before = value;
                    case "after" -> after = value;
                    case "messageFrames" -> messageFrames = Math.max(1, Integer.parseInt(value.trim()));
                    case "message" -> MESSAGES.add(value);
                    default -> { }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("[Animated MOTD] Could not read {}, using defaults", file, e);
        }
        if (MESSAGES.isEmpty()) {
            MESSAGES.add("§aBuild, explore, survive §8• §bAdventure awaits!");
        }
    }

    /** The description for this moment. */
    public static Component currentFrame() {
        long frame = System.currentTimeMillis() / interval;
        return Component.literal(topLine(frame) + "\n" + MESSAGES.get((int) ((frame / messageFrames) % MESSAGES.size())));
    }

    /** The title with a bright shimmer sweeping across it, then a short pause before it sweeps again. */
    private static String topLine(long frame) {
        int letters = title.length();
        int cycle = letters + 4;
        int pos = (int) (frame % cycle);
        StringBuilder sb = new StringBuilder(before);
        for (int i = 0; i < letters; i++) {
            String colour = i == pos ? shimmerColour : Math.abs(i - pos) == 1 ? edgeColour : titleColour;
            sb.append(colour).append(title.charAt(i));
        }
        sb.append(after);
        return sb.toString();
    }
}
