package com.peggy.virtualchat;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.peggy.virtualchat.database.AppDatabase;
import com.peggy.virtualchat.database.ChatMessage;
import com.peggy.virtualchat.database.ChatMessageDao;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.peggy.virtualchat.network.GeminiEngine;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import com.peggy.virtualchat.background.AiChatWorker;

public class MainActivity extends AppCompatActivity {
    public static final String CHANNEL_ID = "virtual_chat_channel";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private EditText editTextInput;
    private Button buttonSend;

    private ChatMessageDao chatDao;

    // 建立專屬的非同步執行緒池，將資料庫的 I/O 耗時操作與 UI 渲染徹底物理隔離
    private final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerViewChat);
        editTextInput = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);

        // 佈局 RecyclerView 物理秩序
        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // 強制新訊息從畫面底部推擠，符合真實通訊軟體邏輯
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // 獲取 Room 資料庫連線實體
        chatDao = AppDatabase.getDatabase(this).chatMessageDao();

        // 綁定發送事件
        buttonSend.setOnClickListener(v -> handleSendAction());

        // 啟動時的初始渲染
        loadVisibleMessages();

        // 建立系統層級的通知隧道
        createNotificationChannel();
        // 強制檢查並索取 Android 13 通知權限
        requestNotificationPermission();
        // 註冊背景推播任務
        scheduleBackgroundChat();
    }

    private void scheduleBackgroundChat() {
        // 將排程縮短為每 1 小時背景觸發一次
        PeriodicWorkRequest chatWorkRequest = new PeriodicWorkRequest.Builder(
                AiChatWorker.class, 1, TimeUnit.HOURS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "AiBackgroundChat",
                ExistingPeriodicWorkPolicy.UPDATE,
                chatWorkRequest
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "虛擬群組推播";
            String description = "接收突發訊息 (靜音懸浮)";
            // 必須維持 HIGH 才能觸發頂部彈出視窗
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            // 強制封殺震動與聲音的物理干擾
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

    private void handleSendAction() {
        String content = editTextInput.getText().toString().trim();
        if (content.isEmpty()) return;

        editTextInput.setText("");

        // 1. 將 Peggy 的發言寫入本地資料庫
        databaseWriteExecutor.execute(() -> {
            ChatMessage newMessage = new ChatMessage();
            newMessage.speakerName = "Peggy";
            newMessage.messageContent = content;
            newMessage.createdTimestamp = System.currentTimeMillis();
            newMessage.isPending = false;
            chatDao.insertMessage(newMessage);

            runOnUiThread(() -> {
                loadVisibleMessages();
                // 2. 觸發 AI 引擎，將歷史對話送出
                triggerAiResponse();
            });
        });
    }

    private void triggerAiResponse() {
        // 在背景撈取近期對話，避免阻塞 UI
        databaseWriteExecutor.execute(() -> {
            List<ChatMessage> history = chatDao.getAllVisibleMessages();

            // 呼叫 Gemini 引擎
            GeminiEngine.requestAiResponse(history, new GeminiEngine.GeminiCallback() {
                @Override
                public void onSuccess(String speaker, int delaySeconds, String message) {
                    if ("none".equalsIgnoreCase(speaker)) {
                        return; // AI 判定目前無人回應，維持死寂
                    }
                    scheduleAiMessage(speaker, message, delaySeconds);
                }

                @Override
                public void onFailure(String errorReason) {
                    // 防禦攔截：在 UI 提示錯誤，避免陷入無止盡的等待
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "連線破裂: " + errorReason, Toast.LENGTH_LONG).show());
                }
            });
        });
    }
    private void scheduleAiMessage(String speaker, String messageContent, int delaySeconds) {
        // 建立精準的物理延遲
        long delayMillis = delaySeconds * 1000L;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 時間到達，將 AI 的回覆正式寫入資料庫
            databaseWriteExecutor.execute(() -> {
                ChatMessage aiMessage = new ChatMessage();
                aiMessage.speakerName = speaker;
                aiMessage.messageContent = messageContent;
                aiMessage.createdTimestamp = System.currentTimeMillis();
                aiMessage.isPending = false;
                chatDao.insertMessage(aiMessage);

                // 強制重繪 UI
                runOnUiThread(this::loadVisibleMessages);
            });
        }, delayMillis);
    }
    private void loadVisibleMessages() {
        // 讀取操作同樣必須在背景執行
        databaseWriteExecutor.execute(() -> {
            List<ChatMessage> history = chatDao.getAllVisibleMessages();

            // 拿到冷資料後，切回 UI Thread 賦予畫面溫度
            runOnUiThread(() -> {
                adapter.setMessages(history);
                // 陣列不為空時，強制滾動至最新對話
                if (!history.isEmpty()) {
                    recyclerView.scrollToPosition(history.size() - 1);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 隨 Activity 銷毀時釋放執行緒池，阻斷 Memory Leak
        databaseWriteExecutor.shutdown();
    }
}