package com.goatteen.trading.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IdempotencyService.
 * 
 * Tests the in-memory tracking of fill executions using idempotency keys.
 * 
 * Note: This uses in-memory storage. For production systems that require
 * persistence across service restarts, this should be backed by a database
 * or distributed cache (Redis).
 */
@DisplayName("IdempotencyService Tests")
class IdempotencyServiceTest {

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService();
    }

    @Test
    @DisplayName("Should return null for non-existent idempotency key")
    void testGetFillId_NonExistent_ReturnsNull() {
        Long result = service.getFillIdByIdempotencyKey("unknown-key");
        assertNull(result);
    }

    @Test
    @DisplayName("Should record and retrieve fill execution by idempotency key")
    void testRecordAndRetrieveFill() {
        String idempotencyKey = "order-uuid-12345";
        Long fillId = 100L;

        // Initially should not exist
        assertNull(service.getFillIdByIdempotencyKey(idempotencyKey));

        // Record the fill
        service.recordFillExecution(idempotencyKey, fillId);

        // Should now retrieve the same fill ID
        assertEquals(fillId, service.getFillIdByIdempotencyKey(idempotencyKey));
    }

    @Test
    @DisplayName("Should handle null idempotency key gracefully")
    void testRecordFill_NullKey_DoesNotCrash() {
        // Should not throw exception
        service.recordFillExecution(null, 100L);

        // Retrieval with null should also not crash
        assertNull(service.getFillIdByIdempotencyKey(null));
    }

    @Test
    @DisplayName("Should handle blank idempotency key gracefully")
    void testRecordFill_BlankKey_DoesNotCrash() {
        // Should not throw exception
        service.recordFillExecution("", 100L);
        service.recordFillExecution("   ", 101L);

        // Retrieval should return null
        assertNull(service.getFillIdByIdempotencyKey(""));
    }

    @Test
    @DisplayName("Should handle null fill ID gracefully")
    void testRecordFill_NullFillId_DoesNotCrash() {
        String idempotencyKey = "order-uuid";

        // Recording with null fill ID should not crash
        service.recordFillExecution(idempotencyKey, null);

        // Retrieval should return null
        assertNull(service.getFillIdByIdempotencyKey(idempotencyKey));
    }

    @Test
    @DisplayName("Should track multiple independent fills")
    void testTrackMultipleFills() {
        // Record multiple fills with different keys
        service.recordFillExecution("key-1", 100L);
        service.recordFillExecution("key-2", 101L);
        service.recordFillExecution("key-3", 102L);

        // Each should be retrievable independently
        assertEquals(100L, service.getFillIdByIdempotencyKey("key-1"));
        assertEquals(101L, service.getFillIdByIdempotencyKey("key-2"));
        assertEquals(102L, service.getFillIdByIdempotencyKey("key-3"));

        // Non-existent keys should still return null
        assertNull(service.getFillIdByIdempotencyKey("key-4"));
    }

    @Test
    @DisplayName("Should update fill ID if same key is recorded again")
    void testRecordFill_UpdatesOnDuplicate() {
        String idempotencyKey = "order-uuid";

        // Record first fill
        service.recordFillExecution(idempotencyKey, 100L);
        assertEquals(100L, service.getFillIdByIdempotencyKey(idempotencyKey));

        // Record again with same key but different fill ID
        service.recordFillExecution(idempotencyKey, 200L);

        // Should return the latest fill ID
        assertEquals(200L, service.getFillIdByIdempotencyKey(idempotencyKey));
    }

    @Test
    @DisplayName("Should report correct number of tracked keys")
    void testGetTrackedKeyCount() {
        assertEquals(0, service.getTrackedKeyCount());

        service.recordFillExecution("key-1", 100L);
        assertEquals(1, service.getTrackedKeyCount());

        service.recordFillExecution("key-2", 101L);
        assertEquals(2, service.getTrackedKeyCount());

        service.recordFillExecution("key-3", 102L);
        assertEquals(3, service.getTrackedKeyCount());
    }

    @Test
    @DisplayName("Should not count null/blank keys in tracked key count")
    void testGetTrackedKeyCount_IgnoresNullKeys() {
        service.recordFillExecution(null, 100L);
        service.recordFillExecution("", 101L);
        service.recordFillExecution("   ", 102L);

        // Count should be 0 since null/blank keys are not recorded
        assertEquals(0, service.getTrackedKeyCount());
    }

    @Test
    @DisplayName("Should clear all tracked keys")
    void testClearAll() {
        // Record some fills
        service.recordFillExecution("key-1", 100L);
        service.recordFillExecution("key-2", 101L);
        service.recordFillExecution("key-3", 102L);

        assertEquals(3, service.getTrackedKeyCount());

        // Clear all
        service.clearAll();

        // All should be gone
        assertEquals(0, service.getTrackedKeyCount());
        assertNull(service.getFillIdByIdempotencyKey("key-1"));
        assertNull(service.getFillIdByIdempotencyKey("key-2"));
        assertNull(service.getFillIdByIdempotencyKey("key-3"));
    }

    @Test
    @DisplayName("Should support high-volume concurrent recordings")
    void testConcurrentRecording() throws InterruptedException {
        int threadCount = 10;
        int recordsPerThread = 100;
        int totalExpected = threadCount * recordsPerThread;

        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    String key = "key-" + threadId + "-" + i;
                    long fillId = (long) (threadId * 1000 + i);
                    service.recordFillExecution(key, fillId);
                }
            });
            threads[t].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify total count (ConcurrentHashMap should handle this safely)
        assertEquals(totalExpected, service.getTrackedKeyCount());
    }

    @Test
    @DisplayName("Should retrieve correct fill after concurrent updates")
    void testConcurrentRetrievalAfterRecording() throws InterruptedException {
        String targetKey = "target-key";
        long expectedFillId = 999L;

        // Record the fill from main thread
        service.recordFillExecution(targetKey, expectedFillId);

        // Spawn multiple reader threads
        Thread[] readers = new Thread[5];
        for (int i = 0; i < 5; i++) {
            readers[i] = new Thread(() -> {
                Long retrievedId = service.getFillIdByIdempotencyKey(targetKey);
                assertEquals(expectedFillId, retrievedId,
                        "Concurrent read should see same fill ID");
            });
            readers[i].start();
        }

        // Wait for all readers
        for (Thread reader : readers) {
            reader.join();
        }
    }

    @Test
    @DisplayName("Should handle very large fill IDs")
    void testLargeFillIds() {
        long maxLongValue = Long.MAX_VALUE;
        service.recordFillExecution("large-key", maxLongValue);

        assertEquals(maxLongValue, service.getFillIdByIdempotencyKey("large-key"));
    }

    @Test
    @DisplayName("Should handle special characters in idempotency keys")
    void testSpecialCharactersInKeys() {
        String[] specialKeys = {
            "key-with-dashes",
            "key_with_underscores",
            "key.with.dots",
            "key/with/slashes",
            "key:with:colons",
            "key|with|pipes",
            "uuid-550e8400-e29b-41d4-a716-446655440000"
        };

        for (int i = 0; i < specialKeys.length; i++) {
            service.recordFillExecution(specialKeys[i], (long) i);
        }

        for (int i = 0; i < specialKeys.length; i++) {
            assertEquals((long) i, service.getFillIdByIdempotencyKey(specialKeys[i]),
                    "Should handle special characters: " + specialKeys[i]);
        }
    }
}
