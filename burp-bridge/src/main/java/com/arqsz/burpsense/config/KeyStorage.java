package com.arqsz.burpsense.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.arqsz.burpsense.constants.SecurityConstants;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import burp.api.montoya.MontoyaApi;

/**
 * Handles secure encryption and decryption of API keys
 */
public class KeyStorage {

    private final MontoyaApi api;
    private final Gson gson = new Gson();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Initializes key storage
     * 
     * @param api The Montoya API instance
     */
    public KeyStorage(MontoyaApi api) {
        this.api = api;
    }

    /**
     * Derives an encryption key from Burp Suite installation-specific entropy
     * 
     * @param salt The salt to use for key derivation
     * @return A SecretKey suitable for AES encryption
     * @throws Exception If key derivation fails
     */
    private SecretKey deriveKey(byte[] salt) throws Exception {
        String projectInfo = api.project().id() + "|" + api.project().name();
        String systemEntropy = System.getProperty("user.name", "default");

        String password = projectInfo + "|" + systemEntropy;

        KeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                SecurityConstants.PBKDF2_ITERATION_COUNT,
                SecurityConstants.KEY_LENGTH_BITS);

        SecretKeyFactory factory = SecretKeyFactory.getInstance(SecurityConstants.KEY_DERIVATION_ALGORITHM);
        byte[] key = factory.generateSecret(spec).getEncoded();

        return new SecretKeySpec(key, SecurityConstants.KEY_ALGORITHM);
    }

    /**
     * Encrypts a list of API keys with unique salt per operation
     * 
     * Format: [salt(16 bytes)][iv(12 bytes)][ciphertext][tag]
     * 
     * @param keys The keys to encrypt
     * @return Base64-encoded encrypted data with embedded salt
     * @throws Exception If encryption fails
     */
    public String encryptKeys(List<ApiKey> keys) throws Exception {
        String json = gson.toJson(keys);
        byte[] plaintext = json.getBytes(StandardCharsets.UTF_8);

        byte[] salt = new byte[SecurityConstants.SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);

        byte[] iv = new byte[SecurityConstants.GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        SecretKey encryptionKey = deriveKey(salt);

        Cipher cipher = Cipher.getInstance(SecurityConstants.ENCRYPTION_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(
                SecurityConstants.GCM_TAG_LENGTH_BITS,
                iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
        byteBuffer.put(salt);
        byteBuffer.put(iv);
        byteBuffer.put(ciphertext);

        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }

    /**
     * Decrypts a list of API keys, extracting salt from encrypted data
     * 
     * @param encrypted Base64-encoded encrypted data with embedded salt
     * @return The decrypted list of API keys
     * @throws Exception If decryption fails
     */
    public List<ApiKey> decryptKeys(String encrypted) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encrypted);

        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

        byte[] salt = new byte[SecurityConstants.SALT_LENGTH_BYTES];
        byteBuffer.get(salt);

        byte[] iv = new byte[SecurityConstants.GCM_IV_LENGTH_BYTES];
        byteBuffer.get(iv);

        byte[] ciphertext = new byte[byteBuffer.remaining()];
        byteBuffer.get(ciphertext);

        SecretKey encryptionKey = deriveKey(salt);

        Cipher cipher = Cipher.getInstance(SecurityConstants.ENCRYPTION_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(
                SecurityConstants.GCM_TAG_LENGTH_BITS,
                iv);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        String json = new String(plaintext, StandardCharsets.UTF_8);

        return gson.fromJson(json, new TypeToken<List<ApiKey>>() {
        }.getType());
    }
}