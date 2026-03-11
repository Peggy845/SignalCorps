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

public class MainActivity extends AppCompatActivity {

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
    }

    private void handleSendAction() {
        String content = editTextInput.getText().toString().trim();
        if (content.isEmpty()) {
            return;
        }

        // 立即清空輸入框，防止高頻率連點造成的資料庫髒寫入
        editTextInput.setText("");

        // 將耗時的 Insert 操作丟入背景執行緒
        databaseWriteExecutor.execute(() -> {
            ChatMessage newMessage = new ChatMessage();
            newMessage.speakerName = "Peggy";
            newMessage.messageContent = content;
            newMessage.createdTimestamp = System.currentTimeMillis();
            newMessage.isPending = false;

            chatDao.insertMessage(newMessage);

            // 寫入完成，確保資料庫已是最新狀態，切回主執行緒觸發 UI 重繪
            runOnUiThread(this::loadVisibleMessages);
        });
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