package com.lzh.radar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lzh.radar.service.LeetCodeCrawlerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
public class AnalyzeController {

    private final LeetCodeCrawlerService leetCodeCrawlerService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnalyzeController(LeetCodeCrawlerService leetCodeCrawlerService) {
        this.leetCodeCrawlerService = leetCodeCrawlerService;
    }

    @PostMapping(value = "/api/analyze/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyze(@RequestBody Map<String, String> body) throws IOException {
        String cookie = body.get("cookie");
        if (cookie == null || !cookie.contains("LEETCODE_SESSION")) {
            SseEmitter bad = new SseEmitter();
            ObjectNode err = mapper.createObjectNode();
            err.put("status", "ERROR");
            err.put("message", "Cookie 中必须包含 LEETCODE_SESSION");
            bad.send(SseEmitter.event().data(err.toString()));
            bad.complete();
            return bad;
        }
        SseEmitter emitter = new SseEmitter(180000L);
        leetCodeCrawlerService.crawl(cookie, emitter);
        return emitter;
    }
}
