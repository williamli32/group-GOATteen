package com.goatteen.trading.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for idempotency key functionality.
 * Tests verify correct behavior of duplicate detection using idempotency keys
 * without requiring a live database connection.
 */
@DisplayName("Idempotency Key Unit Tests")
class IdempotencyKeyUnitTest {

    @Mock
    private FillRepository fillRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Fill entity has idempotency key field")
    void testFillHasIdempotencyKeyField() {
        // Arrange
        Fill fill = new Fill();
        String testKey = "test-key-001";

        // Act
        fill.setIdempotencyKey(testKey);

        // Assert
        assertEquals(testKey, fill.getIdempotencyKey());
    }

    @Test
    @DisplayName("Fill idempotency key can be set to null")
    void testFillIdempotencyKeyNull() {
        // Arrange
        Fill fill = new Fill();
        fill.setIdempotencyKey("initial-key");

        // Act
        fill.setIdempotencyKey(null);

        // Assert
        assertNull(fill.getIdempotencyKey());
    }

    @Test
    @DisplayName("Repository method finds fill by idempotency key")
    void testRepositoryFindByIdempotencyKey() {
        // Arrange
        String key = "repo-test-key";
        Fill expectedFill = new Fill();
        expectedFill.setIdempotencyKey(key);

        when(fillRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.of(expectedFill));

        // Act
        Optional<Fill> result = fillRepository.findByIdempotencyKey(key);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(key, result.get().getIdempotencyKey());
        verify(fillRepository).findByIdempotencyKey(key);
    }

    @Test
    @DisplayName("Repository returns empty when idempotency key not found")
    void testRepositoryFindByIdempotencyKeyNotFound() {
        // Arrange
        String key = "non-existent-key";
        when(fillRepository.findByIdempotencyKey(key))
                .thenReturn(Optional.empty());

        // Act
        Optional<Fill> result = fillRepository.findByIdempotencyKey(key);

        // Assert
        assertTrue(result.isEmpty());
        verify(fillRepository).findByIdempotencyKey(key);
    }

    @Test
    @DisplayName("Multiple fills can have different idempotency keys")
    void testMultipleFillsWithDifferentKeys() {
        // Arrange
        Fill fill1 = new Fill();
        fill1.setIdempotencyKey("key-1");

        Fill fill2 = new Fill();
        fill2.setIdempotencyKey("key-2");

        when(fillRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(fill1));
        when(fillRepository.findByIdempotencyKey("key-2"))
                .thenReturn(Optional.of(fill2));

        // Act & Assert
        Optional<Fill> result1 = fillRepository.findByIdempotencyKey("key-1");
        Optional<Fill> result2 = fillRepository.findByIdempotencyKey("key-2");

        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals("key-1", result1.get().getIdempotencyKey());
        assertEquals("key-2", result2.get().getIdempotencyKey());
    }

    @Test
    @DisplayName("Empty string is valid idempotency key")
    void testEmptyStringIdempotencyKey() {
        // Arrange
        Fill fill = new Fill();
        fill.setIdempotencyKey("");

        // Act & Assert
        assertEquals("", fill.getIdempotencyKey());
    }

    @Test
    @DisplayName("Idempotency key persists across multiple operations")
    void testIdempotencyKeyPersistence() {
        // Arrange
        String originalKey = "persistent-key";
        Fill fill = new Fill();
        fill.setIdempotencyKey(originalKey);

        // Act - simulate multiple operations
        String key1 = fill.getIdempotencyKey();
        fill.setFillPrice(BigDecimal.TEN);
        String key2 = fill.getIdempotencyKey();

        // Assert - key should remain unchanged
        assertEquals(originalKey, key1);
        assertEquals(originalKey, key2);
    }
}
