package com.lzh.radar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LeetCodeCrawlerService {

    private static final String API_URL = "https://leetcode.cn/graphql/";
    private static final int LIMIT = 100;
    private static final int TAG_DISCOVERY_LIMIT = 500;
    private static final long SLEEP_MS = 250L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Integer> TIER_BENCHMARKS = new HashMap<>();
    private static final Map<String, double[]> TAG_WEIGHTS = new HashMap<>();
    private static final double[] DEFAULT_WEIGHT = {0.2, 0.2, 0.1, 0.2, 0.1, 0.2};
    private static final int[] COG_BENCHMARKS = {150, 150, 80, 100, 40, 60};

    static {
        List.of("数组", "字符串", "哈希表", "动态规划", "数学")
                .forEach(t -> TIER_BENCHMARKS.put(t, 60));
        List.of("树", "二叉树", "深度优先搜索", "广度优先搜索", "二分查找", "贪心", "排序",
                "双指针", "矩阵", "栈", "递归", "链表", "队列")
                .forEach(t -> TIER_BENCHMARKS.put(t, 35));
        List.of("并查集", "单调栈", "字典树", "滑动窗口", "图", "设计", "回溯",
                "堆（优先队列）", "前缀和", "位运算", "拓扑排序", "分治", "线段树", "模拟",
                "计数", "有序集合", "快速选择", "归并排序", "最短路", "字符串匹配",
                "组合数学", "数据流", "交互", "扫描线", "状态压缩", "记忆化搜索",
                "双向链表", "二叉搜索树", "平衡二叉树", "后缀数组", "最小生成树", "基环树",
                "堆", "图论", "环检测", "双连通分量", "强连通分量", "欧拉回路")
                .forEach(t -> TIER_BENCHMARKS.put(t, 20));

        // ======= 六大认知维度权重矩阵 - 10大族群分组 [DS, AL, TM, TH, EG, IN] =======
        // 族群 1：基础线性数据结构 [0.7, 0.1, 0.0, 0.0, 0.2, 0.0]
        double[] g1 = {0.7, 0.1, 0.0, 0.0, 0.2, 0.0};
        List.of("array", "linked-list", "stack", "queue", "hash-table",
                "doubly-linked-list", "matrix", "ordered-set", "heap-priority-queue")
                .forEach(t -> TAG_WEIGHTS.put(t, g1));

        // 族群 2：经典树与图论结构 [0.7, 0.2, 0.1, 0.0, 0.0, 0.0]
        double[] g2 = {0.7, 0.2, 0.1, 0.0, 0.0, 0.0};
        List.of("tree", "binary-tree", "binary-search-tree", "graph",
                "minimum-spanning-tree", "functional-graph", "balanced-binary-tree")
                .forEach(t -> TAG_WEIGHTS.put(t, g2));

        // 族群 3：高阶固化模板结构 [0.4, 0.0, 0.6, 0.0, 0.0, 0.0]
        double[] g3 = {0.4, 0.0, 0.6, 0.0, 0.0, 0.0};
        List.of("trie", "segment-tree", "binary-indexed-tree", "union-find",
                "monotonic-stack", "monotonic-queue", "suffix-array",
                "strongly-connected-component", "biconnected-component", "quickselect",
                "merge-sort", "counting-sort", "bucket-sort", "radix-sort", "counting")
                .forEach(t -> TAG_WEIGHTS.put(t, g3));

        // 族群 4：经典搜索与图遍历算法 [0.1, 0.6, 0.3, 0.0, 0.0, 0.0]
        double[] g4 = {0.1, 0.6, 0.3, 0.0, 0.0, 0.0};
        List.of("depth-first-search", "breadth-first-search", "binary-search",
                "sorting", "backtracking", "divide-and-conquer", "recursion",
                "topological-sort", "shortest-path", "eulerian-circuit")
                .forEach(t -> TAG_WEIGHTS.put(t, g4));

        // 族群 5：动态规划与记忆化递推 [0.1, 0.4, 0.0, 0.5, 0.0, 0.0]
        double[] g5 = {0.1, 0.4, 0.0, 0.5, 0.0, 0.0};
        List.of("dynamic-programming", "memoization", "bitmask")
                .forEach(t -> TAG_WEIGHTS.put(t, g5));

        // 族群 6：双指针与滑窗机制 [0.1, 0.5, 0.0, 0.2, 0.0, 0.2]
        double[] g6 = {0.1, 0.5, 0.0, 0.2, 0.0, 0.2};
        List.of("two-pointers", "sliding-window", "prefix-sum")
                .forEach(t -> TAG_WEIGHTS.put(t, g6));

        // 族群 7：纯数学分析与严密数理 [0.0, 0.1, 0.0, 0.8, 0.0, 0.1]
        double[] g7 = {0.0, 0.1, 0.0, 0.8, 0.0, 0.1};
        List.of("math", "bit-manipulation", "combinatorics", "number-theory",
                "hash-function")
                .forEach(t -> TAG_WEIGHTS.put(t, g7));

        // 族群 8：算法直觉与启发式探路 [0.0, 0.0, 0.0, 0.3, 0.0, 0.7]
        double[] g8 = {0.0, 0.0, 0.0, 0.3, 0.0, 0.7};
        List.of("greedy", "brainteaser", "geometry", "game-theory",
                "interactive", "random", "randomized", "line-sweep",
                "rejection-sampling", "reservoir-sampling")
                .forEach(t -> TAG_WEIGHTS.put(t, g8));

        // 族群 9：系统设计、模拟与底层工程 [0.0, 0.0, 0.0, 0.0, 1.0, 0.0]
        double[] g9 = {0.0, 0.0, 0.0, 0.0, 1.0, 0.0};
        List.of("design", "concurrency", "simulation", "database",
                "shell", "data-stream", "iterator")
                .forEach(t -> TAG_WEIGHTS.put(t, g9));

        // 族群 10：文本特殊处理与字符串匹配 [0.4, 0.2, 0.2, 0.0, 0.2, 0.0]
        double[] g10 = {0.4, 0.2, 0.2, 0.0, 0.2, 0.0};
        List.of("string", "string-matching", "rolling-hash")
                .forEach(t -> TAG_WEIGHTS.put(t, g10));
    }

    private static class TagStat {
        String name;
        String slug;
        int total;
        int easy;
        int medium;
        int hard;
        double mastery;
    }

    public void crawl(String cookie, SseEmitter emitter) {
        new Thread(() -> doCrawl(cookie, emitter)).start();
    }

    private void doCrawl(String cookie, SseEmitter emitter) {
        HttpClient client = HttpClient.newHttpClient();
        Map<String, TagStat> tagMap = new LinkedHashMap<>();
        double[] cogRaw = new double[6];
        int totalQuestions = 0;

        try {
            int skip = 0;
            boolean hasMore = true;
            int loaded = 0;

            while (hasMore) {
                String payload = """
                {
                  "operationName": "problemsetQuestionList",
                  "variables": {
                    "categorySlug": "",
                    "skip": %d,
                    "limit": %d,
                    "filters": { "status": "AC" }
                  },
                  "query": "query problemsetQuestionList($categorySlug: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) { problemsetQuestionList(categorySlug: $categorySlug, limit: $limit, skip: $skip, filters: $filters) { hasMore total questions { difficulty topicTags { name nameTranslated slug } } } }"
                }
                """.formatted(skip, LIMIT);

                HttpRequest request = buildRequest(cookie, payload);
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    emitError(emitter, "凭证失效或请求受限 [HTTP " + response.statusCode() + ": " + response.body() + "]");
                    return;
                }

                JsonNode root = MAPPER.readTree(response.body());
                if (root.has("errors")) {
                    emitError(emitter, "凭证失效或请求受限 [errors: " + root.get("errors") + "]");
                    return;
                }

                JsonNode problemset = root.at("/data/problemsetQuestionList");
                hasMore = problemset.get("hasMore").asBoolean();
                totalQuestions = problemset.get("total").asInt();

                JsonNode questions = problemset.get("questions");
                for (JsonNode q : questions) {
                    loaded++;
                    String difficulty = q.get("difficulty").asText();
                    double qScore = switch (difficulty) {
                        case "HARD" -> 5.0;
                        case "MEDIUM" -> 2.5;
                        default -> 1.0;
                    };

                    JsonNode tags = q.get("topicTags");
                    if (tags != null && tags.isArray()) {
                        for (JsonNode t : tags) {
                            String tagName = t.get("nameTranslated").isNull()
                                    ? t.get("name").asText()
                                    : t.get("nameTranslated").asText();
                            if (tagName == null || tagName.isEmpty()) continue;
                            String tagSlug = t.get("slug").asText();

                            TagStat stat = tagMap.computeIfAbsent(tagName, k -> {
                                TagStat s = new TagStat();
                                s.name = k;
                                s.slug = tagSlug;
                                return s;
                            });
                            if (stat.slug == null || stat.slug.isEmpty()) stat.slug = tagSlug;
                            stat.total++;
                            if ("EASY".equals(difficulty)) stat.easy++;
                            else if ("MEDIUM".equals(difficulty)) stat.medium++;
                            else if ("HARD".equals(difficulty)) stat.hard++;

                            double[] weights = TAG_WEIGHTS.getOrDefault(tagSlug, DEFAULT_WEIGHT);
                            for (int d = 0; d < 6; d++) {
                                cogRaw[d] += qScore * weights[d];
                            }
                        }
                    }
                }

                ObjectNode progress = MAPPER.createObjectNode();
                progress.put("status", "RUNNING");
                progress.put("message", "正在统计已解题目... (" + loaded + "/" + totalQuestions + ")");
                progress.put("currentStep", loaded);
                progress.put("totalSteps", totalQuestions);
                emitter.send(SseEmitter.event().data(progress.toString()));

                skip += LIMIT;
                Thread.sleep(SLEEP_MS);
            }

            Set<String> allTagNames = new LinkedHashSet<>(tagMap.keySet());
            ObjectNode progress2 = MAPPER.createObjectNode();
            progress2.put("status", "RUNNING");
            progress2.put("message", "正在收集全部标签...");
            progress2.put("currentStep", 0);
            progress2.put("totalSteps", 1);
            emitter.send(SseEmitter.event().data(progress2.toString()));

            skip = 0;
            hasMore = true;
            int pagesDone = 0;

            while (hasMore) {
                String payload = """
                {
                  "operationName": "problemsetQuestionList",
                  "variables": {
                    "categorySlug": "",
                    "skip": %d,
                    "limit": %d,
                    "filters": {}
                  },
                  "query": "query problemsetQuestionList($categorySlug: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) { problemsetQuestionList(categorySlug: $categorySlug, limit: $limit, skip: $skip, filters: $filters) { hasMore questions { topicTags { name nameTranslated } } } }"
                }
                """.formatted(skip, TAG_DISCOVERY_LIMIT);

                HttpRequest request = buildRequest(cookie, payload);
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    emitError(emitter, "凭证失效或请求受限 [HTTP " + response.statusCode() + ": " + response.body() + "]");
                    return;
                }

                JsonNode root = MAPPER.readTree(response.body());
                if (root.has("errors")) {
                    emitError(emitter, "凭证失效或请求受限 [errors: " + root.get("errors") + "]");
                    return;
                }

                JsonNode problemset = root.at("/data/problemsetQuestionList");
                hasMore = problemset.get("hasMore").asBoolean();

                JsonNode questions = problemset.get("questions");
                for (JsonNode q : questions) {
                    JsonNode tags = q.get("topicTags");
                    if (tags != null && tags.isArray()) {
                        for (JsonNode t : tags) {
                            String tagName = t.get("nameTranslated").isNull()
                                    ? t.get("name").asText()
                                    : t.get("nameTranslated").asText();
                            if (tagName != null && !tagName.isEmpty()) {
                                allTagNames.add(tagName);
                            }
                        }
                    }
                }

                pagesDone++;
                ObjectNode progress3 = MAPPER.createObjectNode();
                progress3.put("status", "RUNNING");
                progress3.put("message", "正在收集全部标签... (第" + pagesDone + "页)");
                progress3.put("currentStep", 1);
                progress3.put("totalSteps", 1);
                emitter.send(SseEmitter.event().data(progress3.toString()));

                skip += TAG_DISCOVERY_LIMIT;
                Thread.sleep(SLEEP_MS);
            }

            for (String name : allTagNames) {
                tagMap.computeIfAbsent(name, k -> {
                    TagStat s = new TagStat();
                    s.name = k;
                    return s;
                });
            }

            for (TagStat s : tagMap.values()) {
                s.mastery = computeMastery(s);
            }

            ObjectNode cognitive = MAPPER.createObjectNode();
            for (int d = 0; d < 6; d++) {
                double lambda = Math.log(2) / COG_BENCHMARKS[d];
                double mastery = 100.0 * (1.0 - Math.exp(-lambda * cogRaw[d]));
                cognitive.put(String.valueOf(d), Math.round(mastery * 10.0) / 10.0);
            }

            List<TagStat> sorted = new ArrayList<>(tagMap.values());
            sorted.sort((a, b) -> Integer.compare(b.total, a.total));

            ArrayNode tagsArr = MAPPER.createArrayNode();
            for (TagStat s : sorted) {
                ObjectNode tagNode = MAPPER.createObjectNode();
                tagNode.put("name", s.name);
                tagNode.put("total", s.total);
                tagNode.put("easy", s.easy);
                tagNode.put("medium", s.medium);
                tagNode.put("hard", s.hard);
                tagNode.put("mastery", s.mastery);
                tagsArr.add(tagNode);
            }

            ObjectNode data = MAPPER.createObjectNode();
            data.put("total", totalQuestions);
            data.set("tags", tagsArr);
            data.set("cognitive", cognitive);

            ObjectNode success = MAPPER.createObjectNode();
            success.put("status", "SUCCESS");
            success.set("data", data);
            emitter.send(SseEmitter.event().data(success.toString()));
            emitter.complete();

        } catch (IOException e) {
            emitError(emitter, "网络请求异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitError(emitter, "请求被中断");
        } catch (Exception e) {
            emitError(emitter, "凭证失效或请求受限: " + e.getMessage());
        }
    }

    private double computeMastery(TagStat s) {
        double score = s.easy * 1.0 + s.medium * 2.5 + s.hard * 5.0;
        if (score == 0) return 0.0;
        int benchmark = TIER_BENCHMARKS.getOrDefault(s.name, 8);
        double lambda = Math.log(2) / benchmark;
        double mastery = 100.0 * (1.0 - Math.exp(-lambda * score));
        return Math.round(mastery * 10.0) / 10.0;
    }

    private HttpRequest buildRequest(String cookie, String payload) {
        return HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Cookie", cookie)
                .header("Referer", "https://leetcode.cn/")
                .header("Origin", "https://leetcode.cn")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
    }

    private void emitError(SseEmitter emitter, String message) {
        try {
            ObjectNode err = MAPPER.createObjectNode();
            err.put("status", "ERROR");
            err.put("message", message);
            emitter.send(SseEmitter.event().data(err.toString()));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }
}
