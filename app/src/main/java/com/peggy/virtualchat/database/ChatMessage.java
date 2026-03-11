package com.peggy.virtualchat.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "chat_message")
public class ChatMessage {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "speaker_name")
    public String speakerName;

    @ColumnInfo(name = "message_content")
    public String messageContent;

    @ColumnInfo(name = "created_timestamp")
    public long createdTimestamp;

    // 防禦推播機制的關鍵屬性
    @ColumnInfo(name = "is_pending")
    public boolean isPending;

    @ColumnInfo(name = "trigger_timestamp")
    public long triggerTimestamp;
}