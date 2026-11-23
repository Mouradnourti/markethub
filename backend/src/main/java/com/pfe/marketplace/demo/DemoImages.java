package com.pfe.marketplace.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Generates offline-friendly SVG product visuals so the demo never depends on external image hosts. */
final class DemoImages {

    private DemoImages() {
    }

    static String svg(String name, String category, String baseColor, int variant) {
        String dark = shade(baseColor, 0.55);
        String light = shade(baseColor, 1.25);
        String from = variant == 0 ? light : dark;
        String to = variant == 0 ? dark : baseColor;
        List<String> lines = wrap(name, 22);
        StringBuilder text = new StringBuilder();
        int y = 455 - (lines.size() - 1) * 21;
        for (String line : lines) {
            text.append("<text x=\"300\" y=\"").append(y).append("\" font-size=\"34\" font-weight=\"600\" fill=\"#fff\" ")
                    .append("text-anchor=\"middle\">").append(escape(line)).append("</text>");
            y += 42;
        }
        String shape = variant == 0
                ? "<circle cx=\"300\" cy=\"235\" r=\"118\" fill=\"#fff\" fill-opacity=\"0.16\"/>"
                : "<rect x=\"185\" y=\"120\" width=\"230\" height=\"230\" rx=\"46\" fill=\"#fff\" fill-opacity=\"0.16\" transform=\"rotate(12 300 235)\"/>";
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600" \
                font-family="Segoe UI, Roboto, Helvetica, Arial, sans-serif">
                <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">\
                <stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></linearGradient></defs>
                <rect width="600" height="600" fill="url(#g)"/>
                <circle cx="520" cy="70" r="170" fill="#fff" fill-opacity="0.07"/>
                <circle cx="60" cy="560" r="140" fill="#000" fill-opacity="0.06"/>
                %s
                <text x="300" y="235" font-size="104" font-weight="700" fill="#fff" text-anchor="middle" \
                dominant-baseline="central">%s</text>
                <text x="40" y="64" font-size="20" letter-spacing="4" fill="#fff" fill-opacity="0.8">%s</text>
                %s
                </svg>
                """.formatted(from, to, shape, escape(initials(name)), escape(category.toUpperCase(Locale.ROOT)), text);
    }

    private static String initials(String name) {
        StringBuilder initials = new StringBuilder();
        for (String word : name.split("[\\s-]+")) {
            if (!word.isEmpty() && Character.isLetter(word.charAt(0)) && initials.length() < 2) {
                initials.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return initials.isEmpty() ? "MH" : initials.toString();
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        lines.add(line.toString());
        return lines.size() > 3 ? lines.subList(0, 3) : lines;
    }

    private static String shade(String hex, double factor) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return "#%02x%02x%02x".formatted(channel(r, factor), channel(g, factor), channel(b, factor));
    }

    private static int channel(int value, double factor) {
        return (int) Math.clamp(Math.round(factor > 1 ? value + (255 - value) * (factor - 1) : value * factor), 0, 255);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
