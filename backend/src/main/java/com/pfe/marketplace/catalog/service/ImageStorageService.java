package com.pfe.marketplace.catalog.service;

import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Stores product images on the local disk (a Docker volume in production) and returns their public URL. */
@Service
public class ImageStorageService {

    public static final String PUBLIC_PREFIX = "/uploads/";

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");

    private final Path root;

    public ImageStorageService(AppProperties properties) {
        this.root = Path.of(properties.uploads().dir()).toAbsolutePath().normalize();
    }

    public String storeProductImage(MultipartFile file) {
        String extension = EXTENSIONS.get(file.getContentType());
        if (file.isEmpty() || extension == null) {
            throw new BusinessException("Only non-empty JPEG, PNG, WebP or GIF images are accepted");
        }
        try (InputStream in = file.getInputStream()) {
            return store("products", extension, in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store image", e);
        }
    }

    /** Writes raw bytes (used by the demo seeder for generated SVG placeholders). */
    public String storeBytes(String folder, String extension, byte[] content) {
        try (InputStream in = new java.io.ByteArrayInputStream(content)) {
            return store(folder, extension, in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        }
    }

    public Path root() {
        return root;
    }

    private String store(String folder, String extension, InputStream in) throws IOException {
        Path directory = root.resolve(folder);
        Files.createDirectories(directory);
        String filename = UUID.randomUUID() + "." + extension;
        Files.copy(in, directory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return PUBLIC_PREFIX + folder + "/" + filename;
    }
}
