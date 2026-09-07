package com.lingdong.learning.studentimport.application;

/** AES-GCM 密文、随机数和密钥版本，不包含任何明文凭证。 */
public record EncryptedStudentCredential(byte[] ciphertext, byte[] nonce, String keyVersion) {
    public EncryptedStudentCredential {
        ciphertext = ciphertext == null ? null : ciphertext.clone();
        nonce = nonce == null ? null : nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext == null ? null : ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce == null ? null : nonce.clone();
    }
}
