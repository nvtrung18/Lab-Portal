package com.web.labportalbackend.ai.rag.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiRagTextChunker {

    static final int MAX_CHUNK_CHARACTERS = 1200;
    static final int MAX_CHUNKS = 512;

    public List<Chunk> chunk(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("RAG document content is required");
        }
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] pages = normalized.split("\f", -1);
        List<Chunk> chunks = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pages.length; pageIndex++) {
            appendPage(chunks, pages[pageIndex].strip(), pageIndex + 1);
        }
        if (chunks.isEmpty() || chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("RAG document cannot be represented by the bounded chunk policy");
        }
        return List.copyOf(chunks);
    }

    private static void appendPage(List<Chunk> chunks, String page, int pageNumber) {
        if (page.isEmpty()) {
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String paragraph : page.split("\\n\\s*\\n+")) {
            String normalized = paragraph.replaceAll("\\s+", " ").strip();
            if (normalized.isEmpty()) {
                continue;
            }
            for (String piece : splitBounded(normalized)) {
                if (!current.isEmpty() && current.length() + 1 + piece.length() > MAX_CHUNK_CHARACTERS) {
                    addChunk(chunks, current.toString(), pageNumber);
                    current.setLength(0);
                }
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(piece);
            }
        }
        if (!current.isEmpty()) {
            addChunk(chunks, current.toString(), pageNumber);
        }
    }

    private static List<String> splitBounded(String value) {
        List<String> pieces = new ArrayList<>();
        int offset = 0;
        while (offset < value.length()) {
            int end = Math.min(offset + MAX_CHUNK_CHARACTERS, value.length());
            if (end < value.length()) {
                int whitespace = value.lastIndexOf(' ', end);
                if (whitespace > offset) {
                    end = whitespace;
                }
            }
            pieces.add(value.substring(offset, end).strip());
            offset = end;
            while (offset < value.length() && Character.isWhitespace(value.charAt(offset))) {
                offset++;
            }
        }
        return pieces;
    }

    private static void addChunk(List<Chunk> chunks, String content, int pageNumber) {
        chunks.add(new Chunk(chunks.size(), pageNumber, content));
    }

    public record Chunk(int index, int pageNumber, String content) {
        public Chunk {
            if (index < 0 || pageNumber <= 0 || content == null || content.isBlank()
                    || content.length() > MAX_CHUNK_CHARACTERS) {
                throw new IllegalArgumentException("RAG chunk is invalid");
            }
        }
    }
}
