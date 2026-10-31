package com.autosign.xiaoheihe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;

final class HeyboxClient {

    static final String API_BASE = "https://api.xiaoheihe.cn";
    static final String DATA_BASE = "https://data.xiaoheihe.cn";
    static final String HKEY_API_DEFAULT = "https://hkey.qcciii.com/hkey";

    static final String WEB_VERSION = "999.0.4";
    static final String X_CLIENT_VERSION = "1.3.389";
    static final String WEB_UA =
            "Mozilla/5.0 (Linux; Android 16; 23113RKC6C Build/BP2A.250605.031.A3; wv) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.192 Mobile Safari/537.36";
    static final String APP_UA =
            "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.118 Safari/537.36 ApiMaxJia/1.0";
    static final String APP_REFERER = "http://api.maxjia.com/";
    static final String WEB_REFERER = "https://web.xiaoheihe.cn/";

    private static final String RSA_BODY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDZgjVwAiKTjZ55nG+mW6r3TSU4"
            + "ECvNYqDMIS/bhCj2QaH5GI/KZb2TBp+CBvUj9SLFnmJQ0kzHzHoGZCQ88VevCff"
            + "F7JePGF9cmKQqotlfTKbV4oxV5iLz7JSG6b/Vg7AXtrTolNtWsa8HiB0tI0YClY"
            + "aQlOXm4UxLeSxQwSFETwIDAQAB";

    private static final String REPORT_IV = "abcdefghijklmnop";
    private static final SecureRandom RNG = new SecureRandom();

    final String heyboxId;
    final String imei;
    final String webCookie;
    final String appCookie;
    final boolean allowExternal;
    final boolean appMode;
    final String deviceId;
    final String cookieKeysSummary;
    String hkeyApi = "";
    String appVersion = "";
    String appBuild = "";

    HeyboxClient(String rawCookie, boolean allowExternal) {
        this(rawCookie, allowExternal, randomDeviceId());
    }

    HeyboxClient(String rawCookie, boolean allowExternal, String deviceId) {
        this.allowExternal = allowExternal;
        this.deviceId = (deviceId == null || deviceId.length() != 32 || !deviceId.matches("[0-9a-f]{32}"))
                ? randomDeviceId() : deviceId;
        if (allowExternal) {
            this.hkeyApi = HKEY_API_DEFAULT;
        }
        LinkedHashMap<String, String> map = parseCookie(rawCookie);
        String pkey = map.get("pkey");
        String tokenId = map.get("x_xhh_tokenid");
        this.appMode = tokenId == null || tokenId.isEmpty() || XhhSig.isAppPkey(pkey);
        if (pkey == null || pkey.isEmpty()) {
            throw new IllegalArgumentException("cookie 里缺少 pkey");
        }
        this.heyboxId = XhhSig.decodePkeyToUserId(pkey);
        if (this.heyboxId.isEmpty()) {
            throw new IllegalArgumentException("无法从 pkey 解析出 heybox_id，cookie 可能不完整");
        }
        this.imei = XhhSig.makeImei(pkey);
        if (!map.containsKey("x_pkey")) {
            map.put("x_pkey", pkey);
        }
        if (!map.containsKey("x_heybox_id")) {
            map.put("x_heybox_id", heyboxId);
        }
        if (appMode) {
            this.appCookie = "pkey=" + pkey;
        } else if (tokenId != null && !tokenId.isEmpty()) {
            if (!map.containsKey("x_xhh_tokenid")) {
                map.put("x_xhh_tokenid", tokenId);
            }
            this.appCookie = "pkey=" + pkey + "; x_xhh_tokenid=" + tokenId;
        } else {
            this.appCookie = "pkey=" + pkey;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        this.webCookie = sb.toString();
        StringBuilder keys = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (keys.length() > 0) {
                keys.append(", ");
            }
            keys.append(e.getKey()).append("(").append(e.getValue().length()).append(")");
        }
        this.cookieKeysSummary = keys.toString();
    }

