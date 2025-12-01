package me.lubomirstankov.gotcraftproxychat.paper.util;

import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

public final class PlaceholderSupport {
    private static final boolean PAPI_PRESENT;
    private static final java.lang.reflect.Method SET_PLACEHOLDERS_METHOD;

    static {
        boolean present = false;
        java.lang.reflect.Method method = null;
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            method = papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            present = true;
        } catch (Exception ignored) {
        }
        PAPI_PRESENT = present;
        SET_PLACEHOLDERS_METHOD = method;
    }

    private PlaceholderSupport() {
    }

    public static String apply(String input, Player player) {
        if (input == null) return null;
        if (!PAPI_PRESENT || player == null) return input;
        try {
            return (String) SET_PLACEHOLDERS_METHOD.invoke(null, player, input);
        } catch (Exception ignored) {
            return input;
        }
    }

    public static String apply(String input, OfflinePlayer offlinePlayer) {
        if (input == null) return null;
        if (!PAPI_PRESENT || offlinePlayer == null) return input;
        try {
            return (String) SET_PLACEHOLDERS_METHOD.invoke(null, offlinePlayer, input);
        } catch (Exception ignored) {
            return input;
        }
    }

    public static boolean isAvailable() {
        return PAPI_PRESENT;
    }
}

