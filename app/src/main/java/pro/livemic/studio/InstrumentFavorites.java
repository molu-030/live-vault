package pro.livemic.studio;

import android.content.SharedPreferences;
import java.util.*;

/** Bank-qualified favorites keep melodic program zero distinct from drum kit zero. */
final class InstrumentFavorites {
    static String key(int bank, int program) { return bank + ":" + program; }
    static Set<String> read(SharedPreferences preferences) {
        Set<String> favorites = new HashSet<>(preferences.getStringSet("instrumentFavorites", Collections.emptySet()));
        if (!preferences.getBoolean("instrumentFavoritesMigrated", false)) {
            for (String legacy : preferences.getStringSet("gmFavorites", Collections.emptySet())) {
                try {
                    int program = Integer.parseInt(legacy);
                    if (program >= 0 && program < 128) favorites.add(key(0, program));
                } catch (NumberFormatException ignored) {}
            }
            preferences.edit().putStringSet("instrumentFavorites", new HashSet<>(favorites))
                    .putBoolean("instrumentFavoritesMigrated", true).apply();
        }
        return favorites;
    }
    static void save(SharedPreferences preferences, Set<String> favorites) {
        preferences.edit().putStringSet("instrumentFavorites", new HashSet<>(favorites)).apply();
    }
}