    static LinkedHashMap<String, String> parseCookie(String raw) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        for (String piece : raw.split(";")) {
            String item = piece.trim();
            int pos = item.indexOf('=');
            if (pos <= 0) {
                continue;
            }
            String k = item.substring(0, pos).trim();
            String v = item.substring(pos + 1).trim();
            if (!k.isEmpty()) {
                map.put(k, v);
            }
        }
        return map;
    }

    private static String buildQuery(Map<String, String> src) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : src.entrySet()) {
            String v = e.getValue();
            if (v == null || v.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=');
            try {
                sb.append(java.net.URLEncoder.encode(v, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException ue) {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    private LinkedHashMap<String, String> webQuery(String path, Map<String, String> extra) {
        return webQueryFor(path, heyboxId, "weboutapp", extra);
    }

    private static LinkedHashMap<String, String> webQueryFor(
            String path, String heyboxIdVal, String xClientType, Map<String, String> extra) {
        LinkedHashMap<String, String> q = new LinkedHashMap<String, String>();
        q.put("app", "heybox");
        q.put("os_type", "web");
        q.put("x_app", "heybox_website");
        q.put("x_client_type", xClientType);
        q.put("client_type", "web");
        q.put("web_version", "3.0");
        q.put("x_os_type", "Windows");
        q.put("device_id", loginDeviceId);
        q.put("heybox_id", heyboxIdVal);
        String[] sig = XhhSig.makeWebSignature(path);
        q.put("version", WEB_VERSION);
        q.put("_time", sig[0]);
        q.put("hkey", sig[1]);
        q.put("nonce", sig[2]);
        if (extra != null) {
            q.putAll(extra);
        }
        return q;
    }

    static String loginDeviceId = randomDeviceId();

    static JSONObject sendLoginCode(String phone, String deviceId) throws Exception {
        return sendLoginCode(phone, deviceId, "");
    }

    static JSONObject sendLoginCode(String phone, String deviceId, String tokenId) throws Exception {
        return sendLoginCodeQ(phone, deviceId, tokenId, null);
    }

    static JSONObject sendLoginCodeQ(String phone, String deviceId, String tokenId, Map<String, String> captcha)
            throws Exception {
        loginDeviceId = deviceId;
        LinkedHashMap<String, String> body = new LinkedHashMap<String, String>();
        body.put("phone_num", rsaEncryptBase64("+86" + phone));
        LinkedHashMap<String, String> extra = new LinkedHashMap<String, String>();
        if (tokenId != null && !tokenId.isEmpty()) {
            extra.put("x_xhh_tokenid", tokenId);
        }
        if (captcha != null) {
            extra.putAll(captcha);
        }
        String url = API_BASE + "/account/get_login_code/?" + buildQuery(
                webQueryFor("/account/get_login_code/", "", "web", extra));
        String resp = http(url, "POST", webHeadersStatic(true, ""), buildQuery(body));
        return parseJson(url, resp);
    }

    static JSONObject nativeLoginWithCode(String phone, String code, String deviceId) throws Exception {
        return nativeLoginWithCode(phone, code, deviceId, "");
    }

    static JSONObject nativeLoginWithCode(String phone, String code, String deviceId, String tokenId) throws Exception {
        return nativeLoginWithCodeQ(phone, code, deviceId, tokenId, null);
    }

    static JSONObject nativeLoginWithCodeQ(String phone, String code, String deviceId, String tokenId,
            Map<String, String> captcha) throws Exception {
        loginDeviceId = deviceId;
        LinkedHashMap<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("code", code);
        extra.put("forbidden_auto_tologin", "true");
        if (tokenId != null && !tokenId.isEmpty()) {
            extra.put("x_xhh_tokenid", tokenId);
        }
        if (captcha != null) {
            extra.putAll(captcha);
        }
        LinkedHashMap<String, String> body = new LinkedHashMap<String, String>();
        body.put("phone_num", rsaEncryptBase64("+86" + phone));
        String url = API_BASE + "/account/login_code/?" + buildQuery(
                webQueryFor("/account/login_code/", "", "web", extra));
        HttpResult hr = httpFull(url, "POST", webHeadersStatic(true, ""), buildQuery(body));
        JSONObject resp = parseJson(url, hr.body);
        if ("ok".equals(resp.optString("status"))) {
            fillLoginResult(resp, hr.setCookie);
        }
        return resp;
    }

    // ---- App（移动端）参数登录：签发 App 登录态 pkey（48），可签到 ----

    static String appLoginImei(String deviceId) {
        String seed = (deviceId == null || deviceId.isEmpty()) ? randomDeviceId() : deviceId;
        return XhhSig.md5Hex(seed).substring(0, 16);
    }

    static String makeDeviceToken() {
        byte[] buf = new byte[64];
        RNG.nextBytes(buf);
        return java.util.Base64.getEncoder().encodeToString(buf);
    }

    static JSONObject appSendLoginCode(String phone, String imei, String tokenId,
            Map<String, String> captcha) throws Exception {
        LinkedHashMap<String, String> extra = new LinkedHashMap<String, String>();
        if (captcha != null) {
            extra.putAll(captcha);
        }
        LinkedHashMap<String, String> body = new LinkedHashMap<String, String>();
        body.put("phone_num", rsaEncryptBase64("+86" + phone));
        return appLoginCall("/account/get_phonenum_code/", imei, tokenId, extra, buildQuery(body));
    }

    static JSONObject appLoginWithCode(String phone, String code, String imei, String tokenId,
            Map<String, String> captcha) throws Exception {
        LinkedHashMap<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("code", code);
        if (captcha != null) {
            extra.putAll(captcha);
        }
        LinkedHashMap<String, String> body = new LinkedHashMap<String, String>();
        body.put("phone_num", rsaEncryptBase64("+86" + phone));
        return appLoginCall("/account/login_code/", imei, tokenId, extra, buildQuery(body));
    }

    private static JSONObject appLoginCall(String path, String imei, String tokenId,
            Map<String, String> extra, String body) throws Exception {
        long t = System.currentTimeMillis() / 1000L;
        LinkedHashMap<String, String> hq = new LinkedHashMap<String, String>();
        hq.put("mode", "request");
        hq.put("path", path);
        hq.put("time", String.valueOf(t));
        hq.put("imei", imei);
        hq.put("heybox_id", "-1");
        hq.put("os_version", "12");
        String hurl = HKEY_API_DEFAULT + "?" + buildQuery(hq);
        LinkedHashMap<String, String> hh = new LinkedHashMap<String, String>();
        hh.put("User-Agent", APP_UA);
        hh.put("Accept", "application/json");
        JSONObject hresp = parseJson(hurl, http(hurl, "GET", hh, null));
        JSONObject hr = hresp.optJSONObject("result");
        if (hr == null) {
            hr = hresp;
        }
        String hk = hr.optString("hkey", "");
        if (hk.isEmpty()) {
            throw new IllegalStateException("获取 hkey 失败: " + hresp.optString("msg", hresp.toString()));
        }
        String ver = hr.optString("version", "1.3.399");
        String build = hr.optString("build", "1148");
        String rnd = hr.optString("_rnd", "");
        String htime = hr.optString("time", String.valueOf(t));

        LinkedHashMap<String, String> q = new LinkedHashMap<String, String>();
        q.put("heybox_id", "-1");
        q.put("imei", imei);
        q.put("device_info", "HBP-AL00");
        q.put("nonce", XhhSig.makeNonce());
        q.put("hkey", hk);
        if (!rnd.isEmpty()) {
            q.put("_rnd", rnd);
        }
        q.put("os_type", "Android");
        q.put("x_os_type", "Android");
        q.put("x_client_type", "mobile");
        q.put("os_version", "12");
        q.put("version", ver);
        q.put("build", build);
        q.put("_time", htime);
        q.put("dw", "360");
        q.put("channel", "heybox");
        q.put("x_app", "heybox");
        q.put("time_zone", "Asia/Shanghai");
        q.put("time_", htime);
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !e.getValue().isEmpty()) {
                    q.put(e.getKey(), e.getValue());
                }
            }
        }
        String url = API_BASE + path + "?" + buildQuery(q);
        LinkedHashMap<String, String> h = new LinkedHashMap<String, String>();
        h.put("User-Agent", APP_UA);
        h.put("Referer", APP_REFERER);
        h.put("Accept", "*/*");
        if (tokenId != null && !tokenId.isEmpty()) {
            h.put("Cookie", "x_xhh_tokenid=" + tokenId);
        }
        h.put("Content-Type", "application/x-www-form-urlencoded");
        HttpResult hr2 = httpFull(url, "POST", h, body);
        JSONObject resp = parseJson(url, hr2.body);
        if ("ok".equals(resp.optString("status"))) {
            fillLoginResult(resp, hr2.setCookie);
            touchAppToken(resp, hr2.setCookie);
        }
        return resp;
    }

    private static void touchAppToken(JSONObject resp, String setCookie) {
        String token = extractCookieValue(setCookie, "x_xhh_tokenid");
        if (token.isEmpty()) {
            return;
        }
        JSONObject result = resp.optJSONObject("result");
        if (result == null) {
            return;
        }
        try {
            result.put("x_xhh_tokenid", token);
        } catch (Exception ignored) {
        }
    }

    private static void fillLoginResult(JSONObject resp, String setCookie) {
        JSONObject result = resp.optJSONObject("result");
        if (result == null) {
            result = new JSONObject();
            try {
                resp.put("result", result);
            } catch (Exception ignored) {
            }
        }
        if (result.optString("pkey", "").isEmpty()) {
            String pkey = extractCookieValue(setCookie, "pkey");
            if (pkey.isEmpty()) {
                pkey = extractCookieValue(setCookie, "user_pkey");
            }
            if (!pkey.isEmpty()) {
                try {
                    result.put("pkey", pkey);
                } catch (Exception ignored) {
                }
            }
        }
        if (result.optString("heybox_id", "").isEmpty()) {
            String hb = extractCookieValue(setCookie, "heybox_id");
            if (!hb.isEmpty()) {
                try {
                    result.put("heybox_id", hb);
                } catch (Exception ignored) {
                }
            }
        }
    }

    static String extractCookieValue(String setCookieHeader, String key) {
        if (setCookieHeader == null || setCookieHeader.isEmpty()) {
            return "";
        }
        String[] parts = setCookieHeader.split(";");
        for (String part : parts) {
            String item = part.trim();
            if (item.startsWith(key + "=")) {
                return item.substring(key.length() + 1).trim();
            }
        }
        return "";
    }

    static final class HttpResult {
        final String body;
        final String setCookie;

        HttpResult(String body, String setCookie) {
            this.body = body;
            this.setCookie = setCookie;
        }
    }

    private static HttpResult httpFull(String urlStr, String method, Map<String, String> headers, String body)
            throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod(method);
            conn.setUseCaches(false);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            if (body != null) {
                conn.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                OutputStream os = conn.getOutputStream();
                os.write(bytes);
                os.flush();
                os.close();
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (is != null) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                is.close();
            }
            StringBuilder cookies = new StringBuilder();
            Map<String, java.util.List<String>> hf = null;
            try {
                hf = conn.getHeaderFields();
            } catch (Exception ignored) {
            }
            if (hf != null) {
                for (Map.Entry<String, java.util.List<String>> e : hf.entrySet()) {
                    if (e.getKey() != null && "set-cookie".equalsIgnoreCase(e.getKey()) && e.getValue() != null) {
                        for (String v : e.getValue()) {
                            if (v != null) {
                                cookies.append(v).append(';');
                            }
                        }
                    }
                }
            }
            return new HttpResult(bos.toString("UTF-8"), cookies.toString());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    static String rsaEncryptBase64(String plain) throws Exception {
        byte[] pubRaw = java.util.Base64.getDecoder().decode(RSA_BODY);
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubRaw));
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, pub);
        return java.util.Base64.getEncoder().encodeToString(rsa.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> webHeadersStatic(boolean hasBody, String cookie) {
        LinkedHashMap<String, String> h = new LinkedHashMap<String, String>();
        h.put("User-Agent", WEB_UA);
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Origin", "https://web.xiaoheihe.cn");
        h.put("Referer", "https://web.xiaoheihe.cn/");
        if (cookie != null && !cookie.isEmpty()) {
            h.put("Cookie", cookie);
        }
        if (hasBody) {
            h.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        }
        return h;
    }

    JSONObject getJson(String path, Map<String, String> extra) throws Exception {
        if (appMode) {
            return getAppJson(path, extra);
        }
        String url = API_BASE + path + "?" + buildQuery(webQuery(path, extra));
        String body = http(url, "GET", webHeaders(false), null);
        return parseJson(url, body);
    }

    JSONObject postJson(String path, Map<String, String> extra, Map<String, String> bodyData) throws Exception {
        String url = API_BASE + path + "?" + buildQuery(webQuery(path, extra));
        String body = bodyData == null ? null : buildQuery(bodyData);
        String resp = http(url, "POST", webHeaders(body != null), body);
        return parseJson(url, resp);
    }

    private Map<String, String> webHeaders(boolean hasBody) {
        LinkedHashMap<String, String> h = new LinkedHashMap<String, String>();
        h.put("User-Agent", WEB_UA);
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Origin", "https://web.xiaoheihe.cn");
        h.put("X-Requested-With", "com.max.xiaoheihe");
        h.put("Referer", WEB_REFERER);
        h.put("Cookie", webCookie);
        if (hasBody) {
            h.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        }
        return h;
    }

    private static JSONObject parseJson(String url, String body) throws Exception {
        if (body == null || body.isEmpty()) {
            throw new IllegalStateException("接口返回为空: " + url);
        }
        return new JSONObject(body);
    }

    JSONObject postLocalEncryptedReport(String path, String jsonPayload, Map<String, String> extraQuery)
            throws Exception {
        long timeSec = System.currentTimeMillis() / 1000L;
        StringBuilder kb = new StringBuilder();
        String pool = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!#$%&()*+,-./:;<=>?@[]^_`{|}~";
        while (kb.length() < 16) {
            kb.append(pool.charAt(RNG.nextInt(pool.length())));
        }
        byte[] aesKey = kb.toString().getBytes(StandardCharsets.UTF_8);
        Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(REPORT_IV.getBytes(StandardCharsets.UTF_8)));
        byte[] encrypted = aes.doFinal(jsonPayload.getBytes(StandardCharsets.UTF_8));
        String data = java.util.Base64.getEncoder().encodeToString(encrypted);
        String key = rsaEncryptBase64(new String(aesKey, StandardCharsets.UTF_8));
        String sid = XhhSig.md5Hex(data + timeSec) + XhhSig.md5Hex(key);

        LinkedHashMap<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("type", "104");
        extra.put("time_", String.valueOf(timeSec));
        if (extraQuery != null) {
            extra.putAll(extraQuery);
        }
        String url = DATA_BASE + path + "?" + buildQuery(webQuery(path, extra));
        LinkedHashMap<String, String> body = new LinkedHashMap<String, String>();
        body.put("sid", sid);
        body.put("key", key);
        body.put("data", data);
        String resp = http(url, "POST", webHeaders(true), buildQuery(body));
        return parseJson(url, resp);
    }

    JSONObject getAppJson(String path) throws Exception {
        return getAppJson(path, null);
    }

    JSONObject getAppJson(String path, Map<String, String> extra) throws Exception {
        String[] hk = requestHkey(path);
        LinkedHashMap<String, String> q = new LinkedHashMap<String, String>();
        q.put("heybox_id", heyboxId);
        q.put("imei", imei);
        q.put("device_info", "HBP-AL00");
        q.put("nonce", XhhSig.makeNonce());
        q.put("hkey", hk[0]);
        q.put("os_type", "Android");
        q.put("x_os_type", "Android");
        q.put("x_client_type", "mobile");
        q.put("os_version", "12");
        q.put("version", hk[2]);
        q.put("build", hk[3]);
        q.put("_time", hk[1]);
        q.put("dw", "360");
        q.put("channel", "heybox");
        q.put("x_app", "heybox");
        q.put("time_zone", "Asia/Shanghai");
        q.put("time_", hk[1]);
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    q.put(e.getKey(), e.getValue());
                }
            }
        }
        String url = API_BASE + path + "?" + buildQuery(q);
        LinkedHashMap<String, String> h = new LinkedHashMap<String, String>();
        h.put("User-Agent", APP_UA);
        h.put("Referer", APP_REFERER);
        h.put("Cookie", appCookie);
        h.put("Accept", "*/*");
        return parseJson(url, http(url, "GET", h, null));
    }

    String[] requestHkey(String path) throws Exception {
        if (!allowExternal || hkeyApi.isEmpty()) {
            throw new IllegalStateException("未开启第三方 hkey 服务器");
        }
        long t = System.currentTimeMillis() / 1000L;
        LinkedHashMap<String, String> q = new LinkedHashMap<String, String>();
        q.put("mode", "request");
        q.put("path", path);
        q.put("time", String.valueOf(t));
        q.put("imei", imei);
        q.put("heybox_id", heyboxId);
        q.put("os_version", "12");
        String url = hkeyApi + "?" + buildQuery(q);
        LinkedHashMap<String, String> h = new LinkedHashMap<String, String>();
        h.put("User-Agent", APP_UA);
        h.put("Accept", "application/json");
        JSONObject resp = parseJson(url, http(url, "GET", h, null));
        String status = resp.optString("status", "");
        if (!status.isEmpty() && !"ok".equals(status)) {
            throw new IllegalStateException("hkey接口失败 status=" + status + " msg=" + resp.optString("msg"));
        }
        JSONObject result = resp.optJSONObject("result");
        if (result == null) {
            result = resp;
        }
        String hk = result.optString("hkey", "");
        String ver = result.optString("version", "");
        String build = result.optString("build", "");
        if (hk.isEmpty() || ver.isEmpty() || build.isEmpty() || !build.matches("\\d+")) {
            throw new IllegalStateException("hkey接口返回不完整");
        }
        appVersion = ver;
        appBuild = build;
        return new String[]{hk, String.valueOf(t), ver, build};
    }

    JSONObject postAppEncryptedReport(String path, String text, Map<String, String> extraQuery) throws Exception {
        if (appVersion.isEmpty() || appBuild.isEmpty()) {
            requestHkey("/task/list_v2/");
        }
        long t = System.currentTimeMillis() / 1000L;
        LinkedHashMap<String, String> q0 = new LinkedHashMap<String, String>();
        q0.put("mode", "report");
        q0.put("path", path);
        q0.put("text", text);
        q0.put("time", String.valueOf(t));
        q0.put("imei", imei);
        q0.put("heybox_id", heyboxId);
        q0.put("os_version", "12");
        String hkeyUrl = hkeyApi;
        LinkedHashMap<String, String> hh = new LinkedHashMap<String, String>();
        hh.put("Content-Type", "application/json");
        hh.put("User-Agent", APP_UA);
        hh.put("Accept", "application/json");
        StringBuilder jsonBody = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : q0.entrySet()) {
            if (!first) {
                jsonBody.append(',');
            }
            first = false;
            jsonBody.append('"').append(e.getKey()).append("\":\"").append(
                    e.getValue().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        jsonBody.append('}');
        JSONObject hResp = parseJson(hkeyUrl, http(hkeyUrl, "POST", hh, jsonBody.toString()));
        JSONObject rp = hResp.optJSONObject("result");
        if (rp == null) {
            rp = hResp;
        }
        rp = rp.optJSONObject("result") != null ? rp.optJSONObject("result") : rp;
        String hk = rp.optString("hkey", "");
        String time = rp.optString("time", String.valueOf(t));
        if (rp.optString("version", "").isEmpty() == false) {
            appVersion = rp.optString("version");
        }
        if (!rp.optString("build", "").isEmpty()) {
            appBuild = rp.optString("build");
        }
        if (hk.isEmpty()) {
            throw new IllegalStateException("hkey服务器report模式返回不完整");
        }

        LinkedHashMap<String, String> q = new LinkedHashMap<String, String>();
        q.put("heybox_id", heyboxId);
        q.put("imei", imei);
        q.put("device_info", "HBP-AL00");
        q.put("nonce", XhhSig.makeNonce());
        q.put("hkey", hk);
        q.put("_rnd", rp.optString("_rnd", ""));
        q.put("os_type", "Android");
        q.put("x_os_type", "Android");
        q.put("x_client_type", "mobile");
        q.put("os_version", "12");
        q.put("version", appVersion);
        q.put("build", appBuild);
        q.put("_time", time);
        q.put("dw", "360");
        q.put("channel", "heybox");
        q.put("x_app", "heybox");
        q.put("time_zone", "Asia/Shanghai");
        q.put("time_", time);
        if (extraQuery != null) {
            q.putAll(extraQuery);
        }
        String url = DATA_BASE + path + "?" + buildQuery(q);
        LinkedHashMap<String, String> body = new LinkedHashMap<String, String>();
        body.put("data", rp.optString("data", ""));
        body.put("key", rp.optString("key", ""));
        body.put("sid", rp.optString("sid", ""));
        LinkedHashMap<String, String> h2 = new LinkedHashMap<String, String>();
        h2.put("User-Agent", APP_UA);
        h2.put("Referer", APP_REFERER);
        h2.put("Cookie", appCookie);
        h2.put("Accept", "application/json");
        h2.put("Content-Type", "application/x-www-form-urlencoded");
        String resp = http(url, "POST", h2, buildQuery(body));
        return parseJson(url, resp);
    }

    private static String http(String urlStr, String method, Map<String, String> headers, String body)
            throws Exception {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestMethod(method);
                conn.setUseCaches(false);
                if (headers != null) {
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        conn.setRequestProperty(e.getKey(), e.getValue());
                    }
                }
                if (body != null) {
                    conn.setDoOutput(true);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    conn.setFixedLengthStreamingMode(bytes.length);
                    OutputStream os = conn.getOutputStream();
                    os.write(bytes);
                    os.flush();
                    os.close();
                }
                int code = conn.getResponseCode();
                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                if (is != null) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        bos.write(buf, 0, n);
                    }
                    is.close();
                }
                if (code < 200 || code >= 300) {
                    String errText = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                    throw new IllegalStateException("HTTP错误 " + code + ": "
                            + errText.substring(0, Math.min(300, errText.length())));
                }
                return bos.toString("UTF-8");
            } catch (java.io.IOException e) {
                last = e;
                if (attempt < 1) {
                    try {
                        Thread.sleep(800);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        throw last;
    }

    static String randomDeviceId() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 16; i++) {
            sb.append(String.format("%02x", RNG.nextInt(256)));
        }
        return sb.toString();
    }

    static String uuid() {
        return UUID.randomUUID().toString();
    }
}
