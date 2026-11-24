package com.pfe.marketplace.demo;

import com.pfe.marketplace.catalog.entity.ProductSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merchandising copy for the demo catalog (tagline, description, key benefits, spec sheet, sale price and
 * illustration), read from {@code demo/copy-*.txt}. Blocks start with {@code # Product name}; see the header of
 * {@code copy-electronics.txt} for the line format.
 */
final class DemoCopy {

    static final List<String> FILES = List.of(
            "demo/copy-electronics.txt", "demo/copy-home.txt", "demo/copy-fashion.txt", "demo/copy-leisure.txt");

    record ProductCopy(String art, String color, BigDecimal wasPrice, String tagline, String description,
                       List<String> highlights, List<ProductSpec> specs) {
    }

    private DemoCopy() {
    }

    static Map<String, ProductCopy> load() {
        Map<String, ProductCopy> copy = new HashMap<>();
        for (String file : FILES) {
            parse(read(file), copy);
        }
        return copy;
    }

    private static String read(String resource) {
        try (InputStream in = DemoCopy.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing demo resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Companions bought with a product, from {@code demo/pairings.txt} ("product => companion | companion"). */
    static Map<String, List<String>> loadPairings() {
        Map<String, List<String>> pairings = new HashMap<>();
        for (String line : read("demo/pairings.txt").split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("=>", 2);
            List<String> companions = new ArrayList<>();
            for (String companion : parts[1].split("\\|")) {
                companions.add(companion.strip());
            }
            pairings.put(parts[0].strip(), List.copyOf(companions));
        }
        return pairings;
    }

    static void parse(String text, Map<String, ProductCopy> into) {
        Builder current = null;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.startsWith("# ")) {
                add(current, into);
                current = new Builder(line.substring(2).strip());
            } else if (current != null && !line.isEmpty()) {
                current.accept(line);
            }
        }
        add(current, into);
    }

    /** Comment blocks have no "art:" line and are skipped. */
    private static void add(Builder builder, Map<String, ProductCopy> into) {
        if (builder != null && builder.art != null) {
            into.put(builder.name, builder.build());
        }
    }

    private static final class Builder {
        private final String name;
        private String art;
        private String color;
        private BigDecimal wasPrice;
        private String tagline;
        private final List<String> paragraphs = new ArrayList<>();
        private final List<String> highlights = new ArrayList<>();
        private final List<ProductSpec> specs = new ArrayList<>();

        Builder(String name) {
            this.name = name;
        }

        void accept(String line) {
            if (line.startsWith("+ ")) {
                highlights.add(line.substring(2).strip());
            } else if (line.startsWith("= ")) {
                String[] parts = line.substring(2).split(":", 2);
                specs.add(new ProductSpec(parts[0].strip(), parts.length > 1 ? parts[1].strip() : ""));
            } else if (line.startsWith("art:")) {
                String[] parts = line.substring(4).strip().split("\\s+");
                art = parts[0];
                color = parts.length > 1 ? parts[1] : "#64748b";
            } else if (line.startsWith("was:")) {
                wasPrice = new BigDecimal(line.substring(4).strip());
            } else if (line.startsWith("tag:")) {
                tagline = line.substring(4).strip();
            } else if (line.startsWith("desc:")) {
                paragraphs.add(line.substring(5).strip());
            }
        }

        ProductCopy build() {
            return new ProductCopy(art, color, wasPrice, tagline, String.join("\n\n", paragraphs),
                    List.copyOf(highlights), List.copyOf(specs));
        }
    }
}
