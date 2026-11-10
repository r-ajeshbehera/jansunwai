package com.jansunwai.service;

import com.jansunwai.config.AppProperties;
import com.jansunwai.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.UUID;

/**
 * Stores uploads on local disk (a Docker volume in production) under RANDOM names, outside the web root.
 * The real file type is detected from the first bytes ("magic numbers"); the client's Content-Type is not trusted.
 */
@Service
public class FileStorageService {
    private final Path root;

    public FileStorageService(AppProperties props) {
        this.root = Paths.get(props.uploads().directory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload directory " + root, e);
        }
    }

    public record Stored(String storedName, String contentType) { }

    /** Returns the detected MIME type, or throws if the bytes are not JPEG/PNG/WEBP. */
    public static String detectImageType(byte[] head) {
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (head.length >= 8 && Arrays.equals(Arrays.copyOf(head, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return "image/png";
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') return "image/webp";
        throw new BusinessRuleException("UNSUPPORTED_FILE", "Only JPEG, PNG or WEBP photos are allowed");
    }

    public Stored store(byte[] bytes) {
        String type = detectImageType(bytes);
        String ext = switch (type) { case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".jpg"; };
        String name = UUID.randomUUID() + ext;
        try {
            Files.write(root.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        }
        return new Stored(name, type);
    }

    public InputStream open(String storedName) throws IOException {
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Invalid file name");   // path traversal guard
        return Files.newInputStream(p);
    }

    public void deleteQuietly(String storedName) {
        try {
            Files.deleteIfExists(root.resolve(storedName).normalize());
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
