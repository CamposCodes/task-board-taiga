package com.r2da.taskboard.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileStore {

    private final Path root;

    FileStore(@Value("${app.files-dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public String save(Long projectId, InputStream content) {
        String key = UUID.randomUUID().toString();
        try {
            Files.createDirectories(folder(projectId));
            Files.copy(content, folder(projectId).resolve(key));
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path path(Long projectId, String key) {
        return folder(projectId).resolve(key);
    }

    public void delete(Long projectId, String key) {
        try {
            Files.deleteIfExists(path(projectId, key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void deleteProject(Long projectId) {
        Path folder = folder(projectId);
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path folder(Long projectId) {
        return root.resolve(String.valueOf(projectId));
    }
}
