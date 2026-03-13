package com.peggy.virtualchat.background;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.peggy.virtualchat.MainActivity;
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
import java.util.concurrent.CountDownLatch;

public class AiChatWorker extends Worker {

    public AiChatWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    // 背景專用的彈藥檢查與跨日重置
    private ApiKey getAvailableKeyAndUpdateReset(ApiKeyDao apiKeyDao) {
        List<ApiKey> allKeys = apiKeyDao.getAllKeys();
        ApiKey validKey = null;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        for (ApiKey key : allKeys) {
            if (!today.equals(key.lastResetDate)) {
                key.usageCount = 0;
                key.lastResetDate = today;
                apiKeyDao.updateKey(key);
            }
            if (key.usageCount < 20 && validKey == null) {
                validKey = key;
            }
        }
        return validKey;
    }

    @NonNull
    @Override
    public Result doWork() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        if (currentHour >= 2 && currentHour < 8) {
            return Result.success();
        }

        Context context = getApplicationContext();
        AppDatabase db = AppDatabase.getDatabase(context);
        ChatMessageDao chatDao = db.chatMessageDao();
        ApiKeyDao apiKeyDao = db.apiKeyDao();

        // 1. 檢查是否有彈藥
        ApiKey validKey = getAvailableKeyAndUpdateReset(apiKeyDao);
        if (validKey == null) {
            return Result.failure(); // 沒子彈，背景任務直接撤退
        }

        List<ChatMessage> history = chatDao.getAllVisibleMessages();
        final Result[] finalResult = new Result[]{Result.failure()};
        CountDownLatch latch = new CountDownLatch(1);

        // 2. 使用動態 Key 發起攻擊
        GeminiEngine.requestAiResponse(validKey.keyString, history, new GeminiEngine.GeminiCallback() {
            @Override
            public void onSuccess(String speaker, int delaySeconds, String message) {
                if (!"none".equalsIgnoreCase(speaker)) {
                    new Thread(() -> {
                        try {
                            // 扣除彈藥
                            validKey.usageCount += 1;
                            apiKeyDao.updateKey(validKey);

                            ChatMessage aiMessage = new ChatMessage();
                            aiMessage.speakerName = speaker;
                            aiMessage.messageContent = message;
                            aiMessage.createdTimestamp = System.currentTimeMillis();
                            aiMessage.isPending = false;
                            chatDao.insertMessage(aiMessage);

                            sendNotification(context, speaker, message);
                        } finally {
                            finalResult[0] = Result.success();
                            latch.countDown();
                        }
                    }).start();
                } else {
                    finalResult[0] = Result.success();
                    latch.countDown();
                }
            }

            @Override
            public void onFailure(String errorReason) {
                finalResult[0] = Result.retry();
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            return Result.failure();
        }
        return finalResult[0];
    }

    private void sendNotification(Context context, String title, String content) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
    }
}