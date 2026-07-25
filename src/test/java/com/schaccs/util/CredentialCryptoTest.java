package com.schaccs.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialCryptoTest {

    @Test
    void decryptRejectsInvalidCiphertext() {
        assertThrows(IllegalArgumentException.class, () -> CredentialCrypto.decrypt("not-valid-ciphertext"));
    }
}
