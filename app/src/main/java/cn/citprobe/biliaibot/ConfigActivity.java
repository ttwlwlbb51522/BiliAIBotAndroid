package cn.citprobe.biliaibot;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConfigActivity extends AppCompatActivity {

    private static final String PREFS = "biliaibot";
    private static final String KEY_URL = "server_url";
    private static final String KEY_TOKEN = "admin_token";

    private EditText configSearch;
    private TextView configStatus;
    private LinearLayout fieldContainer;
    private ApiClient api;
    private final List<Field> fields = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        configSearch = findViewById(R.id.configSearch);
        configStatus = findViewById(R.id.configStatus);
        fieldContainer = findViewById(R.id.fieldContainer);

        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        String url = preferences.getString(KEY_URL, "");
        String token = preferences.getString(KEY_TOKEN, "");
        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "请先在主界面保存连接", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        api = new ApiClient(url, token);

        findViewById(R.id.reloadConfig).setOnClickListener(v -> load());
        findViewById(R.id.closeConfig).setOnClickListener(v -> finish());
        configSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        load();
    }

    private void load() {
        configStatus.setText("正在加载配置项…");
        api.fields(new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (!body.optBoolean("ok", false)) {
                    configStatus.setText("加载失败：" + body.optString("message", ""));
                    return;
                }
                fields.clear();
                JSONArray array = body.optJSONArray("fields");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item != null) {
                            fields.add(Field.from(item));
                        }
                    }
                }
                configStatus.setText("共 " + fields.size() + " 项，点击任意一项修改；保存后需重启机器人");
                render();
            }

            @Override
            public void onError(String message) {
                configStatus.setText("加载失败：" + message);
            }
        });
    }

    private void render() {
        fieldContainer.removeAllViews();
        String query = configSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        String category = "";
        int shown = 0;
        for (Field field : fields) {
            String haystack = (field.key + " " + field.description + " "
                    + field.category).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) {
                continue;
            }
            if (!field.category.equals(category)) {
                category = field.category;
                TextView header = new TextView(this);
                header.setText(category);
                header.setTextColor(0xFF111827);
                header.setTextSize(15);
                header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
                header.setPadding(4, 18, 4, 2);
                fieldContainer.addView(header);
            }
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_config_field, fieldContainer, false);
            TextView keyView = item.findViewById(R.id.fieldKey);
            TextView descView = item.findViewById(R.id.fieldDescription);
            TextView valueView = item.findViewById(R.id.fieldValue);
            keyView.setText(field.key);
            descView.setText(field.description);
            if (field.sensitive) {
                valueView.setText(field.value.isEmpty() ? "未设置（敏感项）" : "已设置（敏感项，点击修改）");
            } else {
                valueView.setText(field.value.isEmpty() ? "当前值：（空）" : "当前值：" + field.value);
            }
            item.setOnClickListener(v -> edit(field));
            fieldContainer.addView(item);
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("没有匹配的配置项");
            empty.setTextColor(0xFF6B7280);
            empty.setPadding(4, 20, 4, 4);
            fieldContainer.addView(empty);
        }
    }

    private void edit(Field field) {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setText(field.sensitive ? "" : field.value);
        input.setHint(field.sensitive ? "留空表示不修改" : "输入新的值");
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(field.description)
                .setMessage(field.key + "\n\n"
                        + (field.sensitive ? "敏感配置项，当前值已隐藏。" : "当前值：" + field.value))
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText().toString();
                    if (field.sensitive && value.isEmpty()) {
                        Toast.makeText(this, "留空不会修改敏感项", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    save(field, value);
                })
                .show();
    }

    private void save(Field field, String value) {
        configStatus.setText("正在保存 " + field.key + "…");
        api.setField(field.key, value, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                boolean ok = body.optBoolean("ok", false);
                String message = body.optString("message", ok ? "已保存" : "保存失败");
                configStatus.setText(message);
                Toast.makeText(ConfigActivity.this, message, Toast.LENGTH_LONG).show();
                if (ok) {
                    load();
                }
            }

            @Override
            public void onError(String message) {
                configStatus.setText("保存失败：" + message);
                Toast.makeText(ConfigActivity.this, "保存失败：" + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private static final class Field {
        final String key;
        final String description;
        final String category;
        final String value;
        final boolean sensitive;

        Field(String key, String description, String category, String value, boolean sensitive) {
            this.key = key;
            this.description = description;
            this.category = category;
            this.value = value;
            this.sensitive = sensitive;
        }

        static Field from(JSONObject item) {
            return new Field(
                    item.optString("key", ""),
                    item.optString("description", ""),
                    item.optString("category", "其他"),
                    item.optString("value", ""),
                    item.optBoolean("sensitive", false)
            );
        }
    }
}
