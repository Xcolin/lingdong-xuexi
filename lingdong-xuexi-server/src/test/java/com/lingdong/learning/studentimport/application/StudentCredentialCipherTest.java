package com.lingdong.learning.studentimport.application;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentCredentialCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(
            "student-import-test-key-32-byte!".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Test
    void encryptsWithRandomNonceAndAuthenticatedContext() {
        StudentCredentialCipher cipher = new StudentCredentialCipher("v1", KEY);

        EncryptedStudentCredential first = cipher.encrypt("2468", "execution:1:row:2");
        EncryptedStudentCredential second = cipher.encrypt("2468", "execution:1:row:2");

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(cipher.decrypt(first, "execution:1:row:2")).isEqualTo("2468");
        assertThatThrownBy(() -> cipher.decrypt(first, "execution:1:row:3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    void rejectsMissingOrShortKeyAndTamperedCiphertext() {
        assertThatThrownBy(() -> new StudentCredentialCipher("v1", ""))
                .isInstanceOf(IllegalStateException.class);
        String shortKey = Base64.getEncoder().encodeToString("short".getBytes());
        assertThatThrownBy(() -> new StudentCredentialCipher("v1", shortKey))
                .isInstanceOf(IllegalStateException.class);

        StudentCredentialCipher cipher = new StudentCredentialCipher("v1", KEY);
        EncryptedStudentCredential encrypted = cipher.encrypt("1357", "execution:2:row:4");
        byte[] tampered = encrypted.ciphertext().clone();
        tampered[0] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(new EncryptedStudentCredential(
                tampered, encrypted.nonce(), encrypted.keyVersion()), "execution:2:row:4"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    void encryptsBinaryCredentialFileWithoutLeavingPlainContent() {
        StudentCredentialCipher cipher = new StudentCredentialCipher("v1", KEY);
        byte[] plaintext = "xlsx-content-with-code-2468".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        EncryptedStudentCredential encrypted = cipher.encryptBytes(plaintext, "execution:3:file");

        assertThat(containsSequence(encrypted.ciphertext(), plaintext)).isFalse();
        assertThat(cipher.decryptBytes(encrypted, "execution:3:file")).isEqualTo(plaintext);
    }

    private boolean containsSequence(byte[] source, byte[] expected) {
        for (int start = 0; start <= source.length - expected.length; start++) {
            int index = 0;
            while (index < expected.length && source[start + index] == expected[index]) {
                index++;
            }
            if (index == expected.length) {
                return true;
            }
        }
        return false;
    }
}
