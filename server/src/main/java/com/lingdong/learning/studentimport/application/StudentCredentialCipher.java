package com.lingdong.learning.studentimport.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** 使用带上下文认证的 AES-256-GCM 保护短期学员初始凭证。 */
@Component
public class StudentCredentialCipher {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final String keyVersion;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public StudentCredentialCipher(
            @Value("${lingdong.student-import.credential-key-version:v1}") String keyVersion,
            @Value("${lingdong.student-import.credential-key:}") String encodedKey
    ) {
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalStateException("学员导入凭证密钥版本不能为空");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey == null ? "" : encodedKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("学员导入凭证密钥必须使用 Base64 编码", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("学员导入凭证密钥解码后必须为32字节");
        }
        this.keyVersion = keyVersion.trim();
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public EncryptedStudentCredential encrypt(String plaintext, String context) {
        if (plaintext == null || plaintext.isBlank() || context == null || context.isBlank()) {
            throw new IllegalArgumentException("凭证明文和加密上下文不能为空");
        }
        return encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8), context);
    }

    public EncryptedStudentCredential encryptBytes(byte[] plaintext, String context) {
        if (plaintext == null || plaintext.length == 0 || context == null || context.isBlank()) {
            throw new IllegalArgumentException("凭证内容和加密上下文不能为空");
        }
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, context);
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new EncryptedStudentCredential(ciphertext, nonce, keyVersion);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("学员初始凭证加密失败", exception);
        }
    }

    public String decrypt(EncryptedStudentCredential encrypted, String context) {
        return new String(decryptBytes(encrypted, context), StandardCharsets.UTF_8);
    }

    public byte[] decryptBytes(EncryptedStudentCredential encrypted, String context) {
        if (encrypted == null || encrypted.ciphertext() == null || encrypted.nonce() == null
                || context == null || context.isBlank() || !keyVersion.equals(encrypted.keyVersion())) {
            throw new IllegalStateException("学员初始凭证解密失败");
        }
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, encrypted.nonce(), context);
            return cipher.doFinal(encrypted.ciphertext());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("学员初始凭证解密失败", exception);
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
