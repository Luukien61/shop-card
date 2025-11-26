package com.luukien.javacard.utils;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class Argon2KeyDerivation {

    // Cấu hình Argon2
    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65536; // 64MB
    private static final int PARALLELISM = 4;
    private static final int KEK_LENGTH = 32; // 256 bits cho KEK (Key Encryption Key)

    /**
     * Dẫn xuất KEK (Key Encryption Key) từ password bằng Argon2
     * Cùng password + salt sẽ luôn cho cùng một KEK
     */
    public static byte[] deriveKEK(String password, byte[] salt) {
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY_KB)
                .withParallelism(PARALLELISM)
                .withSalt(salt);

        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        
        gen.init(builder.build());

        byte[] kek = new byte[KEK_LENGTH];
        gen.generateBytes(password.toCharArray(), kek);

        return kek;
    }

    /**
     * Tạo khóa AES ngẫu nhiên để mã hóa dữ liệu thực tế
     */
    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        return keyGen.generateKey();
    }

    /**
     * Mã hóa khóa AES bằng KEK (Key Wrapping)
     * Trả về: IV + Encrypted AES Key
     */
    public static byte[] wrapAESKey(SecretKey aesKey, byte[] kek) throws Exception {
        SecretKeySpec kekSpec = new SecretKeySpec(kek, "AES");

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, kekSpec, spec);

        byte[] encryptedKey = cipher.doFinal(aesKey.getEncoded());

        // Kết hợp IV + encrypted key
        byte[] wrapped = new byte[iv.length + encryptedKey.length];
        System.arraycopy(iv, 0, wrapped, 0, iv.length);
        System.arraycopy(encryptedKey, 0, wrapped, iv.length, encryptedKey.length);

        return wrapped;
    }

    /**
     * Giải mã khóa AES từ wrapped key bằng KEK
     */
    public static SecretKey unwrapAESKey(byte[] wrappedKey, byte[] kek) throws Exception {
        // Tách IV và encrypted key
        byte[] iv = new byte[12];
        byte[] encryptedKey = new byte[wrappedKey.length - 12];
        System.arraycopy(wrappedKey, 0, iv, 0, 12);
        System.arraycopy(wrappedKey, 12, encryptedKey, 0, encryptedKey.length);

        SecretKeySpec kekSpec = new SecretKeySpec(kek, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, kekSpec, spec);

        byte[] keyBytes = cipher.doFinal(encryptedKey);
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Tạo salt cố định từ identifier
     */
    public static byte[] generateSaltFromIdentifier(String identifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(identifier.getBytes(StandardCharsets.UTF_8));
            byte[] salt = new byte[16];
            System.arraycopy(hash, 0, salt, 0, 16);
            return salt;
        } catch (Exception e) {
            throw new RuntimeException("Error generating salt", e);
        }
    }

    /**
     * Mã hóa dữ liệu bằng AES key
     */
    public static String encryptData(String plaintext, SecretKey aesKey) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Kết hợp IV + ciphertext
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Giải mã dữ liệu bằng AES key
     */
    public static String decryptData(String encryptedData, SecretKey aesKey) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedData);

        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

}
