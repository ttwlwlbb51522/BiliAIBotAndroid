package cn.citprobe.biliaibot;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "biliaibot";
    private static final String KEY_URL = "server_url";
    private static final String KEY_TOKEN = "admin_token";
    private static final String DEFAULT_URL = "http://10.0.2.2:24444";

    private EditText serverUrl;
    private EditText adminToken;
    private TextView connectionState;
    private TextView statusText;
    private TextView configText;
    private TextView draftEmpty;
    private LinearLayout draftContainer;
    private TextView logText;
    private Button browseButton;

    private SharedPreferences preferences;
    private ApiClient api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverUrl = findViewById(R.id.serverUrl);
        adminToken = findViewById(R.id.adminToken);
        connectionState = findViewById(R.id.connectionState);
        statusText = findViewById(R.id.statusText);
        configText = findViewById(R.id.configText);
        draftEmpty = findViewById(R.id.draftEmpty);
        draftContainer = findViewById(R.id.draftContainer);
        logText = findViewById(R.id.logText);
        browseButton = findViewById(R.id.browseButton);

        serverUrl.setText(preferences.getString(KEY_URL, DEFAULT_URL));
        adminToken.setText(preferences.getString(KEY_TOKEN, ""));

        findViewById(R.id.saveConnection).setOnClickListener(v -> saveAndConnect());
        findViewById(R.id.refreshStatus).setOnClickListener(v -> refreshStatus());
        findViewById(R.id.refreshDrafts).setOnClickListener(v -> refreshDrafts());
        findViewById(R.id.refreshLogs).setOnClickListener(v -> refreshLogs());
        browseButton.setOnClickListener(v -> showBrowseDialog());
        findViewById(R.id.editConfig).setOnClickListener(v ->
                startActivity(new Intent(this, ConfigActivity.class)));

        String savedToken = preferences.getString(KEY_TOKEN, "");
        if (!savedToken.isEmpty()) {
            connect();
        } else {
            connectionState.setText("请填写电脑端地址和管理 Token");
        }
    }

    private void saveAndConnect() {
        String url = ApiClient.normalize(serverUrl.getText().toString());
        String token = adminToken.getText().toString().trim();
        if (url.isEmpty() || token.isEmpty()) {
            toast("地址和 Token 都不能为空");
            return;
        }
        preferences.edit().putString(KEY_URL, url).putString(KEY_TOKEN, token).apply();
        serverUrl.setText(url);
        connect();
    }

    private void connect() {
        api = new ApiClient(serverUrl.getText().toString(), adminToken.getText().toString());
        connectionState.setText("正在连接…");
        refreshStatus();
    }

    private boolean ready() {
        if (api == null) {
            toast("请先保存连接");
            return false;
        }
        return true;
    }

    private void refreshStatus() {
        if (!ready()) {
            return;
        }
        api.status(new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (!body.optBoolean("ok", false)) {
                    connectionState.setText("连接失败：" + body.optString("message", "未知错误"));
                    return;
                }
                JSONObject bot = body.optJSONObject("bot");
                String name = bot == null ? "未知" : bot.optString("name", "未知");
                long mid = bot == null ? 0 : bot.optLong("mid", 0);
                String stats = formatStats(body.optJSONObject("stats"));
                String text = "机器人：" + name + "（UID " + mid + "）\n"
                        + "主人 UID：" + body.optLong("ownerUid", 0) + "\n"
                        + "版本：" + body.optString("version", "dev")
                        + "    运行：" + formatDuration(body.optLong("uptimeSeconds", 0)) + "\n"
                        + "模式：" + (body.optBoolean("dryRun", true) ? "DRY-RUN（不实际发送）" : "实际发送")
                        + "\n待审草稿：" + body.optInt("draftCount", 0)
                        + "    自动刷视频：" + (body.optBoolean("autoBrowse", false)
                        ? "开（" + body.optInt("autoBrowseIntervalMinutes", 30) + "分钟）" : "关") + "\n"
                        + "分享方式：" + body.optString("browseShareMode", "")
                        + "    轮询：" + body.optInt("pollSeconds", 0) + "s";
                statusText.setText(text);
                configText.setText("模型：" + body.optString("model", "?")
                        + "\n今日统计：" + (stats.isEmpty() ? "暂无" : stats));
                connectionState.setText("已连接：" + serverUrl.getText());
            }

            @Override
            public void onError(String message) {
                connectionState.setText("连接失败：" + message);
            }
        });
    }

    private void refreshDrafts() {
        if (!ready()) {
            return;
        }
        draftEmpty.setText("加载中…");
        api.drafts(new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (!body.optBoolean("ok", false)) {
                    draftEmpty.setText("加载失败：" + body.optString("message", ""));
                    return;
                }
                JSONArray array = body.optJSONArray("drafts");
                draftContainer.removeAllViews();
                int count = array == null ? 0 : array.length();
                if (count == 0) {
                    draftEmpty.setVisibility(View.VISIBLE);
                    draftEmpty.setText("暂无待审草稿");
                    return;
                }
                draftEmpty.setVisibility(View.GONE);
                for (int i = 0; i < count; i++) {
                    addDraft(array.optJSONObject(i));
                }
            }

            @Override
            public void onError(String message) {
                draftEmpty.setText("加载失败：" + message);
            }
        });
    }

    private void addDraft(JSONObject draft) {
        if (draft == null) {
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.item_draft, draftContainer, false);
        TextView title = view.findViewById(R.id.draftTitle);
        TextView meta = view.findViewById(R.id.draftMeta);
        TextView content = view.findViewById(R.id.draftContent);
        TextView reply = view.findViewById(R.id.draftReply);
        Button approve = view.findViewById(R.id.approveButton);
        Button drop = view.findViewById(R.id.dropButton);

        String author = draft.optString("authorName", "网友");
        title.setText(author + " · " + draft.optString("title", "评论"));
        meta.setText("评论 ID " + draft.optLong("sourceRpid", 0)
                + "    时间 " + draft.optString("createdAt", ""));
        content.setText("原评论：" + draft.optString("content", ""));
        reply.setText("拟回复：" + draft.optString("reply", ""));
        approve.setOnClickListener(v -> confirmApprove(draft));
        drop.setOnClickListener(v -> confirmDrop(draft));
        draftContainer.addView(view);
    }

    private void confirmApprove(JSONObject draft) {
        String id = draft.optString("id", "");
        new AlertDialog.Builder(this)
                .setTitle("批准并发送")
                .setMessage(draft.optString("reply", ""))
                .setNegativeButton("取消", null)
                .setPositiveButton("发送", (dialog, which) -> approve(id))
                .show();
    }

    private void approve(String id) {
        api.approve(id, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                toast(body.optString("message", body.optBoolean("ok", false) ? "已批准" : "未发送"));
                refreshDrafts();
                refreshStatus();
            }

            @Override
            public void onError(String message) {
                toast("批准失败：" + message);
            }
        });
    }

    private void confirmDrop(JSONObject draft) {
        String id = draft.optString("id", "");
        new AlertDialog.Builder(this)
                .setTitle("丢弃草稿")
                .setMessage(draft.optString("reply", ""))
                .setNegativeButton("取消", null)
                .setPositiveButton("丢弃", (dialog, which) -> drop(id))
                .show();
    }

    private void drop(String id) {
        api.drop(id, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                toast(body.optString("message", "已丢弃"));
                refreshDrafts();
            }

            @Override
            public void onError(String message) {
                toast("丢弃失败：" + message);
            }
        });
    }

    private void showBrowseDialog() {
        if (!ready()) {
            return;
        }
        EditText input = new EditText(this);
        input.setHint("搜索关键词");
        input.setText("蓝色大肥鱼");
        new AlertDialog.Builder(this)
                .setTitle("手动刷视频")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("开始", (dialog, which) -> {
                    api.browse(input.getText().toString(), new ApiClient.Callback() {
                        @Override
                        public void onSuccess(JSONObject body) {
                            toast(body.optString("message", "已开始"));
                            refreshLogs();
                        }

                        @Override
                        public void onError(String message) {
                            toast("启动失败：" + message);
                        }
                    });
                })
                .show();
    }

    private void refreshLogs() {
        if (!ready()) {
            return;
        }
        logText.setText("加载中…");
        api.logs(250, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                logText.setText(body.optString("logs", "暂无日志"));
            }

            @Override
            public void onError(String message) {
                logText.setText("读取日志失败：" + message);
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static String formatStats(JSONObject stats) {
        if (stats == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<String> keys = stats.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(key).append('=').append(stats.optLong(key, 0));
        }
        return sb.toString();
    }

    private static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        return m + "m " + s + "s";
    }
}
