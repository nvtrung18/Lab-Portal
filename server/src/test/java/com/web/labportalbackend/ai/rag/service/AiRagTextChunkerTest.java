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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1198, 1199, 1200, 2399})
    void supplementaryUnicodeCannotBeSplitInsideASurrogatePair(int prefixLength) {
        String content = "đ".repeat(prefixLength) + "🧪" + "ữ".repeat(1205);
        var chunks = chunker.chunk(content);
        assertEquals(content, chunks.stream().map(AiRagTextChunker.Chunk::content).collect(java.util.stream.Collectors.joining()));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().length() <= AiRagTextChunker.MAX_CHUNK_CHARACTERS));
        for (var chunk : chunks) {
            assertTrue(java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(chunk.content()),
                    "Each chunk must remain valid Unicode before database serialization");
        }
    }

    @Test
    void blankContentFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk(" \n\f "));
    }
}
