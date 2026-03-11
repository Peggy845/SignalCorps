package com.peggy.virtualchat.network;

import java.util.List;

public class GeminiResponse {
    public List<Candidate> candidates;

    public static class Candidate {
        public Content content;
    }

    public static class Content {
        public List<Part> parts;
    }

    public static class Part {
        public String text;
    }

    // 提煉核心資訊的防禦性 Getter
    public String getResponseText() {
        if (candidates != null && !candidates.isEmpty() &&
                candidates.get(0).content != null &&
                candidates.get(0).content.parts != null &&
                !candidates.get(0).content.parts.isEmpty()) {
            return candidates.get(0).content.parts.get(0).text;
        }
        return null; // 若結構破裂，直接回傳 null 交由上層處理
    }
}