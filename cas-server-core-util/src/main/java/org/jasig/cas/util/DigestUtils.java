package org.jasig.cas.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * This is {@link DigestUtils}
 * that encapsulates common compression calls and operations
 * in one spot.
 *
 * @author Timur Duehr timur.duehr@nccgroup.trust
 * @since 4.3
 */
public final class DigestUtils {
    private static String SHA512 = "SHA512";
    private static String SHA = "SHA";

    private DigestUtils() {
    }

    /**
     * Computes hex encoded SHA512 digest.
     * @param data data to be hashed
     * @return sha-512 hash
     */
    public static String sha512(final String data) {
        return digest(SHA512, data.getBytes());
    }

    /**
     * Computes hex encoded SHA digest.
     * @param data data to be hashed
     * @return sha hash
     */
    public static String sha(final String data) {
        return digest(SHA, data);
    }

    /**
     * Computes SHA digest.
     * @param data data to be hashed
     * @return sha hash
     */
    public static byte[] sha(final byte[] data) {
        return rawDigest(SHA, data);
    }

    /**
     * Computes hex encoded digest.
     * @param alg Digest algorithm to use
     * @param data data to be hashed
     * @return hex encoded hash
     */
    public static String digest(final String alg, final String data) {
        return digest(alg, data.getBytes());
    }

    /**
     * Computes hex encoded digest.
     * @param alg Digest algorithm to use
     * @param data data to be hashed
     * @return hex encoded hash
     */
    public static String digest(final String alg, final byte[] data) {
        return EncodingUtils.hexEncode(rawDigest(alg, data));
    }

    /**
     * Computes digest.
     * @param alg Digest algorithm to use
     * @param data data to be hashed
     * @return hash
     */
    public static byte[] rawDigest(final String alg, final byte[] data) {
        try {
            return MessageDigest.getInstance(alg).digest(data);
        } catch (final NoSuchAlgorithmException cause) {
            //TODO Throw exception inheriting from RootCasException?
            throw new SecurityException(cause);
        }
    }

}
