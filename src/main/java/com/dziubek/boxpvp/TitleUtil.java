package com.dziubek.boxpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Mały helper na tytuły ekranowe (Adventure API, natywnie dostępne w Paper - bez dodatkowej
 * zależności). Przyjmuje zwykłe teksty z kodami § tak jak reszta pluginu, żeby nie trzeba było
 * nigdzie indziej myśleć o Component.
 */
public class TitleUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private TitleUtil() {
    }

    public static void show(Player player, String titleText, String subtitleText) {
        Component title = LEGACY.deserialize(titleText);
        Component subtitle = subtitleText == null ? Component.empty() : LEGACY.deserialize(subtitleText);
        Title.Times times = Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(400));
        player.showTitle(Title.title(title, subtitle, times));
    }
}
