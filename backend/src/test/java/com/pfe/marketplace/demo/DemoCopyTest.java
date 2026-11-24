package com.pfe.marketplace.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.pfe.marketplace.catalog.entity.ProductSpec;
import com.pfe.marketplace.demo.DemoCopy.ProductCopy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DemoCopyTest {

    private static final Set<String> PRODUCT_NAMES =
            DemoCatalog.PRODUCTS.stream().map(DemoCatalog.ProductDef::name).collect(Collectors.toSet());

    @Test
    void parsesAProductBlockAndSkipsComments() {
        Map<String, ProductCopy> copy = new HashMap<>();
        DemoCopy.parse("""
                # A comment block without art is ignored
                # Lamp
                art: table-lamp #d6c7a1
                was: 54
                tag: Warm light.
                desc: First paragraph.
                desc: Second paragraph.
                + Oak base
                = Height: 45 cm
                = Bulb: E27: max 40 W
                """, copy);

        assertThat(copy).containsOnlyKeys("Lamp");
        ProductCopy lamp = copy.get("Lamp");
        assertThat(lamp.art()).isEqualTo("table-lamp");
        assertThat(lamp.color()).isEqualTo("#d6c7a1");
        assertThat(lamp.wasPrice()).isEqualByComparingTo(new BigDecimal("54"));
        assertThat(lamp.description()).isEqualTo("First paragraph.\n\nSecond paragraph.");
        assertThat(lamp.highlights()).containsExactly("Oak base");
        assertThat(lamp.specs()).containsExactly(new ProductSpec("Height", "45 cm"), new ProductSpec("Bulb", "E27: max 40 W"));
    }

    @Test
    void everyDemoProductHasCompleteCopyAndAHigherCompareAtPrice() {
        Map<String, ProductCopy> copy = DemoCopy.load();
        assertThat(copy.keySet()).containsAll(PRODUCT_NAMES);
        for (DemoCatalog.ProductDef product : DemoCatalog.PRODUCTS) {
            ProductCopy text = copy.get(product.name());
            assertThat(text.tagline()).as(product.name()).isNotBlank().hasSizeLessThanOrEqualTo(200);
            assertThat(text.description()).as(product.name()).hasSizeGreaterThan(80);
            assertThat(text.highlights()).as(product.name()).hasSizeBetween(3, 8);
            assertThat(text.specs()).as(product.name()).hasSizeGreaterThanOrEqualTo(2);
            if (text.wasPrice() != null) {
                assertThat(text.wasPrice()).as(product.name()).isGreaterThan(BigDecimal.valueOf(product.price()));
            }
        }
    }

    @Test
    void pairingsOnlyReferToDemoProducts() {
        Map<String, List<String>> pairings = DemoCopy.loadPairings();
        assertThat(pairings).isNotEmpty();
        pairings.forEach((product, companions) -> {
            assertThat(PRODUCT_NAMES).as("pairing key").contains(product);
            assertThat(PRODUCT_NAMES).as("companions of " + product).containsAll(companions);
        });
    }

    @Test
    void everyIllustrationKindIsDrawnInBothVariants() {
        String fallbackBox = "M100 120 L150 70 H350 L300 120 Z";
        for (ProductCopy text : DemoCopy.load().values()) {
            for (int variant = 0; variant < 2; variant++) {
                String svg = DemoImages.illustration(text.art(), text.color(), "#3b82f6", variant);
                assertThat(svg).as(text.art()).startsWith("<svg").endsWith("</svg>\n").doesNotContain(fallbackBox);
            }
        }
    }
}
