package com.peggy.virtualchat.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ChatMessageDao {
    // 抓取所有已顯示的正常對話
    @Query("SELECT * FROM chat_message WHERE is_pending = 0 ORDER BY created_timestamp ASC")
    List<ChatMessage> getAllVisibleMessages();

    // 抓取時間已到、準備推播的隱藏對話
    @Query("SELECT * FROM chat_message WHERE is_pending = 1 AND trigger_timestamp <= :currentTime")
    List<ChatMessage> getDuePendingMessages(long currentTime);

    @Insert
    void insertMessage(ChatMessage message);

    @Update
    void updateMessage(ChatMessage message);

    @Query("DELETE FROM chat_message")
    void deleteAllMessages();
}