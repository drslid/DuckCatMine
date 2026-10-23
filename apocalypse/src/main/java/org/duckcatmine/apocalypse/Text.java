package org.duckcatmine.apocalypse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public final class Text {
    private static final Component PREFIX = Component.text("☣ ", NamedTextColor.DARK_GREEN);

    private Text() {
    }

    public static void info(CommandSender to, String message) {
        send(to, message, NamedTextColor.GRAY);
    }

    public static void good(CommandSender to, String message) {
        send(to, message, NamedTextColor.GREEN);
    }

    public static void warn(CommandSender to, String message) {
        send(to, message, NamedTextColor.GOLD);
    }

    public static void error(CommandSender to, String message) {
        send(to, message, NamedTextColor.RED);
    }

    private static void send(CommandSender to, String message, NamedTextColor color) {
        to.sendMessage(PREFIX.append(Component.text(message, color)));
    }

    public static String position(double x, double y, double z) {
        return "X " + Math.round(x) + " Y " + Math.round(y) + " Z " + Math.round(z);
    }
}
