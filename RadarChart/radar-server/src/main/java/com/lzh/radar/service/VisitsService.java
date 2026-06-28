package com.lzh.radar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class VisitsService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Path usagePath;
    private final Path pagePath;
    private final Object lock = new Object();

    public VisitsService(@Value("${app.sources.path:../../Sources}") String sourcesPath) {
        Path dir = Path.of(sourcesPath, "posts");
        this.usagePath = dir.resolve("usage.yml");
        this.pagePath = dir.resolve("page.yml");
    }

    public void incrementUsage() {
        increment(usagePath);
    }

    public void incrementPage() {
        increment(pagePath);
    }

    public Map<String, Integer> usageStats() {
        return readStats(usagePath);
    }

    public Map<String, Integer> pageStats() {
        return readStats(pagePath);
    }

    private void increment(Path path) {
        synchronized (lock) {
            try {
                Files.createDirectories(path.getParent());
                Map<String, Object> data = readRaw(path);
                String today = LocalDate.now().format(FMT);
                int count = data.get(today) instanceof Number ? ((Number) data.get(today)).intValue() : 0;
                data.put(today, count + 1);
                Files.writeString(path, new Yaml().dumpAsMap(data), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRaw(Path path) {
        try {
            if (!Files.exists(path)) return new LinkedHashMap<>();
            Map<String, Object> raw = new Yaml().load(Files.readString(path, StandardCharsets.UTF_8));
            return raw != null ? raw : new LinkedHashMap<>();
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> readStats(Path path) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Object> raw = readRaw(path);
        raw.forEach((k, v) -> result.put(k, v instanceof Number ? ((Number) v).intValue() : 0));
        return result;
    }
}
