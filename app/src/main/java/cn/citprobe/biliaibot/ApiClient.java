package cn.citprobe.biliaibot;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 电脑端 Bot 的管理 API 客户端。
 */
final class ApiClient {

    interface Callback {
        void onSuccess(JSONObject body);

        void onError(String message);
    }

    private final String baseUrl;
    private final String token;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

    ApiClient(String baseUrl, String token) {
        this.baseUrl = normalize(baseUrl);
        this.token = token == null ? "" : token.trim();
    }

    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    void status(Callback callback) {
        get("/api/status", callback);
    }

    void drafts(Callback callback) {
        get("/api/drafts", callback);
    }

    void logs(int lines, Callback callback) {
        get("/api/logs?lines=" + lines, callback);
    }

    void rawConfig(Callback callback) {
        get("/api/config/raw", callback);
    }

    void fields(Callback callback) {
        get("/api/config/fields", callback);
    }

    void setField(String key, String value, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("key", key);
            body.put("value", value);
            request("POST", "/api/config/set", body.toString(), callback);
        } catch (Exception e) {
            main.post(() -> callback.onError(e.getMessage()));
        }
    }

    void browse(String keyword, Callback callback) {
        String path = "/api/browse";
        if (keyword != null && !keyword.trim().isEmpty()) {
            path += "?keyword=" + encode(keyword.trim());
        }
        post(path, callback);
    }

    void approve(String id, Callback callback) {
        post("/api/drafts/" + encode(id) + "/approve", callback);
    }

    void drop(String id, Callback callback) {
        post("/api/drafts/" + encode(id) + "/drop", callback);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private void get(String path, Callback callback) {
        request("GET", path, callback);
    }

    private void post(String path, Callback callback) {
        request("POST", path, callback);
    }

    private void request(String method, String path, Callback callback) {
        request(method, path, null, callback);
    }

    private void request(String method, String path, String body, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                if (baseUrl.isEmpty()) {
                    throw new IllegalArgumentException("请先填写电脑端地址");
                }
                if (token.isEmpty()) {
                    throw new IllegalArgumentException("请先填写管理 Token");
                }
                connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(20000);
                connection.setRequestMethod(method);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("X-Admin-Token", token);
                if (body != null) {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                    connection.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(bytes);
                    }
                } else if ("POST".equals(method)) {
                    connection.setDoOutput(true);
                    connection.setFixedLengthStreamingMode(0);
                    try (OutputStream ignored = connection.getOutputStream()) {
                        // 无请求体
                    }
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 400
                        ? connection.getErrorStream() : connection.getInputStream();
                String text = read(stream);
                JSONObject json = text == null || text.isEmpty() ? new JSONObject() : new JSONObject(text);
                if (!json.has("ok")) {
                    json.put("ok", code >= 200 && code < 300);
                }
                if (code < 200 || code >= 300) {
                    String error = json.optString("error", json.optString("message", "HTTP " + code));
                    json.put("message", error);
                }
                JSONObject result = json;
                main.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                main.post(() -> callback.onError(readable(e)));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static String readable(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }
}
