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
import burp.api.montoya.project.Project;

/**
 * Handles secure encryption and decryption of API keys
 */
public class KeyStorage {

    private final SecretKey encryptionKey;
    private final Gson gson = new Gson();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Initializes key storage with a derived encryption key
     * 
     * @param api The Montoya API instance
     * @throws Exception If key derivation fails
     */
    public KeyStorage(MontoyaApi api) throws Exception {
        Project project = api.project();
        String projectIdentifier = project.id() + "|" + project.name();
        this.encryptionKey = deriveKey(projectIdentifier);
    }

    /**
     * Derives an encryption key from a password using PBKDF2
     * 
     * @param password The password to derive from
     * @return A SecretKey suitable for AES encryption
     * @throws Exception If key derivation fails
     */
    private SecretKey deriveKey(String password) throws Exception {
        byte[] salt = SecurityConstants.KEY_DERIVATION_SALT.getBytes(StandardCharsets.UTF_8);

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
     * Encrypts a list of API keys
     * 
     * @param keys The keys to encrypt
     * @return Base64-encoded encrypted data
     * @throws Exception If encryption fails
     */
    public String encryptKeys(List<ApiKey> keys) throws Exception {
        String json = gson.toJson(keys);
        byte[] plaintext = json.getBytes(StandardCharsets.UTF_8);

        byte[] iv = new byte[SecurityConstants.GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(SecurityConstants.ENCRYPTION_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(
                SecurityConstants.GCM_TAG_LENGTH_BITS,
                iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        byteBuffer.put(iv);
        byteBuffer.put(ciphertext);

        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }

    /**
     * Decrypts a list of API keys
     * 
     * @param encrypted Base64-encoded encrypted data
     * @return The decrypted list of API keys
     * @throws Exception If decryption fails
     */
    public List<ApiKey> decryptKeys(String encrypted) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encrypted);

        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[SecurityConstants.GCM_IV_LENGTH_BYTES];
        byteBuffer.get(iv);

        byte[] ciphertext = new byte[byteBuffer.remaining()];
        byteBuffer.get(ciphertext);

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