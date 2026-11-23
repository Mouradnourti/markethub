package com.pfe.marketplace;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.File;
import java.io.IOException;

/**
 * Runs the backend with an embedded PostgreSQL 16 (data kept in {@code backend/.local-pg}) and the demo seed,
 * for machines without Docker: {@code mvn spring-boot:test-run}. Redis is replaced by an in-memory cache.
 */
public final class LocalDevApplication {

    private LocalDevApplication() {
    }

    public static void main(String[] args) throws IOException {
        File dataDirectory = new File(".local-pg").getAbsoluteFile();
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setPort(Integer.parseInt(System.getProperty("local.pg.port", "54329")))
                .setDataDirectory(dataDirectory)
                .setCleanDataDirectory(false)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (IOException ignored) {
                // shutting down
            }
        }));
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        if (System.getProperty("spring.profiles.active") == null) {
            System.setProperty("spring.profiles.active", "local,demo");
        }
        MarketplaceApplication.main(args);
    }
}
