package com.lingdong.learning.studentimport.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 自描述的受控凭证密文封装，不包含明文文件内容。 */
final class StudentCredentialEnvelope {
    private static final byte[] MAGIC = "LDIC1".getBytes(StandardCharsets.US_ASCII);

    private StudentCredentialEnvelope() { }

    static byte[] pack(EncryptedStudentCredential encrypted) {
        byte[] version = encrypted.keyVersion().getBytes(StandardCharsets.UTF_8);
        byte[] nonce = encrypted.nonce();
        byte[] ciphertext = encrypted.ciphertext();
        if (version.length > 32 || nonce.length > 32 || ciphertext.length == 0) {
            throw new IllegalArgumentException("学员凭证密文封装参数不合法");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(output)) {
            data.write(MAGIC);
            data.writeByte(version.length);
            data.write(version);
            data.writeByte(nonce.length);
            data.write(nonce);
            data.writeInt(ciphertext.length);
            data.write(ciphertext);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("学员凭证密文封装失败", exception);
        }
    }

    static EncryptedStudentCredential unpack(byte[] content) {
        if (content == null || content.length < MAGIC.length + 7) {
            throw new IllegalStateException("学员凭证密文格式不合法");
        }
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(content))) {
            byte[] magic = data.readNBytes(MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IllegalStateException("学员凭证密文格式不合法");
            }
            int versionLength = data.readUnsignedByte();
            String version = new String(data.readNBytes(versionLength), StandardCharsets.UTF_8);
            int nonceLength = data.readUnsignedByte();
            byte[] nonce = data.readNBytes(nonceLength);
            int cipherLength = data.readInt();
            if (versionLength < 1 || versionLength > 32 || nonceLength != 12
                    || cipherLength < 17 || cipherLength != data.available()) {
                throw new IllegalStateException("学员凭证密文格式不合法");
            }
            return new EncryptedStudentCredential(data.readNBytes(cipherLength), nonce, version);
        } catch (IOException exception) {
            throw new IllegalStateException("学员凭证密文读取失败", exception);
        }
    }
}
