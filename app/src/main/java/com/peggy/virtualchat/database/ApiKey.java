package com.peggy.virtualchat.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "api_keys")
public class ApiKey {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "key_name")
    public String keyName;

    @ColumnInfo(name = "key_string")
    public String keyString;

    @ColumnInfo(name = "usage_count")
    public int usageCount;

    @ColumnInfo(name = "last_reset_date")
    public String lastResetDate; // 格式: "yyyy-MM-dd"
}