package com.autosign.xiaoheihe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Random;

public final class XhhSig {

    public static final String ALPHABET = "AB45STUVWZEFGJ6CH01D237IXYPQRKLMN89";

    private static final Random RNG = new Random();
    private static final char[] NONCE_POOL =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private XhhSig() {
    }

    public static String md5Hex(String input) {
        try {
            byte[] d = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha1Hex(String input) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static String makeWebNonce() {
        long now = System.currentTimeMillis();
        byte[] rnd = new byte[8];
        RNG.nextBytes(rnd);
        StringBuilder r = new StringBuilder(16);
        for (byte b : rnd) {
            r.append(String.format("%02x", b));
        }
        return md5Hex(now + String.valueOf(RNG.nextDouble()) + r).toUpperCase();
    }

    public static String makeNonce() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(NONCE_POOL[RNG.nextInt(NONCE_POOL.length)]);
        }
        return sb.toString();
    }

    private static int mul2(int v) {
        return (v & 128) != 0 ? ((v << 1) ^ 27) & 255 : (v << 1) & 0xff;
    }

    private static int mixX(int v) {
        return mul2(v) ^ v;
    }

    private static int mixK(int v) {
        return mixX(mul2(v));
    }

    private static int mixC(int v) {
        return mixK(mixX(mul2(v)));
    }

    private static int mixS(int v) {
        return mixC(v) ^ mixK(v) ^ mixX(v);
    }

    private static void mixArray(int[] in) {
        int[] out = new int[4];
        out[0] = mixS(in[0]) ^ mixC(in[1]) ^ mixK(in[2]) ^ mixX(in[3]);
        out[1] = mixX(in[0]) ^ mixS(in[1]) ^ mixC(in[2]) ^ mixK(in[3]);
        out[2] = mixK(in[0]) ^ mixX(in[1]) ^ mixS(in[2]) ^ mixC(in[3]);
        out[3] = mixC(in[0]) ^ mixK(in[1]) ^ mixX(in[2]) ^ mixS(in[3]);
        System.arraycopy(out, 0, in, 0, 4);
    }

    private static String remapBy(String input, String alphabet, int endOffset) {
        String base = alphabet.substring(0, alphabet.length() + endOffset);
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            out.append(base.charAt(input.charAt(i) % base.length()));
        }
        return out.toString();
    }

    private static String remapFull(String input, String alphabet) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            out.append(alphabet.charAt(input.charAt(i) % alphabet.length()));
        }
        return out.toString();
    }

    private static String interleave(String[] parts) {
        int maxLen = 0;
        for (String p : parts) {
            maxLen = Math.max(maxLen, p.length());
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < maxLen; i++) {
            for (String p : parts) {
                if (i < p.length()) {
                    out.append(p.charAt(i));
                }
            }
        }
        return out.toString();
    }

    public static String normalizePath(String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder("/");
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(p).append('/');
            }
        }
        return sb.toString();
    }

    public static String makeWebHkey(String path, long timeSec, String nonce) {
        String timePart = remapBy(String.valueOf(timeSec), ALPHABET, -2);
        String pathPart = remapFull(normalizePath(path), ALPHABET);
        String noncePart = remapFull(nonce, ALPHABET);
        String mixed = interleave(new String[]{timePart, pathPart, noncePart});
        if (mixed.length() > 20) {
            mixed = mixed.substring(0, 20);
        }
        String digest = md5Hex(mixed);
        String tail = digest.substring(digest.length() - 6);
        int[] bytes = new int[6];
        for (int i = 0; i < 6; i++) {
            bytes[i] = tail.charAt(i);
        }
        mixArray(bytes);
        int sum = 0;
        for (int b : bytes) {
            sum += b;
        }
        String suffix = String.valueOf(sum % 100);
        if (suffix.length() < 2) {
            suffix = "0" + suffix;
        }
        return remapBy(digest.substring(0, 5), ALPHABET, -4) + suffix;
    }

    public static String[] makeWebSignature(String path) {
        long t = System.currentTimeMillis() / 1000L;
        String nonce = makeWebNonce();
        return new String[]{String.valueOf(t), makeWebHkey(path, t + 1, nonce), nonce};
    }

    public static String decodePkeyToUserId(String pkey) {
        if (pkey == null || pkey.isEmpty()) {
            return "";
        }
        String decoded = pkey;
        try {
            decoded = java.net.URLDecoder.decode(pkey, "UTF-8");
        } catch (Exception ignored) {
        }
        while (decoded.endsWith("_")) {
            decoded = decoded.substring(0, decoded.length() - 1);
        }
        StringBuilder b64 = new StringBuilder(decoded.replace('-', '+').replace('_', '/'));
        while (b64.length() % 4 != 0) {
            b64.append('=');
        }
        try {
            byte[] raw = java.util.Base64.getDecoder().decode(b64.toString());
            String plain = new String(raw, StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("_(\\d{5,})").matcher(plain);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String makeImei(String pkey) {
        String hex = md5Hex(pkey);
        return hex.substring(0, 16);
    }

    /** pkey 里的客户端标记：App 登录态一般是 48，网页登录态是 28 等。 */
    public static String pkeyClientTag(String pkey) {
        if (pkey == null || pkey.isEmpty()) {
            return "";
        }
        String decoded = pkey;
        try {
            decoded = java.net.URLDecoder.decode(pkey, "UTF-8");
        } catch (Exception ignored) {
        }
        while (decoded.endsWith("_")) {
            decoded = decoded.substring(0, decoded.length() - 1);
        }
        StringBuilder b64 = new StringBuilder(decoded.replace('-', '+').replace('_', '/'));
        while (b64.length() % 4 != 0) {
            b64.append('=');
        }
        try {
            byte[] raw = java.util.Base64.getDecoder().decode(b64.toString());
            String plain = new String(raw, StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{9,12})\\.(\\d{1,3})_").matcher(plain);
            if (m.find()) {
                return m.group(2);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** 是否是能签到的 App 登录态 pkey（实测 48 与 88 都能签；28 是网页登录态，签到会被拒）。 */
    public static boolean isAppPkey(String pkey) {
        String tag = pkeyClientTag(pkey);
        return "48".equals(tag) || "88".equals(tag);
    }
}
