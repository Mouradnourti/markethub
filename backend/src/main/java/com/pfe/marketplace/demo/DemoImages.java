package com.pfe.marketplace.demo;

import java.util.Locale;

/**
 * Draws offline-friendly, studio-style SVG product illustrations so the demo never depends on external image
 * hosts. Each product type is a small flat-vector drawing in a 400 × 400 box, placed on a 600 × 600 backdrop.
 * Variant 0 is the hero shot; variant 1 is a closer, tilted shot on a different backdrop.
 */
final class DemoImages {

    private static final String SCREEN = "url(#screen)";
    private static final String GLASS = "#dbeafe";
    private static final String METAL = "#cbd5e1";
    private static final String INK = "#0f172a";

    private DemoImages() {
    }

    /** Colours derived from the product's main colour. */
    private record Pal(String c, String d, String l, String edge) {

        static Pal of(String color) {
            boolean light = luminance(color) > 0.72;
            return new Pal(color, shade(color, light ? 0.8 : 0.7), shade(color, 1.35),
                    light ? " stroke=\"" + shade(color, 0.72) + "\" stroke-width=\"3\"" : "");
        }
    }

    static String illustration(String kind, String color, String categoryColor, int variant) {
        Pal pal = Pal.of(color);
        String object = draw(kind, pal);
        String backdrop;
        String placement;
        if (variant == 0) {
            backdrop = """
                    <rect width="600" height="600" fill="url(#bg)"/>
                    <ellipse cx="300" cy="512" rx="190" ry="24" fill="#0f172a" opacity=".12"/>""";
            placement = "translate(60 46) scale(1.2)";
        } else {
            backdrop = """
                    <rect width="600" height="600" fill="url(#bg2)"/>
                    <circle cx="300" cy="300" r="236" fill="%s"/>
                    <ellipse cx="300" cy="520" rx="170" ry="20" fill="#0f172a" opacity=".10"/>"""
                    .formatted(mix(categoryColor, 0.78));
            placement = "translate(300 300) rotate(-7) scale(1.28) translate(-200 -205)";
        }
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600">
                <defs>
                <radialGradient id="bg" cx="50%%" cy="38%%" r="75%%"><stop offset="0" stop-color="%s"/>\
                <stop offset="1" stop-color="%s"/></radialGradient>
                <linearGradient id="bg2" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fafaf9"/>\
                <stop offset="1" stop-color="#e7e5e4"/></linearGradient>
                <linearGradient id="screen" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1e3a5f"/>\
                <stop offset=".55" stop-color="#0b1220"/><stop offset="1" stop-color="#111827"/></linearGradient>
                <linearGradient id="rgb" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#ec4899"/>\
                <stop offset=".35" stop-color="#8b5cf6"/><stop offset=".7" stop-color="#06b6d4"/>\
                <stop offset="1" stop-color="#22c55e"/></linearGradient>
                <linearGradient id="mirror" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f0f9ff"/>\
                <stop offset="1" stop-color="#bfdbfe"/></linearGradient>
                </defs>
                %s
                <g transform="%s">%s</g>
                </svg>
                """.formatted(mix(categoryColor, 0.93), mix(categoryColor, 0.80), backdrop, placement, object);
    }

    private static String draw(String kind, Pal p) {
        String c = p.c();
        String d = p.d();
        String l = p.l();
        String edge = p.edge();
        return switch (kind) {
            case "phone" -> phone(p, 120);
            case "tablet" -> r(85, 25, 230, 350, 26, c, edge) + r(98, 38, 204, 324, 14, SCREEN)
                    + c(200, 31, 3, "#334155") + glare(98, 38, 204, 324);
            case "screen-protector" -> phone(p, 100)
                    + r(140, 22, 168, 348, 30, "#e0f2fe", " opacity=\".55\" stroke=\"#fff\" stroke-width=\"3\"")
                    + path("M160 40 L210 40 L170 350 L150 350 Z", "#fff", " opacity=\".35\"");
            case "phone-case" -> r(120, 30, 160, 340, 34, c, edge) + r(138, 48, 70, 70, 18, d)
                    + c(160, 70, 12, INK) + c(186, 96, 12, INK) + c(186, 70, 5, "#475569")
                    + r(128, 44, 8, 312, 4, l, " opacity=\".45\"") + r(278, 110, 6, 50, 3, d);
            case "charger" -> r(163, 92, 14, 62, 4, METAL) + r(223, 92, 14, 62, 4, METAL)
                    + r(115, 140, 170, 175, 34, c, edge) + r(170, 270, 60, 16, 8, "#334155")
                    + r(130, 152, 16, 150, 8, "#fff", " opacity=\".35\"")
                    + stroke("M200 286 C 200 340, 300 330, 330 370", d, 10);
            case "wireless-charger" -> e(200, 262, 150, 50, d) + e(200, 246, 150, 50, c, edge)
                    + e(200, 246, 96, 30, "none", " stroke=\"" + l + "\" stroke-width=\"6\"")
                    + c(200, 288, 4, "#22c55e") + stroke("M348 252 C 390 270, 390 330, 340 360", d, 10);
            case "powerbank" -> r(150, 50, 30, 14, 4, INK) + r(220, 50, 30, 14, 4, INK)
                    + r(110, 58, 180, 300, 32, c, edge) + r(160, 110, 80, 40, 8, "#0b1220")
                    + bars(172, 120, 4, 12, 20, "#22c55e") + r(122, 70, 14, 276, 7, "#fff", " opacity=\".18\"");
            case "watch" -> r(160, 16, 80, 120, 22, c, edge) + r(160, 266, 80, 120, 22, c, edge)
                    + r(132, 106, 136, 190, 44, "#1f2937") + r(145, 119, 110, 164, 34, SCREEN)
                    + c(200, 200, 38, "none", " stroke=\"#22c55e\" stroke-width=\"9\" stroke-dasharray=\"170 70\""
                    + " stroke-linecap=\"round\"")
                    + c(200, 200, 22, "none", " stroke=\"#38bdf8\" stroke-width=\"7\" stroke-dasharray=\"90 60\"")
                    + r(266, 170, 12, 36, 4, "#374151");
            case "headphones" -> stroke("M92 236 C 92 60, 308 60, 308 236", c, 26)
                    + stroke("M92 236 C 92 60, 308 60, 308 236", l, 6, " opacity=\".5\"")
                    + r(52, 196, 86, 140, 40, c, edge) + r(262, 196, 86, 140, 40, c, edge)
                    + r(122, 212, 30, 108, 14, d) + r(248, 212, 30, 108, 14, d)
                    + r(64, 210, 12, 110, 6, "#fff", " opacity=\".2\"");
            case "earbuds" -> r(95, 222, 210, 140, 62, c, edge) + stroke("M97 272 H303", d, 4)
                    + c(200, 320, 5, "#22c55e") + bud(145, 130, p) + bud(255, 130, p);
            case "speaker" -> r(125, 66, 150, 290, 64, c, edge) + r(140, 112, 120, 200, 46, d, " opacity=\".55\"")
                    + dots(156, 132, 5, 9, 22, 21, l) + r(170, 50, 60, 26, 12, d)
                    + r(136, 80, 12, 260, 6, "#fff", " opacity=\".2\"");
            case "party-speaker" -> r(160, 24, 80, 24, 12, d) + r(105, 40, 190, 334, 26, c, edge)
                    + c(200, 118, 34, d) + c(200, 118, 20, "#0b1220")
                    + c(200, 262, 78, "none", " stroke=\"url(#rgb)\" stroke-width=\"9\"")
                    + c(200, 262, 64, d) + c(200, 262, 44, "#0b1220") + c(200, 262, 15, d);
            case "microphone" -> stroke("M138 126 V176 A62 62 0 0 0 262 176 V126", d, 10)
                    + r(150, 36, 100, 160, 50, c, edge) + grille(160, 70, 80, 7, 16, l)
                    + r(186, 236, 28, 92, 6, d) + e(200, 340, 96, 22, c, edge);
            case "soundbar" -> r(262, 96, 118, 262, 20, d) + c(321, 214, 38, shade(c, 0.45))
                    + c(321, 214, 14, d) + r(14, 252, 310, 66, 33, c, edge)
                    + r(36, 266, 266, 38, 19, d, " opacity=\".6\"") + c(300, 285, 4, "#22c55e");
            case "turntable" -> r(40, 50, 320, 300, 24, c, edge) + c(180, 200, 124, "#111827")
                    + c(180, 200, 100, "none", " stroke=\"#1f2937\" stroke-width=\"3\"")
                    + c(180, 200, 78, "none", " stroke=\"#1f2937\" stroke-width=\"3\"")
                    + c(180, 200, 36, "#ef4444") + c(180, 200, 5, "#e5e7eb")
                    + c(316, 92, 20, METAL) + stroke("M316 92 L306 250 L270 280", METAL, 8)
                    + c(326, 316, 11, "#e5e7eb") + c(290, 316, 11, "#e5e7eb");
            case "smart-speaker" -> r(118, 96, 164, 266, 46, c, edge) + e(200, 98, 82, 22, d)
                    + e(200, 98, 60, 14, "none", " stroke=\"#38bdf8\" stroke-width=\"5\"")
                    + texture(130, 140, 140, 200, 12, l) + r(128, 110, 12, 240, 6, "#fff", " opacity=\".18\"");
            case "laptop" -> r(66, 60, 268, 184, 14, d) + r(78, 72, 244, 160, 5, SCREEN) + glare(78, 72, 244, 160)
                    + path("M30 244 H370 L384 266 Q386 276 374 276 H26 Q14 276 16 266 Z", c, edge)
                    + r(168, 244, 64, 8, 4, d);
            case "mouse" -> r(138, 64, 124, 270, 62, c, edge) + stroke("M200 70 V168", d, 3)
                    + r(191, 104, 18, 44, 9, d) + r(146, 86, 10, 200, 5, "#fff", " opacity=\".2\"");
            case "keyboard" -> r(14, 112, 372, 180, 20, c, edge) + keys(p);
            case "monitor" -> r(24, 30, 352, 232, 14, d) + r(36, 42, 328, 200, 6, SCREEN) + glare(36, 42, 328, 200)
                    + r(184, 262, 32, 64, 6, c, edge) + r(116, 322, 168, 18, 9, c, edge);
            case "hub" -> stroke("M92 196 C 30 196, 40 310, 110 330", d, 12) + r(110, 318, 40, 24, 6, d)
                    + r(88, 150, 226, 94, 26, c, edge) + r(112, 186, 36, 20, 4, INK)
                    + r(160, 188, 28, 15, 3, "#1e293b") + r(196, 188, 28, 15, 3, "#1e293b")
                    + r(232, 188, 28, 15, 3, "#1e293b") + r(272, 192, 26, 7, 3, INK);
            case "webcam" -> r(140, 262, 120, 44, 12, d) + r(172, 236, 56, 34, 6, d)
                    + r(86, 126, 228, 116, 58, c, edge) + c(200, 184, 46, "#0b1220") + c(200, 184, 30, "#1e3a8a")
                    + c(188, 172, 9, "#fff", " opacity=\".5\"") + c(272, 166, 5, "#22c55e");
            case "ssd" -> r(108, 86, 184, 248, 30, c, edge) + r(124, 102, 152, 216, 22, l, " opacity=\".35\"")
                    + r(178, 326, 44, 9, 4, INK) + c(200, 128, 5, "#22c55e");
            case "laptop-stand" -> r(70, 330, 260, 14, 7, c, edge) + stroke("M110 330 L232 168", c, 14)
                    + stroke("M290 330 L304 152", c, 14) + stroke("M186 194 L346 132", d, 18)
                    + path("M176 182 L342 118 L350 134 L184 198 Z", "#475569")
                    + stroke("M342 118 L292 10", "#334155", 12);
            case "router" -> r(78, 96, 116, 250, 34, c, edge) + r(214, 126, 108, 220, 32, c, edge)
                    + e(136, 112, 40, 8, l) + e(268, 140, 36, 7, l) + c(136, 310, 6, "#22c55e")
                    + c(268, 310, 6, "#22c55e") + r(90, 110, 10, 220, 5, "#fff", " opacity=\".25\"");
            case "pan" -> stroke("M296 220 L392 196", "#1f2937", 24)
                    + path("M24 222 Q34 296 170 298 Q306 296 316 222 Z", c, edge) + e(170, 222, 146, 58, d)
                    + e(170, 216, 128, 46, "#1f2937") + e(140, 204, 60, 14, "#fff", " opacity=\".08\"");
            case "pot" -> r(28, 196, 56, 22, 11, d) + r(316, 196, 56, 22, 11, d)
                    + r(70, 168, 260, 170, 34, c, edge) + e(200, 168, 132, 24, d)
                    + path("M76 168 Q200 70 324 168 Z", GLASS, " opacity=\".7\" stroke=\"" + d + "\" stroke-width=\"4\"")
                    + r(178, 96, 44, 24, 10, d) + r(86, 186, 14, 130, 7, "#fff", " opacity=\".22\"");
            case "kettle" -> stroke("M268 140 Q356 150 330 306", "#1f2937", 20)
                    + path("M118 150 L66 116 L78 104 L140 134 Z", c, edge)
                    + path("M108 330 L130 118 Q200 98 270 118 L292 330 Z", GLASS,
                    " opacity=\".85\" stroke=\"" + d + "\" stroke-width=\"4\"")
                    + path("M116 330 L124 230 Q200 214 276 230 L284 330 Z", "#93c5fd", " opacity=\".55\"")
                    + e(200, 118, 72, 14, c) + r(90, 322, 220, 42, 16, "#1f2937");
            case "espresso" -> r(80, 38, 240, 30, 12, METAL) + r(80, 60, 240, 300, 24, c, edge)
                    + c(130, 104, 20, "#f8fafc") + stroke("M130 104 L140 94", "#ef4444", 3)
                    + r(146, 140, 108, 30, 8, METAL) + stroke("M200 170 L334 186", "#111827", 16)
                    + r(108, 296, 184, 32, 6, "#9ca3af") + r(170, 244, 60, 52, 10, "#fff")
                    + stroke("M230 256 Q252 270 230 284", "#fff", 6) + stroke("M296 130 L318 262", METAL, 8);
            case "air-fryer" -> r(86, 60, 228, 304, 62, c, edge) + c(200, 120, 32, "#0b1220")
                    + c(200, 120, 32, "none", " stroke=\"#f97316\" stroke-width=\"4\"")
                    + r(132, 184, 136, 104, 22, "#0b1220", " opacity=\".85\"") + r(156, 306, 88, 26, 13, d)
                    + r(98, 80, 14, 260, 7, "#fff", " opacity=\".15\"");
            case "knife" -> path("M20 252 L270 214 L292 226 L292 252 Q160 296 20 252 Z", "#e5e7eb",
                    " stroke=\"#9ca3af\" stroke-width=\"3\"") + stroke("M40 252 Q160 276 290 248", "#fff", 3)
                    + r(286, 216, 112, 40, 16, c, edge) + c(316, 236, 5, METAL) + c(346, 236, 5, METAL)
                    + c(376, 236, 5, METAL);
            case "cutting-board" -> r(70, 70, 290, 210, 28, d) + r(50, 110, 300, 220, 30, c, edge)
                    + c(310, 150, 16, mix(c, 0.85)) + stroke("M80 300 Q200 280 320 300", d, 3)
                    + stroke("M80 180 Q190 160 300 190", d, 3) + r(30, 170, 230, 170, 26, l, edge);
            case "containers" -> r(110, 84, 180, 30, 12, c) + r(120, 110, 160, 70, 16, GLASS, glassEdge())
                    + r(60, 192, 280, 32, 12, c, edge) + r(70, 220, 260, 130, 22, GLASS, glassEdge())
                    + r(96, 262, 90, 60, 12, "#86efac") + r(200, 252, 100, 70, 12, "#fca5a5");
            case "hand-blender" -> r(162, 14, 76, 210, 38, c, edge) + c(200, 70, 12, d) + c(200, 104, 8, d)
                    + r(186, 222, 28, 108, 6, METAL) + path("M146 330 Q200 300 254 330 L242 374 H158 Z", METAL);
            case "scale" -> r(56, 120, 288, 232, 32, c, edge) + r(66, 130, 268, 212, 26, "#fff", " opacity=\".25\"")
                    + r(146, 292, 108, 36, 8, "#0b1220") + bars(162, 302, 4, 16, 22, "#67e8f9");
            case "french-press" -> r(194, 36, 12, 70, 4, d) + c(200, 36, 16, d)
                    + stroke("M270 146 Q348 156 334 300 L272 312", d, 18) + r(118, 88, 164, 28, 10, d)
                    + r(128, 110, 144, 238, 14, c, edge) + r(122, 214, 156, 18, 6, d) + r(120, 340, 160, 18, 6, d);
            case "table-lamp" -> c(200, 200, 120, "#fde68a", " opacity=\".25\"")
                    + r(186, 196, 28, 132, 8, "#a16207") + e(200, 332, 74, 16, "#a16207")
                    + path("M116 64 H284 L326 204 H74 Z", c, edge) + stroke("M118 64 H282", d, 6);
            case "led-strip" -> stroke("M30 120 C 120 40, 180 220, 260 140 S 370 120, 370 220", "url(#rgb)", 16)
                    + stroke("M30 260 C 110 200, 190 330, 280 270", "url(#rgb)", 16, " opacity=\".75\"")
                    + r(282, 250, 64, 116, 18, "#1f2937") + c(314, 284, 12, c) + c(300, 326, 6, "#94a3b8")
                    + c(328, 326, 6, "#94a3b8");
            case "cushion" -> path("M100 70 Q220 46 350 76 Q378 180 352 290 Q220 316 92 286 Q70 180 100 70 Z", d)
                    + path("M50 100 Q190 70 320 100 Q348 210 322 330 Q190 356 48 326 Q22 210 50 100 Z", c, edge)
                    + stroke("M80 140 Q190 120 290 140", l, 4, " opacity=\".6\"")
                    + stroke("M76 290 Q186 312 300 290", d, 4, " opacity=\".5\"");
            case "vase" -> stroke("M120 150 Q110 60 80 30", "#15803d", 4) + e(84, 40, 16, 8, "#22c55e")
                    + path("M90 360 Q70 280 100 230 Q112 200 108 150 H132 Q128 200 140 230 Q170 280 150 360 Z", c, edge)
                    + path("M180 360 Q150 300 180 260 Q200 236 236 236 Q272 236 292 260 Q322 300 292 360 Z", l, edge)
                    + r(300, 200, 70, 160, 22, d) + stroke("M236 236 Q240 150 270 110", "#15803d", 4)
                    + e(274, 112, 18, 9, "#22c55e");
            case "mirror" -> stroke("M200 20 L130 64 M200 20 L270 64", d, 5) + c(200, 200, 160, c, edge)
                    + c(200, 200, 146, "url(#mirror)") + path("M120 120 L170 80 L260 300 L210 330 Z", "#fff",
                    " opacity=\".35\"") + path("M200 70 L222 60 L300 250 L280 262 Z", "#fff", " opacity=\".3\"");
            case "candle" -> candle(64, 190, 96, 130, c) + candle(160, 160, 96, 160, d) + candle(256, 200, 96, 120, l);
            case "blanket" -> r(62, 264, 276, 70, 30, c, edge) + r(52, 196, 296, 74, 30, d)
                    + r(70, 130, 260, 72, 30, c, edge) + knit(84, 150, 232, 36, l) + knit(84, 286, 232, 36, l)
                    + fringe(338, 270, 8, d);
            case "frames" -> frame(20, 96, 108, 150, c) + frame(146, 70, 108, 200, c) + frame(272, 96, 108, 150, c);
            case "floor-lamp" -> e(112, 368, 64, 14, "#e5e7eb") + stroke("M112 366 V184 Q112 40 276 56", c, 10)
                    + c(300, 150, 80, "#fde68a", " opacity=\".25\"") + path("M236 52 H320 L348 128 H208 Z", c, edge);
            case "plant-hanger" -> stroke("M200 6 L140 240 M200 6 L260 240 M200 6 L200 240", "#d6c7a1", 5)
                    + c(200, 20, 10, d) + c(172, 120, 7, d) + c(228, 120, 7, d)
                    + leaves() + r(136, 236, 128, 110, 20, c, edge) + stroke("M150 330 Q120 370 140 396", "#16a34a", 4);
            case "tshirt" -> garment("M140 60 L96 78 L36 150 L88 192 L120 162 L120 362 H280 V162 L312 192 "
                    + "L364 150 L304 78 L260 60 Q200 104 140 60 Z", p) + stroke("M140 60 Q200 112 260 60", p.d(), 7);
            case "hoodie" -> path("M146 58 Q200 -6 254 58 Q200 96 146 58 Z", d)
                    + garment("M146 58 L92 80 L40 300 L84 314 L122 170 L120 364 H280 L278 170 L316 314 "
                    + "L360 300 L308 80 L254 58 Q200 100 146 58 Z", p)
                    + r(150, 270, 100, 56, 20, d, " opacity=\".55\"") + stroke("M184 80 V130 M216 80 V130", "#fff", 4);
            case "jeans" -> garment("M118 40 H282 L304 362 H214 L200 146 L186 362 H96 Z", p)
                    + r(118, 40, 164, 26, 4, d) + c(200, 54, 5, METAL)
                    + stroke("M134 70 Q150 110 186 96 M266 70 Q250 110 214 96", l, 3, " stroke-dasharray=\"6 5\"");
            case "leggings" -> garment("M138 40 H262 L252 362 H210 L200 160 L190 362 H148 Z", p)
                    + r(138, 40, 124, 34, 6, d);
            case "jacket" -> garment("M146 56 L92 78 L40 300 L84 314 L122 170 L120 364 H280 L278 170 L316 314 "
                    + "L360 300 L308 78 L254 56 L200 96 Z", p)
                    + stroke("M200 96 V364", d, 5) + stroke("M146 56 L176 120 L200 96 L224 120 L254 56", d, 6)
                    + stroke("M124 210 H276 M124 270 H276", d, 3, " opacity=\".5\"")
                    + r(136, 290, 40, 6, 3, d) + r(224, 290, 40, 6, 3, d);
            case "shirt" -> garment("M146 56 L92 78 L40 300 L84 314 L122 170 L120 364 H280 L278 170 L316 314 "
                    + "L360 300 L308 78 L254 56 Q200 76 146 56 Z", p)
                    + path("M146 56 L180 112 L200 72 Z", d) + path("M254 56 L220 112 L200 72 Z", d)
                    + stroke("M200 76 V364", d, 3) + c(200, 150, 4, d) + c(200, 210, 4, d) + c(200, 270, 4, d);
            case "sweater" -> garment("M146 56 L92 78 L40 300 L84 314 L122 170 L120 364 H280 L278 170 L316 314 "
                    + "L360 300 L308 78 L254 56 Q200 96 146 56 Z", p)
                    + stroke("M146 56 Q200 100 254 56", d, 9) + stroke("M122 346 H278", d, 14)
                    + stroke("M46 296 L80 308 M354 296 L320 308", d, 12) + knit(130, 160, 140, 160, l);
            case "socks" -> sock(70, d) + sock(140, c) + "";
            case "sneaker" -> shoe(p, "#f1f5f9");
            case "running-shoe" -> shoe(p, l);
            case "boot" -> path("M108 54 H236 V246 Q306 252 344 278 Q364 298 352 322 H88 Q86 200 108 54 Z", c, edge)
                    + path("M200 120 Q214 180 236 190 V120 Z", d) + r(84, 318, 274, 26, 8, "#1f2937")
                    + r(118, 36, 36, 30, 8, d);
            case "backpack" -> stroke("M166 62 Q200 14 234 62", d, 12) + r(96, 56, 208, 306, 54, c, edge)
                    + path("M96 140 Q200 100 304 140 V180 H96 Z", d) + r(128, 210, 144, 124, 30, d)
                    + stroke("M146 226 H254", l, 4);
            case "crossbody" -> stroke("M96 186 Q200 -50 304 186", d, 10) + r(78, 168, 244, 176, 30, c, edge)
                    + path("M78 200 Q78 168 110 168 H290 Q322 168 322 200 V262 H78 Z", d)
                    + r(186, 246, 28, 32, 6, METAL);
            case "duffel" -> stroke("M128 176 Q200 84 272 176", d, 16) + r(24, 168, 352, 168, 84, c, edge)
                    + stroke("M70 176 Q200 150 330 176", d, 5) + r(132, 236, 136, 70, 18, d, " opacity=\".6\"")
                    + c(44, 252, 30, d, " opacity=\".5\"") + c(356, 252, 30, d, " opacity=\".5\"");
            case "sandal" -> path("M70 60 Q150 40 160 140 Q168 250 146 340 Q116 376 84 340 Q52 250 58 150 Q56 80 70 60 Z",
                    "#d6d3d1", " opacity=\".9\"") + r(40, 130, 140, 80, 30, d)
                    + path("M200 36 Q306 18 318 140 Q328 262 294 352 Q254 392 204 352 Q164 262 174 140 Q168 56 200 36 Z",
                    "#e7e5e4", " stroke=\"#a8a29e\" stroke-width=\"3\"") + r(150, 124, 198, 92, 36, c, edge)
                    + stroke("M176 300 Q246 316 304 300", "#a8a29e", 3);
            case "wallet" -> r(110, 82, 124, 50, 6, "#60a5fa") + r(90, 96, 124, 50, 6, "#e5e7eb")
                    + r(66, 112, 268, 186, 24, c, edge) + stroke("M200 112 V298", d, 4)
                    + r(80, 126, 240, 158, 16, "none", " stroke=\"" + l + "\" stroke-width=\"2\" stroke-dasharray=\"7 6\"");
            case "tote" -> stroke("M150 142 Q150 44 200 44 Q250 44 250 142", d, 12)
                    + path("M86 140 H314 L292 362 H108 Z", c, edge) + r(150, 210, 100, 70, 10, d, " opacity=\".25\"");
            case "serum" -> r(180, 40, 40, 86, 20, "#111827") + r(168, 118, 64, 44, 8, "#1f2937")
                    + r(128, 156, 144, 196, 28, c, edge) + r(150, 220, 100, 92, 8, "#fff")
                    + r(166, 240, 68, 8, 4, c) + r(166, 258, 50, 6, 3, "#cbd5e1") + r(140, 170, 12, 160, 6, "#fff", " opacity=\".35\"");
            case "cream" -> r(86, 160, 228, 54, 16, c, edge) + r(98, 206, 204, 130, 22, "#f8fafc",
                    " stroke=\"#e2e8f0\" stroke-width=\"3\"") + r(130, 246, 140, 44, 8, c, " opacity=\".55\"");
            case "pump-bottle" -> path("M166 104 V78 H268 V96 H222 V104 Z", "#e5e7eb") + r(178, 100, 44, 44, 6, "#e5e7eb")
                    + r(128, 140, 144, 216, 32, c, edge) + r(150, 210, 100, 96, 10, "#fff", " opacity=\".85\"")
                    + r(166, 232, 68, 8, 4, d);
            case "oil-bottle" -> r(174, 54, 52, 40, 8, "#111827")
                    + path("M140 360 V184 Q140 140 180 130 V92 H220 V130 Q260 140 260 184 V360 Z", c, edge)
                    + r(156, 220, 88, 100, 8, "#fef3c7") + r(172, 248, 56, 8, 4, d);
            case "toothbrush" -> e(200, 360, 70, 16, "#e5e7eb") + r(176, 118, 48, 244, 24, c, edge)
                    + r(188, 60, 24, 70, 10, c, edge) + r(182, 16, 36, 60, 14, "#e5e7eb") + r(188, 24, 24, 44, 6, "#38bdf8")
                    + c(200, 214, 12, "#38bdf8");
            case "hair-dryer" -> r(150, 180, 70, 180, 30, c, " transform=\"rotate(10 185 270)\"" + edge)
                    + r(78, 86, 244, 116, 58, c, edge) + r(300, 108, 76, 72, 12, d) + c(116, 144, 42, d)
                    + grille(92, 126, 48, 6, 7, l) + c(190, 250, 8, l);
            case "deodorant" -> r(150, 54, 100, 78, 30, d) + r(150, 104, 100, 254, 30, c, edge)
                    + r(166, 196, 68, 90, 8, "#fff", " opacity=\".7\"");
            case "brushes" -> brush(-28, c) + brush(-12, d) + brush(4, c) + brush(20, d) + brush(34, c);
            case "diffuser" -> stroke("M200 120 C 170 90, 230 70, 200 40 C 180 20, 220 10, 210 0", "#e2e8f0", 10,
                    " opacity=\".9\"") + path("M108 344 Q98 200 200 122 Q302 200 292 344 Z", c, edge)
                    + r(104, 320, 192, 30, 10, d) + e(200, 362, 110, 12, "#a78bfa", " opacity=\".35\"");
            case "yoga-mat" -> r(70, 164, 262, 140, 8, c, edge) + e(332, 234, 44, 70, l, edge)
                    + e(332, 234, 30, 50, "none", " stroke=\"" + d + "\" stroke-width=\"4\"")
                    + e(332, 234, 14, 26, d) + stroke("M140 160 V308 M260 160 V308", "#111827", 8);
            case "dumbbell" -> r(116, 188, 168, 26, 13, "#9ca3af") + r(56, 146, 30, 110, 10, d)
                    + r(82, 122, 46, 158, 12, c, edge) + r(272, 122, 46, 158, 12, c, edge) + r(314, 146, 30, 110, 10, d)
                    + c(105, 202, 10, l) + c(295, 202, 10, l);
            case "bands" -> e(200, 150, 140, 60, "none", " stroke=\"" + c + "\" stroke-width=\"14\"")
                    + e(200, 200, 140, 60, "none", " stroke=\"#f59e0b\" stroke-width=\"14\"")
                    + e(200, 250, 140, 60, "none", " stroke=\"#22c55e\" stroke-width=\"14\"")
                    + e(200, 300, 140, 60, "none", " stroke=\"#3b82f6\" stroke-width=\"14\"");
            case "bottle" -> stroke("M180 40 Q200 10 220 40", "#1f2937", 8) + r(154, 40, 92, 62, 16, "#1f2937")
                    + r(138, 92, 124, 274, 38, c, edge) + r(138, 180, 124, 40, 0, l, " opacity=\".5\"")
                    + r(150, 110, 12, 236, 6, "#fff", " opacity=\".22\"");
            case "foam-roller" -> r(54, 150, 292, 140, 24, c, edge) + e(346, 220, 32, 70, d) + bumps(80, 170, 250, 100, l);
            case "tent" -> stroke("M10 336 H390", "#65a30d", 6) + path("M36 334 L200 86 L364 334 Z", c, edge)
                    + path("M200 86 L148 334 H252 Z", d) + path("M200 86 L252 334 L292 334 Z", l, " opacity=\".7\"")
                    + stroke("M200 86 L200 60", "#334155", 4);
            case "trekking-poles" -> pole(-14, c) + pole(10, d);
            case "jump-rope" -> stroke("M110 110 C 60 380, 340 380, 290 110", "#111827", 5)
                    + r(92, 30, 36, 96, 16, c, edge) + r(272, 30, 36, 96, 16, c, edge);
            case "helmet" -> path("M54 284 Q54 92 220 80 Q356 86 364 262 Q300 252 240 272 L54 284 Z", c, edge)
                    + path("M120 120 Q170 100 200 140 L160 160 Z", d) + path("M230 104 Q290 110 310 160 L262 162 Z", d)
                    + path("M200 170 Q250 160 280 200 L220 214 Z", d) + r(340, 236, 22, 36, 6, "#ef4444")
                    + stroke("M140 284 Q170 340 220 330", "#111827", 5);
            case "pullup-bar" -> stroke("M30 120 V180 M370 120 V180", "#475569", 10) + r(20, 168, 360, 24, 12, c, edge)
                    + r(40, 162, 80, 36, 18, "#111827") + r(280, 162, 80, 36, 18, "#111827");
            case "book" -> r(88, 48, 34, 304, 6, p.d()) + r(116, 48, 194, 304, 8, c, edge)
                    + r(300, 56, 14, 288, 2, "#f5f5f4") + r(146, 100, 134, 14, 4, l) + r(146, 126, 96, 10, 4, l)
                    + c(212, 240, 44, l, " opacity=\".5\"");
            case "book-stack" -> r(56, 266, 290, 56, 6, c, edge) + r(70, 276, 262, 36, 2, "#f5f5f4")
                    + r(80, 210, 260, 56, 6, l, edge) + r(94, 220, 232, 36, 2, "#f5f5f4")
                    + r(60, 154, 272, 56, 6, d) + r(74, 164, 244, 36, 2, "#f5f5f4") + r(56, 266, 30, 56, 6, d)
                    + r(80, 210, 30, 56, 6, c) + r(60, 154, 30, 56, 6, c);
            case "notebook" -> r(108, 46, 206, 308, 10, "#f5f5f4") + r(96, 44, 206, 310, 16, c, edge)
                    + r(262, 44, 12, 310, 2, shade(c, 0.6)) + path("M150 354 V392 L162 382 L174 392 V354 Z", "#ef4444");
            case "pen" -> "<g transform=\"rotate(-35 200 200)\">" + r(70, 184, 236, 32, 16, c, edge)
                    + r(70, 184, 90, 32, 16, d) + r(160, 182, 8, 36, 2, "#eab308")
                    + path("M304 186 L366 200 L304 214 Z", "#e5e7eb") + r(84, 176, 70, 6, 3, "#eab308") + "</g>";
            case "paint-set" -> r(44, 100, 312, 210, 18, "#e5e7eb", " stroke=\"#94a3b8\" stroke-width=\"3\"") + paints()
                    + "<g transform=\"rotate(-20 200 340)\">" + r(70, 334, 220, 12, 6, "#a16207")
                    + path("M290 334 L330 340 L290 346 Z", "#111827") + "</g>";
            case "desk-organiser" -> stroke("M110 200 L94 70", "#ef4444", 10) + stroke("M140 200 L150 60", "#2563eb", 10)
                    + stroke("M170 200 L176 90", "#111827", 10) + r(240, 80, 60, 120, 10, "#1f2937")
                    + r(70, 186, 260, 150, 10, c, edge) + stroke("M200 186 V336 M270 186 V336", d, 6);
            case "board-game" -> r(66, 60, 268, 268, 14, c, edge) + hexes(p) + r(240, 280, 62, 62, 12, "#fff")
                    + c(258, 298, 6, INK) + c(284, 324, 6, INK) + c(271, 311, 6, INK);
            case "jigsaw" -> r(50, 70, 300, 240, 12, c, edge) + skyline(p)
                    + stroke("M150 70 V310 M250 70 V310 M50 190 H350", "#fff", 3, " opacity=\".5\" stroke-dasharray=\"10 8\"")
                    + path("M290 320 h40 a12 12 0 1 1 20 0 h40 v60 h-100 Z", l, edge);
            case "bricks" -> brick(70, 250, 160, c) + brick(230, 250, 100, "#facc15") + brick(110, 170, 120, "#3b82f6")
                    + brick(230, 170, 80, "#22c55e") + brick(150, 90, 100, c);
            case "rc-car" -> path("M40 250 L70 180 L150 160 L250 160 L320 190 L360 250 Z", c, edge)
                    + path("M150 168 L180 120 H250 L280 172 Z", d) + r(300, 140, 70, 14, 4, "#111827")
                    + wheel(100, 270) + wheel(300, 270);
            case "train" -> r(30, 200, 140, 90, 10, "#2563eb") + r(190, 170, 180, 120, 12, c, edge)
                    + r(290, 110, 80, 80, 8, d) + r(210, 120, 30, 50, 4, "#111827") + c(76, 300, 24, "#111827")
                    + c(126, 300, 24, "#111827") + c(230, 300, 26, "#111827") + c(330, 300, 26, "#111827")
                    + r(170, 240, 20, 10, 2, "#a16207");
            case "teddy" -> c(130, 96, 36, c) + c(270, 96, 36, c) + c(130, 96, 18, l) + c(270, 96, 18, l)
                    + e(200, 290, 110, 90, c) + c(200, 150, 92, c) + e(200, 182, 40, 30, l)
                    + c(166, 140, 9, INK) + c(234, 140, 9, INK) + e(200, 170, 14, 10, INK)
                    + c(100, 330, 34, d) + c(300, 330, 34, d) + c(96, 250, 30, d) + c(304, 250, 30, d);
            case "science-kit" -> path("M170 60 H230 V150 L300 330 Q306 352 282 352 H118 Q94 352 100 330 L170 150 Z",
                    GLASS, glassEdge()) + path("M132 250 H268 L298 330 Q302 344 286 344 H114 Q98 344 102 330 Z", c)
                    + c(180, 290, 10, "#fff", " opacity=\".6\"") + c(220, 310, 7, "#fff", " opacity=\".6\"")
                    + r(310, 140, 34, 200, 16, GLASS, glassEdge()) + r(312, 240, 30, 98, 14, "#f59e0b");
            case "cards" -> card(-18, "#f59e0b") + card(0, "#22c55e") + card(18, c);
            case "drone" -> stroke("M110 110 L290 290 M290 110 L110 290", "#1f2937", 14) + r(150, 160, 100, 80, 24, c, edge)
                    + prop(110, 110) + prop(290, 110) + prop(110, 290) + prop(290, 290) + c(200, 252, 14, "#0b1220");
            case "tiles" -> r(110, 200, 90, 90, 6, c, " opacity=\".85\"") + r(200, 200, 90, 90, 6, "#f59e0b", " opacity=\".85\"")
                    + path("M110 200 L200 110 L290 200 Z", "#ef4444", " opacity=\".85\"")
                    + r(110, 290, 90, 60, 6, "#22c55e", " opacity=\".85\"") + r(200, 290, 90, 60, 6, "#a855f7", " opacity=\".85\"");
            default -> r(100, 120, 200, 200, 16, c, edge) + path("M100 120 L150 70 H350 L300 120 Z", l)
                    + path("M300 120 L350 70 V270 L300 320 Z", d);
        };
    }

    // ---------- composite parts ----------

    private static String phone(Pal p, int x) {
        return r(x, 30, 160, 340, 30, p.c(), p.edge()) + r(x + 10, 42, 140, 316, 22, SCREEN)
                + c(x + 80, 58, 5, "#111827") + glare(x + 10, 42, 140, 316) + r(x + 158, 110, 5, 42, 2, p.d());
    }

    private static String bud(int x, int y, Pal p) {
        return r(x - 12, y, 24, 84, 12, p.c(), p.edge()) + c(x, y, 36, p.c(), p.edge()) + e(x - 4, y - 4, 16, 18, p.d());
    }

    private static String shoe(Pal p, String sole) {
        return path("M40 292 Q36 232 92 222 L168 202 Q200 150 252 160 L300 176 Q354 192 362 252 L364 292 Z",
                p.c(), p.edge()) + path("M28 290 H372 Q378 322 346 324 H56 Q24 322 28 290 Z", sole,
                " stroke=\"" + shade(sole, 0.75) + "\" stroke-width=\"3\"")
                + stroke("M196 176 L220 216 M214 168 L240 206 M232 164 L258 200", p.d(), 5)
                + stroke("M70 262 Q180 246 300 262", p.d(), 3, " stroke-dasharray=\"8 6\"");
    }

    private static String garment(String d, Pal p) {
        return path(d, p.c(), p.edge());
    }

    private static String sock(int x, String fill) {
        return path("M" + x + " 40 H" + (x + 72) + " V228 Q" + (x + 72) + " 268 " + (x + 116) + " 290 L" + (x + 150)
                + " 306 Q" + (x + 182) + " 322 " + (x + 170) + " 352 Q" + (x + 158) + " 378 " + (x + 126) + " 368 L"
                + (x + 30) + " 330 Q" + x + " 314 " + x + " 280 Z", fill, " stroke=\"#94a3b8\" stroke-width=\"2\"")
                + r(x, 40, 72, 34, 4, "#94a3b8", " opacity=\".5\"");
    }

    private static String candle(int x, int y, int w, int h, String fill) {
        int cx = x + w / 2;
        return r(x, y, w, h, 14, fill) + e(cx, y + 6, w / 2 - 6, 10, "#fef3c7") + stroke("M" + cx + " " + y + " V" + (y - 18), "#1f2937", 3)
                + path("M" + cx + " " + (y - 60) + " Q" + (cx + 16) + " " + (y - 30) + " " + cx + " " + (y - 18)
                + " Q" + (cx - 16) + " " + (y - 30) + " " + cx + " " + (y - 60) + " Z", "#f59e0b")
                + e(cx, y - 30, 5, 9, "#fde68a");
    }

    private static String frame(int x, int y, int w, int h, String wood) {
        return r(x, y, w, h, 4, wood) + r(x + 10, y + 10, w - 20, h - 20, 2, "#fafaf9")
                + stroke("M" + (x + w / 2) + " " + (y + h - 24) + " V" + (y + 30), "#15803d", 3)
                + e(x + w / 2 - 14, y + h / 2, 14, 7, "#22c55e") + e(x + w / 2 + 14, y + h / 2 - 22, 14, 7, "#16a34a")
                + e(x + w / 2 - 12, y + h / 2 - 40, 12, 6, "#4ade80");
    }

    private static String leaves() {
        return e(170, 236, 34, 12, "#16a34a") + e(232, 232, 34, 12, "#22c55e") + e(200, 220, 12, 30, "#15803d");
    }

    private static String brush(int angle, String handle) {
        return "<g transform=\"rotate(" + angle + " 200 380)\">" + r(192, 190, 16, 190, 8, handle)
                + r(189, 146, 22, 48, 4, METAL) + path("M187 148 Q200 60 213 148 Z", "#1f2937") + "</g>";
    }

    private static String pole(int angle, String color) {
        return "<g transform=\"rotate(" + angle + " 200 380)\">" + r(194, 40, 12, 330, 6, METAL)
                + r(188, 30, 24, 80, 10, "#a16207") + r(186, 104, 28, 10, 4, color) + e(200, 330, 24, 6, "#1f2937")
                + "</g>";
    }

    private static String card(int angle, String color) {
        return "<g transform=\"rotate(" + angle + " 200 360)\">" + r(130, 80, 140, 210, 16, "#fff",
                " stroke=\"#e2e8f0\" stroke-width=\"3\"") + r(144, 94, 112, 182, 10, color) + c(200, 185, 30, "#fff",
                " opacity=\".85\"") + "</g>";
    }

    private static String prop(int x, int y) {
        return e(x, y, 54, 12, "#94a3b8", " opacity=\".7\"") + c(x, y, 10, "#1f2937");
    }

    private static String wheel(int x, int y) {
        return c(x, y, 46, "#111827") + c(x, y, 22, "#9ca3af") + c(x, y, 8, "#111827");
    }

    private static String brick(int x, int y, int w, String color) {
        StringBuilder studs = new StringBuilder();
        for (int sx = x + 20; sx < x + w - 10; sx += 40) {
            studs.append(r(sx - 12, y - 14, 24, 16, 4, shade(color, 0.85)));
        }
        return studs + r(x, y, w, 80, 6, color, " stroke=\"" + shade(color, 0.75) + "\" stroke-width=\"3\"");
    }

    // ---------- textures ----------

    private static String glare(int x, int y, int w, int h) {
        return path("M" + (x + w / 3) + " " + y + " L" + (x + w / 3 + w / 4) + " " + y + " L" + (x + w / 6) + " "
                + (y + h) + " L" + x + " " + (y + h) + " L" + x + " " + (y + h * 3 / 4) + " Z", "#fff", " opacity=\".06\"");
    }

    private static String bars(int x, int y, int count, int h, int step, String color) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            out.append(r(x + i * step, y, step - 6, h, 2, color));
        }
        return out.toString();
    }

    private static String dots(int x, int y, int cols, int rows, int step, int stepY, String color) {
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                out.append(c(x + col * step, y + row * stepY, 4, color, " opacity=\".7\""));
            }
        }
        return out.toString();
    }

    private static String grille(int x, int y, int w, int lines, int step, String color) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            out.append(stroke("M" + x + " " + (y + i * step) + " H" + (x + w), color, 3, " opacity=\".6\""));
        }
        return out.toString();
    }

    private static String texture(int x, int y, int w, int h, int step, String color) {
        StringBuilder out = new StringBuilder();
        for (int ly = y; ly < y + h; ly += step) {
            out.append(stroke("M" + x + " " + ly + " H" + (x + w), color, 2, " opacity=\".35\""));
        }
        return out.toString();
    }

    private static String knit(int x, int y, int w, int h, String color) {
        StringBuilder out = new StringBuilder();
        for (int kx = x; kx < x + w; kx += 18) {
            out.append(stroke("M" + kx + " " + y + " l9 " + h / 2 + " l9 -" + h / 2, color, 3, " opacity=\".45\""));
        }
        return out.toString();
    }

    private static String fringe(int x, int y, int count, String color) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            out.append(stroke("M" + x + " " + (y + i * 8) + " h18", color, 3));
        }
        return out.toString();
    }

    private static String bumps(int x, int y, int w, int h, String color) {
        StringBuilder out = new StringBuilder();
        for (int by = y; by < y + h; by += 24) {
            for (int bx = x + ((by / 24) % 2) * 12; bx < x + w; bx += 24) {
                out.append(c(bx, by, 6, color, " opacity=\".6\""));
            }
        }
        return out.toString();
    }

    private static String keys(Pal p) {
        String key = shade(p.c(), 1.5);
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 15; col++) {
                boolean accent = row == 0 && col % 5 == 0;
                out.append(r(32 + col * 23, 128 + row * 28, 19, 22, 4, accent ? "#8b5cf6" : key));
            }
        }
        out.append(r(120, 268, 160, 16, 4, key));
        return out.toString();
    }

    private static String paints() {
        String[] colours = {"#ef4444", "#f97316", "#facc15", "#22c55e", "#06b6d4", "#3b82f6",
            "#8b5cf6", "#ec4899", "#a16207", "#78716c", "#111827", "#f8fafc"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 18; i++) {
            out.append(r(62 + (i % 6) * 48, 118 + (i / 6) * 60, 40, 50, 6, colours[i % colours.length]));
        }
        return out.toString();
    }

    private static String hexes(Pal p) {
        String[] colours = {p.l(), "#facc15", "#22c55e", "#a16207", p.d(), "#e5e7eb", "#84cc16"};
        StringBuilder out = new StringBuilder();
        int i = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cx = 130 + col * 66 + (row % 2) * 33;
                int cy = 120 + row * 58;
                out.append(path("M" + cx + " " + (cy - 34) + " l29 17 v34 l-29 17 l-29 -17 v-34 Z", colours[i++ % 7],
                        " stroke=\"#fff\" stroke-width=\"3\""));
            }
        }
        return out.toString();
    }

    private static String skyline(Pal p) {
        String dark = p.d();
        return r(50, 230, 300, 80, 0, dark) + r(80, 150, 40, 160, 2, dark) + r(130, 120, 30, 190, 2, dark)
                + r(170, 170, 50, 140, 2, dark) + r(230, 100, 26, 210, 2, dark) + r(270, 160, 50, 150, 2, dark)
                + c(300, 110, 22, "#fde68a");
    }

    // ---------- primitives ----------

    private static String glassEdge() {
        return " opacity=\".85\" stroke=\"#93c5fd\" stroke-width=\"3\"";
    }

    private static String r(int x, int y, int w, int h, int rx, String fill) {
        return r(x, y, w, h, rx, fill, "");
    }

    private static String r(int x, int y, int w, int h, int rx, String fill, String extra) {
        return "<rect x=\"" + x + "\" y=\"" + y + "\" width=\"" + w + "\" height=\"" + h + "\" rx=\"" + rx
                + "\" fill=\"" + fill + "\"" + extra + "/>";
    }

    private static String c(int cx, int cy, int radius, String fill) {
        return c(cx, cy, radius, fill, "");
    }

    private static String c(int cx, int cy, int radius, String fill, String extra) {
        return "<circle cx=\"" + cx + "\" cy=\"" + cy + "\" r=\"" + radius + "\" fill=\"" + fill + "\"" + extra + "/>";
    }

    private static String e(int cx, int cy, int rx, int ry, String fill) {
        return e(cx, cy, rx, ry, fill, "");
    }

    private static String e(int cx, int cy, int rx, int ry, String fill, String extra) {
        return "<ellipse cx=\"" + cx + "\" cy=\"" + cy + "\" rx=\"" + rx + "\" ry=\"" + ry + "\" fill=\"" + fill + "\""
                + extra + "/>";
    }

    private static String path(String d, String fill) {
        return path(d, fill, "");
    }

    private static String path(String d, String fill, String extra) {
        return "<path d=\"" + d + "\" fill=\"" + fill + "\"" + extra + "/>";
    }

    private static String stroke(String d, String color, int width) {
        return stroke(d, color, width, "");
    }

    private static String stroke(String d, String color, int width, String extra) {
        return "<path d=\"" + d + "\" fill=\"none\" stroke=\"" + color + "\" stroke-width=\"" + width
                + "\" stroke-linecap=\"round\" stroke-linejoin=\"round\"" + extra + "/>";
    }

    // ---------- colour maths ----------

    /** Blends the colour towards white; amount 0 = unchanged, 1 = white. */
    static String mix(String hex, double amount) {
        int[] rgb = rgb(hex);
        return hex(rgb[0] + (255 - rgb[0]) * amount, rgb[1] + (255 - rgb[1]) * amount, rgb[2] + (255 - rgb[2]) * amount);
    }

    static String shade(String hex, double factor) {
        int[] rgb = rgb(hex);
        if (factor > 1) {
            return mix(hex, Math.min(factor - 1, 1));
        }
        return hex(rgb[0] * factor, rgb[1] * factor, rgb[2] * factor);
    }

    private static double luminance(String hex) {
        int[] rgb = rgb(hex);
        return (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]) / 255;
    }

    private static int[] rgb(String hex) {
        return new int[]{Integer.parseInt(hex.substring(1, 3), 16), Integer.parseInt(hex.substring(3, 5), 16),
            Integer.parseInt(hex.substring(5, 7), 16)};
    }

    private static String hex(double r, double g, double b) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(double value) {
        return (int) Math.clamp(Math.round(value), 0, 255);
    }
}
