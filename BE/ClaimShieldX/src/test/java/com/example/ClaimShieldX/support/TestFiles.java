package com.example.ClaimShieldX.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

/** Small files that start with the real signature of each accepted type, plus some that do not. */
public final class TestFiles {
    private TestFiles() {
    }

    /** Different seeds give different bytes, and so different checksums. */
    public static byte[] pdf(long seed) {
        return withBody("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII), seed, 200);
    }

    public static byte[] jpeg(long seed) {
        return withBody(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, seed, 200);
    }

    public static byte[] png(long seed) {
        return withBody(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, seed, 200);
    }

    public static byte[] pdfOfSize(long seed, int totalBytes) {
        byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        return withBody(header, seed, totalBytes - header.length);
    }

    public static byte[] windowsExecutable() {
        return withBody("MZ".getBytes(StandardCharsets.US_ASCII), 1, 200);
    }

    public static byte[] plainText() {
        return "this is not a document".getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] zipArchive() {
        return withBody(new byte[]{0x50, 0x4B, 0x03, 0x04}, 1, 200);
    }

    private static byte[] withBody(byte[] header, long seed, int bodyLength) {
        byte[] body = new byte[bodyLength];
        new Random(seed).nextBytes(body);
        byte[] all = Arrays.copyOf(header, header.length + body.length);
        System.arraycopy(body, 0, all, header.length, body.length);
        return all;
    }
}
