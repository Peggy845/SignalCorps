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
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.peggy.virtualchat.database.ApiKey;
import com.peggy.virtualchat.database.ApiKeyDao;
import com.peggy.virtualchat.database.AppDatabase;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiKeySettingsActivity extends AppCompatActivity {

    private ApiKeyAdapter adapter;
    private ApiKeyDao apiKeyDao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_key_settings);

        apiKeyDao = AppDatabase.getDatabase(this).apiKeyDao();

        // 綁定 UI 實體
        RecyclerView recyclerView = findViewById(R.id.recyclerViewApiKeys);
        Button buttonAdd = findViewById(R.id.buttonAddKey);
        SwitchMaterial switchAutoRotate = findViewById(R.id.switchAutoRotate); // 未來 V2 版會用到

        // 設定 RecyclerView
        adapter = new ApiKeyAdapter(this::deleteApiKey);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // 綁定新增按鈕
        buttonAdd.setOnClickListener(v -> showAddKeyDialog());

        // 初始載入彈匣
        loadApiKeys();
    }

    private void loadApiKeys() {
        databaseExecutor.execute(() -> {
            List<ApiKey> keys = apiKeyDao.getAllKeys();
            runOnUiThread(() -> adapter.setKeys(keys));
        });
    }

    private void showAddKeyDialog() {
        // 建立輸入表單的實體
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_api_key, null);
        EditText editName = view.findViewById(R.id.editKeyName);
        EditText editKey = view.findViewById(R.id.editKeyString);

        new AlertDialog.Builder(this)
                .setTitle("新增戰備 API Key")
                .setView(view)
                .setPositiveButton("新增", (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String key = editKey.getText().toString().trim();
                    if (!name.isEmpty() && !key.isEmpty()) {
                        insertApiKey(name, key);
                    } else {
                        Toast.makeText(this, "名稱與 Key 不可為空", Toast.LENGTH_SHORT).show();
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
            newKey.lastResetDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            apiKeyDao.insertKey(newKey);
            loadApiKeys(); // 重新整理畫面
        });
    }

    private void deleteApiKey(ApiKey apiKey) {
        databaseExecutor.execute(() -> {
            apiKeyDao.deleteKey(apiKey);
            loadApiKeys();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseExecutor.shutdown();
    }
}