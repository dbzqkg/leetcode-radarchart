package com.lzh.radar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
public class InfoController {

    private final Path configPath;
    private final Path noticePath;
    private final ObjectMapper mapper = new ObjectMapper();

    public InfoController(@Value("${app.sources.path:../../Sources}") String sourcesPath) {
        this.configPath = Path.of(sourcesPath, "posts", "application.yml");
        this.noticePath = Path.of(sourcesPath, "posts", "须知.md");
    }

    @GetMapping(value = "/api/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public String info() {
        ObjectNode node = mapper.createObjectNode();
        node.put("updatetime", readUpdateTime());
        return node.toString();
    }

    @GetMapping(value = "/api/notice", produces = MediaType.APPLICATION_JSON_VALUE)
    public String notice() {
        ObjectNode node = mapper.createObjectNode();
        node.put("content", readNotice());
        return node.toString();
    }

    @SuppressWarnings("unchecked")
    private String readUpdateTime() {
        try {
            byte[] bytes = Files.readAllBytes(configPath);
            String content;
            try {
                content = new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                content = new String(bytes, Charset.forName("GBK"));
            }
            Map<String, Object> yaml = new Yaml().load(content);
            return yaml != null ? String.valueOf(yaml.getOrDefault("updatetime", "未知")) : "未知";
        } catch (IOException e) {
            return "未知";
        }
    }

    private String readNotice() {
        try {
            byte[] bytes = Files.readAllBytes(noticePath);
            try {
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return new String(bytes, Charset.forName("GBK"));
            }
        } catch (IOException e) {
            return "";
        }
    }
}
