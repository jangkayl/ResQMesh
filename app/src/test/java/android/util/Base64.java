package android.util;

import java.nio.charset.StandardCharsets;

/** JVM-only implementation of the Base64 operations used by payload/crypto tests. */
public final class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;

    private Base64() {}

    public static String encodeToString(byte[] input, int flags) {
        java.util.Base64.Encoder encoder = (flags & URL_SAFE) != 0
                ? java.util.Base64.getUrlEncoder() : java.util.Base64.getEncoder();
        if ((flags & NO_PADDING) != 0) encoder = encoder.withoutPadding();
        String encoded = encoder.encodeToString(input);
        if ((flags & NO_WRAP) != 0 || encoded.isEmpty()) return encoded;
        String newline = (flags & CRLF) != 0 ? "\r\n" : "\n";
        StringBuilder wrapped = new StringBuilder();
        for (int offset = 0; offset < encoded.length(); offset += 76) {
            wrapped.append(encoded, offset, Math.min(offset + 76, encoded.length())).append(newline);
        }
        return wrapped.toString();
    }

    public static byte[] decode(byte[] input, int flags) {
        return decode(new String(input, StandardCharsets.US_ASCII), flags);
    }

    public static byte[] decode(String input, int flags) {
        java.util.Base64.Decoder decoder = (flags & URL_SAFE) != 0
                ? java.util.Base64.getUrlDecoder() : java.util.Base64.getDecoder();
        return decoder.decode(input.replaceAll("[\\t\\n\\r ]", ""));
    }
}
