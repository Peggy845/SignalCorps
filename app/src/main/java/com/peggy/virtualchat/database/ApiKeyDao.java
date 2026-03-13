package com.peggy.virtualchat.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ApiKeyDao {
    @Insert
    void insertKey(ApiKey apiKey);

    @Delete
    void deleteKey(ApiKey apiKey);

    @Update
    void updateKey(ApiKey apiKey);

    @Query("SELECT * FROM api_keys ORDER BY id ASC")
    List<ApiKey> getAllKeys();
}