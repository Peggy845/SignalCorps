package com.peggy.virtualchat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import com.peggy.virtualchat.database.AppDatabase;
import com.peggy.virtualchat.database.ChatMessage;
import com.peggy.virtualchat.database.ChatMessageDao;
import com.peggy.virtualchat.network.GeminiEngine;

import java.util.List;
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

    // 獨立的非同步執行緒池，防禦 UI 阻塞
    private final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 抹除系統標題列後，這裡依然載入我們自訂的 activity_main
        setContentView(R.layout.activity_main);

        // 綁定 UI 實體
        recyclerView = findViewById(R.id.recyclerViewChat);
        editTextInput = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        buttonCall = findViewById(R.id.buttonCall);
        ImageView iconClearChat = findViewById(R.id.iconClearChat);
        iconClearChat.setOnClickListener(v -> showClearChatDialog());

        // 佈局 RecyclerView 物理秩序
        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // 獲取 Room 資料庫連線
        chatDao = AppDatabase.getDatabase(this).chatMessageDao();

        // 綁定攻擊按鈕事件
        buttonSend.setOnClickListener(v -> handleSendAction());
        buttonCall.setOnClickListener(v -> showCallDialog());

        // 系統權限與排程部署
        createNotificationChannel();
        requestNotificationPermission();
        scheduleBackgroundChat();

        // 初始渲染
        loadVisibleMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 防禦機制：只要畫面從背景回到前景，強制重新抓取資料庫，解決推播後 UI 未更新的脫鉤問題
        loadVisibleMessages();
    }

    // --- 發射管線與指令區 ---

    private void handleSendAction() {
        String content = editTextInput.getText().toString().trim();
        if (content.isEmpty()) return;

        // 瞬間清空輸入框防禦連點
        editTextInput.setText("");
        sendMessageAndTriggerAi(content);
    }

    private void showClearChatDialog() {
        new AlertDialog.Builder(this)
                .setTitle("物理清除警告")
                .setMessage("確定要殲滅所有對話紀錄？此操作將重置 AI 的上下文記憶，且無法復原。")
                .setPositiveButton("強制清除", (dialog, which) -> purgeDatabase())
                .setNegativeButton("取消", null)
                .show();
    }

    private void purgeDatabase() {
        // 將高耗能的 Delete 操作丟入背景執行緒
        databaseWriteExecutor.execute(() -> {
            chatDao.deleteAllMessages();

            // 資料庫清空後，切回主執行緒將 UI 歸零
            runOnUiThread(() -> {
                adapter.setMessages(new java.util.ArrayList<>()); // 灌入空陣列
                Toast.makeText(this, "物理秩序已重置", Toast.LENGTH_SHORT).show();
            });
        });
    }
    private void showCallDialog() {
        String[] targets = {"Erwin", "Levi", "Hange", "RM", "SUGA", "J-hope"};
        new AlertDialog.Builder(this)
                .setTitle("指定呼叫目標")
                .setItems(targets, (dialog, which) -> {
                    String targetName = targets[which];
                    // 組裝強制指令，交由 GeminiEngine 的 Prompt 解析
                    String callCmd = "呼叫 " + targetName;
                    sendMessageAndTriggerAi(callCmd);
                })
                .show();
    }

    private void sendMessageAndTriggerAi(String content) {
        // 將 Peggy 的發言寫入本地資料庫
        databaseWriteExecutor.execute(() -> {
            ChatMessage newMessage = new ChatMessage();
            newMessage.speakerName = "Peggy";
            newMessage.messageContent = content;
            newMessage.createdTimestamp = System.currentTimeMillis();
            newMessage.isPending = false;
            chatDao.insertMessage(newMessage);

            runOnUiThread(() -> {
                loadVisibleMessages();
                // 觸發 AI 引擎
                triggerAiResponse();
            });
        });
    }

    // --- AI 引擎與排程區 ---

    private void triggerAiResponse() {
        databaseWriteExecutor.execute(() -> {
            List<ChatMessage> history = chatDao.getAllVisibleMessages();

            GeminiEngine.requestAiResponse(history, new GeminiEngine.GeminiCallback() {
                @Override
                public void onSuccess(String speaker, int delaySeconds, String message) {
                    if ("none".equalsIgnoreCase(speaker)) return;
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

    // --- 系統權限與背景任務 ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "虛擬群組推播";
            String description = "接收突發訊息 (靜音懸浮)";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            // 強制封殺震動與聲音，僅保留橫幅彈出
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
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void scheduleBackgroundChat() {
        // 每 1 小時背景觸發一次 (受限於 Android 省電機制，實際觸發時間會有浮動)
        PeriodicWorkRequest chatWorkRequest = new PeriodicWorkRequest.Builder(
                AiChatWorker.class, 1, TimeUnit.HOURS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "AiBackgroundChat",
                ExistingPeriodicWorkPolicy.UPDATE,
                chatWorkRequest
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 阻斷 Memory Leak
        databaseWriteExecutor.shutdown();
    }
}