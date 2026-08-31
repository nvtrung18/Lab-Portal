package com.web.labportalbackend.ai.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiRagTextChunkerTest {

    private final AiRagTextChunker chunker = new AiRagTextChunker();

    @Test
    void normalizesTextAndPreservesFormFeedPageMetadata() {
        List<AiRagTextChunker.Chunk> chunks = chunker.chunk(" First  paragraph.\r\n\r\nSecond.\fPage two. ");

        assertEquals(2, chunks.size());
        assertEquals(0, chunks.getFirst().index());
        assertEquals(1, chunks.getFirst().pageNumber());
        assertEquals("First paragraph.\nSecond.", chunks.getFirst().content());
        assertEquals(2, chunks.getLast().pageNumber());
        assertEquals("Page two.", chunks.getLast().content());
    }

    @Test
    void longTextIsSplitIntoBoundedNonEmptyChunks() {
        String content = "word ".repeat(800);

        List<AiRagTextChunker.Chunk> chunks = chunker.chunk(content);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> !chunk.content().isBlank()
                && chunk.content().length() <= AiRagTextChunker.MAX_CHUNK_CHARACTERS));
    }

    @Test
    void blankContentFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk(" \n\f "));
    }
}
