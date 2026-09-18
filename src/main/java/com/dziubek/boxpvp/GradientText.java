package com.dziubek.boxpvp;

/**
 * Buduje tekst z gradientem kolorów przy użyciu natywnego, wanilijnego kodowania hex
 * Minecrafta (sekwencja §x§R§R§G§G§B§B na każdy znak) - rozumiane natywnie przez każdego
 * klienta 1.16+ i każdy pipeline tekstu w tym pluginie (Bukkit legacy String, DecentHolograms,
 * Adventure LegacyComponentSerializer, TextDisplay#setText). Wejściowy tekst MUSI być czystym
 * tekstem bez własnych kodów koloru (&amp; lub §) - ta klasa sama nadaje kolor każdemu znakowi.
 */
public final class GradientText {

    private GradientText() {
    }

    public static String apply(String text, int fromRgb, int toRgb) {
        return apply(text, fromRgb, toRgb, false);
    }

    public static String apply(String text, int fromRgb, int toRgb, boolean bold) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int length = text.length();
        int fr = (fromRgb >> 16) & 0xFF, fg = (fromRgb >> 8) & 0xFF, fb = fromRgb & 0xFF;
        int tr = (toRgb >> 16) & 0xFF, tg = (toRgb >> 8) & 0xFF, tb = toRgb & 0xFF;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                result.append(c);
                continue;
            }
            double ratio = length == 1 ? 0.0 : (double) i / (length - 1);
            int r = (int) Math.round(fr + (tr - fr) * ratio);
            int g = (int) Math.round(fg + (tg - fg) * ratio);
            int b = (int) Math.round(fb + (tb - fb) * ratio);

            result.append('§').append('x');
            appendHexPair(result, r);
            appendHexPair(result, g);
            appendHexPair(result, b);
            if (bold) {
                result.append("§l"); // kolor resetuje formatowanie, więc §l trzeba dać PO nim
            }
            result.append(c);
        }
        return result.toString();
    }

    private static void appendHexPair(StringBuilder sb, int channel) {
        String hex = String.format("%02X", channel);
        sb.append('§').append(hex.charAt(0)).append('§').append(hex.charAt(1));
    }
}
