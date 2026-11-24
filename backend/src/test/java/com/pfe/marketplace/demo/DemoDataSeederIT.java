package com.pfe.marketplace.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.catalog.service.ImageStorageService;
import com.pfe.marketplace.support.ApiFixtures;
import java.nio.file.Files;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DemoDataSeederIT extends ApiFixtures {

    @Autowired ImageStorageService storage;
    @Autowired Clock clock;

    @Test
    void seedsAConsistentDemoMarketplaceOnce() throws Exception {
        DemoDataSeeder seeder = new DemoDataSeeder(jdbc, passwordEncoder, storage, clock);
        seeder.run(null);
        long ordersAfterFirstRun = count("orders");
        seeder.run(null);

        assertThat(count("orders")).isEqualTo(ordersAfterFirstRun).isGreaterThan(400);
        assertThat(count("products")).isGreaterThanOrEqualTo(120);
        assertThat(count("reviews")).isGreaterThan(50);
        assertThat(jdbc.queryForObject("select count(*) from shop_orders where status = 'RETURNED'", Long.class))
                .isPositive();
        assertThat(jdbc.queryForObject("select count(*) from disputes where status = 'OPEN'", Long.class)).isPositive();
        assertThat(Files.readString(storage.root().resolve("demo/p1-0.svg"))).startsWith("<svg").contains("</svg>");

        // Every product has merchandising copy; about half are on sale.
        String demoProducts = "from products p join shops s on s.id = p.shop_id where s.slug in "
                + "('technova', 'soundwave-audio', 'maison-belle', 'urban-threads', 'glow-beauty', 'peaksport', 'pageturner')";
        assertThat(jdbc.queryForObject("select count(*) " + demoProducts + " and (p.tagline is null "
                + "or jsonb_array_length(p.highlights) < 3 or jsonb_array_length(p.specs) < 2)", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) " + demoProducts + " and p.compare_at_price > p.price",
                Long.class)).isBetween(30L, 60L);
        Long phone = jdbc.queryForObject("select id from products where name = 'Nova X5 Smartphone 128 GB'", Long.class);
        JsonNode product = send(null, get("/api/products/" + phone), null, 200);
        assertThat(product.get("highlights").size()).isGreaterThanOrEqualTo(3);
        assertThat(product.get("description").asText()).contains("AMOLED");

        // Seeded accounts work and the data loads through JPA.
        String customer = login("customer1@markethub.test", "Customer123!");
        JsonNode orders = send(customer, get("/api/orders"), null, 200);
        assertThat(orders.get("totalElements").asLong()).isPositive();
        String seller = login("seller1@markethub.test", "Seller123!");
        JsonNode stats = send(seller, get("/api/seller/stats"), null, 200);
        assertThat(stats.get("grossRevenue").decimalValue()).isPositive();
        assertThat(send(login(DemoDataSeeder.ADMIN_EMAIL, "Admin123!"), get("/api/admin/stats"), null, 200)
                .get("grossMerchandiseValue").decimalValue()).isPositive();

        // Sequences were realigned: new rows can still be inserted.
        register(uniqueEmail("after-seed"), "CUSTOMER", null);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
