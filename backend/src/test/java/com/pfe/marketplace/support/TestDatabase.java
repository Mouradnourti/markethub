package com.pfe.marketplace.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL per test JVM. Uses Testcontainers when Docker is available (CI), otherwise an embedded
 * PostgreSQL binary so the suite also runs on a laptop without Docker. Both are real PostgreSQL 16.
 */
public final class TestDatabase {

    private static final TestDatabase INSTANCE = start();

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private TestDatabase(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public static TestDatabase get() {
        return INSTANCE;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    private static TestDatabase start() {
        return dockerAvailable() ? startContainer() : startEmbedded();
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    @SuppressWarnings("resource")
    private static TestDatabase startContainer() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        return new TestDatabase(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    private static TestDatabase startEmbedded() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> closeQuietly(postgres)));
            return new TestDatabase(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "postgres");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start embedded PostgreSQL", e);
        }
    }

    private static void closeQuietly(EmbeddedPostgres postgres) {
        try {
            postgres.close();
        } catch (IOException ignored) {
            // JVM is shutting down
        }
    }
}
