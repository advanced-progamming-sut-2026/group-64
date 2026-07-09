package ir.sharif.pvz.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void hashesAreDeterministicAndOneWay() {
        String hash = PasswordHasher.sha256("Secret!123");
        assertEquals(hash, PasswordHasher.sha256("Secret!123"));
        assertNotEquals("Secret!123", hash);
        assertEquals(64, hash.length());
    }

    @Test
    void matchesComparesRawAgainstHash() {
        String hash = PasswordHasher.sha256("Secret!123");
        assertTrue(PasswordHasher.matches("Secret!123", hash));
        assertFalse(PasswordHasher.matches("wrong", hash));
    }
}
