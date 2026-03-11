package com.peggy.virtualchat.network;

import java.util.List;
import java.util.ArrayList;

public class GeminiRequest {
    public List<Content> contents;

    public GeminiRequest(String text) {
        this.contents = new ArrayList<>();
        Content content = new Content();
        content.parts = new ArrayList<>();

        Part part = new Part();
        part.text = text;
        content.parts.add(part);

        this.contents.add(content);
    }

    public static class Content {
        public List<Part> parts;
    }

    public static class Part {
        public String text;
    }
}