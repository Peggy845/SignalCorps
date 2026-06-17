package com.peggy.virtualchat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.peggy.virtualchat.database.ApiKey;
import com.peggy.virtualchat.database.ApiKeyDao;
import com.peggy.virtualchat.database.AppDatabase;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiKeySettingsActivity extends AppCompatActivity {

    private ApiKeyAdapter adapter;
    private ApiKeyDao apiKeyDao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private Button btnAddKey, btnEnterDeleteMode, btnConfirmDelete, btnCancelDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_key_settings);

        apiKeyDao = AppDatabase.getDatabase(this).apiKeyDao();

        RecyclerView recyclerView = findViewById(R.id.recyclerViewApiKeys);
        btnAddKey = findViewById(R.id.btnAddKey);
        btnEnterDeleteMode = findViewById(R.id.btnEnterDeleteMode);
        btnConfirmDelete = findViewById(R.id.btnConfirmDelete);
        btnCancelDelete = findViewById(R.id.btnCancelDelete);

        adapter = new ApiKeyAdapter();
        // 將判斷邏輯接入雙態閥門
        adapter.setOnKeyActionListener(apiKey -> handleKeyAction(apiKey));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // --- 按鈕行為綁定 ---
        btnAddKey.setOnClickListener(v -> showAddKeyDialog());

        btnEnterDeleteMode.setOnClickListener(v -> toggleDeleteMode(true));
        btnCancelDelete.setOnClickListener(v -> toggleDeleteMode(false));

        btnConfirmDelete.setOnClickListener(v -> executeBatchDelete());

        loadApiKeys();
    }
    private void handleKeyAction(ApiKey apiKey) {
        if (apiKey.usageCount < 20) {
            new AlertDialog.Builder(this)
                    .setTitle("強制棄用警告")
                    .setMessage("確定要將「" + apiKey.keyName + "」標記為耗盡？系統將自動跳過此彈匣。")
                    .setPositiveButton("標記耗盡", (dialog, which) -> {
                        databaseExecutor.execute(() -> {
                            apiKey.usageCount = 20;
                            apiKeyDao.updateKey(apiKey);
                            loadApiKeys();
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("重新裝填 (手動輪迴)")
                    .setMessage("確定要將「" + apiKey.keyName + "」的使用次數歸零？它將重新回到首位待命。")
                    .setPositiveButton("強制歸零", (dialog, which) -> {
                        databaseExecutor.execute(() -> {
                            apiKey.usageCount = 0;
                            apiKeyDao.updateKey(apiKey);
                            loadApiKeys();
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }
    private void toggleDeleteMode(boolean enter) {
        adapter.setDeleteMode(enter);
        // UI 狀態機切換
        btnAddKey.setVisibility(enter ? View.GONE : View.VISIBLE);
        btnEnterDeleteMode.setVisibility(enter ? View.GONE : View.VISIBLE);
        btnConfirmDelete.setVisibility(enter ? View.VISIBLE : View.GONE);
        btnCancelDelete.setVisibility(enter ? View.VISIBLE : View.GONE);
    }

    private void loadApiKeys() {
        databaseExecutor.execute(() -> {
            List<ApiKey> keys = apiKeyDao.getAllKeys();

            final List<ApiKey> finalKeys = keys;
            runOnUiThread(() -> adapter.setKeys(finalKeys));
        });
    }

    private void executeBatchDelete() {
        Set<ApiKey> targets = adapter.getSelectedKeys();
        if (targets.isEmpty()) {
            toggleDeleteMode(false);
            return;
        }

        databaseExecutor.execute(() -> {
            for (ApiKey key : targets) {
                apiKeyDao.deleteKey(key);
            }
            runOnUiThread(() -> {
                toggleDeleteMode(false);
                loadApiKeys();
                Toast.makeText(this, "已刪除key", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showAddKeyDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_api_key, null);
        EditText editName = view.findViewById(R.id.editKeyName);
        EditText editKey = view.findViewById(R.id.editKeyString);

        new AlertDialog.Builder(this)
                .setTitle("新增 API Key")
                .setMessage("請至 Google AI Studio 申請免費 API Key")
                .setView(view)
                .setPositiveButton("新增", (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String key = editKey.getText().toString().trim();
                    if (!name.isEmpty() && !key.isEmpty()) {
                        insertApiKey(name, key);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void insertApiKey(String name, String keyString) {
        databaseExecutor.execute(() -> {
            ApiKey newKey = new ApiKey();
            newKey.keyName = name;
            newKey.keyString = keyString;
            newKey.usageCount = 0;

            // 【防禦修正】將新增 Key 的時間戳記，強制對齊 Google 伺服器的太平洋時區
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
            newKey.lastResetDate = sdf.format(new java.util.Date());

            apiKeyDao.insertKey(newKey);
            loadApiKeys();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseExecutor.shutdown();
    }

    private void forceExhaustKey(ApiKey apiKey) {
        new AlertDialog.Builder(this)
                .setTitle("強制棄用警告")
                .setMessage("確定要將「" + apiKey.keyName + "」標記為耗盡？系統將自動切換至下一把鑰匙")
                .setPositiveButton("標記耗盡", (dialog, which) -> {
                    databaseExecutor.execute(() -> {
                        apiKey.usageCount = 20; // 強制推滿
                        apiKeyDao.updateKey(apiKey);
                        loadApiKeys(); // 重整畫面
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }
}