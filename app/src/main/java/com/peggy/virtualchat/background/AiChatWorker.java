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
import com.peggy.virtualchat.R;
import com.peggy.virtualchat.database.AppDatabase;
import com.peggy.virtualchat.database.ChatMessage;
import com.peggy.virtualchat.database.ChatMessageDao;
import com.peggy.virtualchat.network.GeminiEngine;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class AiChatWorker extends Worker {

    public AiChatWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // 睡眠防火牆：02:00 ~ 07:59 強制阻斷 API 呼叫
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        if (currentHour >= 2 && currentHour < 8) {
            return Result.success();
        }

        Context context = getApplicationContext();
        ChatMessageDao chatDao = AppDatabase.getDatabase(context).chatMessageDao();

        // 撈取近期紀錄，提供 AI 上下文
        List<ChatMessage> history = chatDao.getAllVisibleMessages();

        // 防禦機制：因為網路請求是非同步的，但 Worker 需要同步回傳 Result，
        // 必須引入 CountDownLatch 強制鎖定執行緒，直到網路有了結果。
        final Result[] finalResult = new Result[]{Result.failure()};
        CountDownLatch latch = new CountDownLatch(1);

        GeminiEngine.requestAiResponse(history, new GeminiEngine.GeminiCallback() {
            @Override
            public void onSuccess(String speaker, int delaySeconds, String message) {
                if (!"none".equalsIgnoreCase(speaker)) {

                    // 防禦機制：強制建立一個獨立的拋棄式執行緒，處理資料庫寫入，徹底避開主執行緒
                    new Thread(() -> {
                        try {
                            ChatMessage aiMessage = new ChatMessage();
                            aiMessage.speakerName = speaker;
                            aiMessage.messageContent = message;
                            aiMessage.createdTimestamp = System.currentTimeMillis();
                            aiMessage.isPending = false;

                            chatDao.insertMessage(aiMessage);

                            // 資料庫寫入成功後，再觸發推播通知
                            sendNotification(context, speaker, message);
                        } finally {
                            // 無論寫入成功或失敗，都必須解開鎖定，讓 Worker 順利結束
                            finalResult[0] = Result.success();
                            latch.countDown();
                        }
                    }).start();

                } else {
                    // 如果是 "none"，沒有資料庫操作，直接解鎖
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
            // 等待網路交戰結果
            latch.await();
        } catch (InterruptedException e) {
            return Result.failure();
        }

        return finalResult[0];
    }

    private void sendNotification(Context context, String title, String content) {
        // 防禦機制：再次確認 Android 13 的推播權限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // 點擊通知後喚醒 MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // 構建通知實體
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email) // 使用系統內建圖示，避免自訂圖示解析錯誤
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // 發射通知 (隨機產生 ID 以避免通知覆蓋)
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}