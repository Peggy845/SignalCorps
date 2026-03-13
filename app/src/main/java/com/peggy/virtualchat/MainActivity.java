package com.peggy.virtualchat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.peggy.virtualchat.background.AiChatWorker;
import com.peggy.virtualchat.database.ApiKey;
import com.peggy.virtualchat.database.ApiKeyDao;
import com.peggy.virtualchat.database.AppDatabase;
import com.peggy.virtualchat.database.ChatMessage;
import com.peggy.virtualchat.database.ChatMessageDao;
import com.peggy.virtualchat.network.GeminiEngine;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_ID = "virtual_chat_channel";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private EditText editTextInput;
    private ImageButton buttonSend;
    private Button buttonCall;

    private ChatMessageDao chatDao;
    private ApiKeyDao apiKeyDao;

    private final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerViewChat);
        editTextInput = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        buttonCall = findViewById(R.id.buttonCall);

        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        chatDao = AppDatabase.getDatabase(this).chatMessageDao();
        apiKeyDao = AppDatabase.getDatabase(this).apiKeyDao();

        buttonSend.setOnClickListener(v -> handleSendAction());
        buttonCall.setOnClickListener(v -> showCallDialog());

        // 綁定右上角齒輪，接通 API Key 設定迴路
        ImageView iconSettings = findViewById(R.id.iconSettings);
        iconSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        // 綁定殲滅按鈕
        ImageView iconClearChat = findViewById(R.id.iconClearChat);
        iconClearChat.setOnClickListener(v -> showClearChatDialog());

        createNotificationChannel();
        requestNotificationPermission();

        // 重新啟動背景巡邏
        scheduleBackgroundChat();

        loadVisibleMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadVisibleMessages();
    }

    // --- 彈藥庫調度邏輯 (The Ammo Dispatcher) ---
    private ApiKey getAvailableKeyAndUpdateReset() {
        List<ApiKey> allKeys = apiKeyDao.getAllKeys();
        ApiKey validKey = null;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int availableCount = 0;

        for (ApiKey key : allKeys) {
            // 跨日重置防禦：如果是新的一天，計數器歸零
            if (!today.equals(key.lastResetDate)) {
                key.usageCount = 0;
                key.lastResetDate = today;
                apiKeyDao.updateKey(key);
            }
            // 尋找還有子彈的 Key (小於20次)
            if (key.usageCount < 20) {
                availableCount++;
                if (validKey == null) {
                    validKey = key; // 鎖定第一把有子彈的 Key
                }
            }
        }

        // UI 警告：若只剩最後一把且用了 15 次以上
        if (validKey != null && availableCount == 1 && validKey.usageCount >= 15) {
            runOnUiThread(() -> Toast.makeText(this, "警告：最後一把 Key 的額度即將耗盡", Toast.LENGTH_SHORT).show());
        }

        return validKey;
    }

    private void handleSendAction() {
        String content = editTextInput.getText().toString().trim();
        if (content.isEmpty()) return;
        editTextInput.setText("");
        sendMessageAndTriggerAi(content);
    }

    private void showCallDialog() {
        String[] targets = {"Erwin", "Levi", "Hange", "RM", "SUGA", "J-hope"};
        new AlertDialog.Builder(this)
                .setTitle("指定呼叫目標")
                .setItems(targets, (dialog, which) -> {
                    String targetName = targets[which];
                    String callCmd = "呼叫 " + targetName;
                    sendMessageAndTriggerAi(callCmd);
                })
                .show();
    }

    private void sendMessageAndTriggerAi(String content) {
        databaseWriteExecutor.execute(() -> {
            ChatMessage newMessage = new ChatMessage();
            newMessage.speakerName = "Peggy";
            newMessage.messageContent = content;
            newMessage.createdTimestamp = System.currentTimeMillis();
            newMessage.isPending = false;
            chatDao.insertMessage(newMessage);

            runOnUiThread(() -> {
                loadVisibleMessages();
                triggerAiResponse();
            });
        });
    }

    private void triggerAiResponse() {
        databaseWriteExecutor.execute(() -> {
            // 1. 索取彈藥
            ApiKey validKey = getAvailableKeyAndUpdateReset();

            if (validKey == null) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "所有 API Key 彈藥已徹底耗盡！請補充。", Toast.LENGTH_LONG).show());
                return;
            }

            List<ChatMessage> history = chatDao.getAllVisibleMessages();

            // 2. 將鎖定的 Key 送往前線
            GeminiEngine.requestAiResponse(validKey.keyString, history, new GeminiEngine.GeminiCallback() {
                @Override
                public void onSuccess(String speaker, int delaySeconds, String message) {
                    if ("none".equalsIgnoreCase(speaker)) return;

                    // 3. 攻擊成功，實體扣除彈藥
                    databaseWriteExecutor.execute(() -> {
                        validKey.usageCount += 1;
                        apiKeyDao.updateKey(validKey);
                    });

                    scheduleAiMessage(speaker, message, delaySeconds);
                }

                @Override
                public void onFailure(String errorReason) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "連線破裂: " + errorReason, Toast.LENGTH_LONG).show());
                }
            });
        });
    }

    private void scheduleAiMessage(String speaker, String messageContent, int delaySeconds) {
        long delayMillis = delaySeconds * 1000L;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            databaseWriteExecutor.execute(() -> {
                ChatMessage aiMessage = new ChatMessage();
                aiMessage.speakerName = speaker;
                aiMessage.messageContent = messageContent;
                aiMessage.createdTimestamp = System.currentTimeMillis();
                aiMessage.isPending = false;
                chatDao.insertMessage(aiMessage);
                runOnUiThread(this::loadVisibleMessages);
            });
        }, delayMillis);
    }

    private void loadVisibleMessages() {
        databaseWriteExecutor.execute(() -> {
            List<ChatMessage> history = chatDao.getAllVisibleMessages();
            runOnUiThread(() -> {
                adapter.setMessages(history);
                if (!history.isEmpty()) {
                    recyclerView.scrollToPosition(history.size() - 1);
                }
            });
        });
    }

    private void showClearChatDialog() {
        new AlertDialog.Builder(this)
                .setTitle("物理清除警告")
                .setMessage("確定要殲滅所有對話紀錄？此操作將重置 AI 的上下文記憶。")
                .setPositiveButton("強制清除", (dialog, which) -> {
                    databaseWriteExecutor.execute(() -> {
                        chatDao.deleteAllMessages();
                        runOnUiThread(() -> {
                            adapter.setMessages(new java.util.ArrayList<>());
                            Toast.makeText(MainActivity.this, "物理秩序已重置", Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "虛擬群組推播";
            String description = "接收突發訊息 (靜音懸浮)";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(false);
            channel.setVibrationPattern(new long[]{0});
            channel.setSound(null, null);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void scheduleBackgroundChat() {
        PeriodicWorkRequest chatWorkRequest = new PeriodicWorkRequest.Builder(AiChatWorker.class, 1, TimeUnit.HOURS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("AiBackgroundChat", ExistingPeriodicWorkPolicy.UPDATE, chatWorkRequest);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseWriteExecutor.shutdown();
    }
}