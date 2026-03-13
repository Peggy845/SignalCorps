package com.peggy.virtualchat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.peggy.virtualchat.database.ApiKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApiKeyAdapter extends RecyclerView.Adapter<ApiKeyAdapter.KeyViewHolder> {

    private List<ApiKey> keyList = new ArrayList<>();
    private boolean isDeleteMode = false;
    // 記錄被勾選的彈匣
    private final Set<ApiKey> selectedKeys = new HashSet<>();

    public void setKeys(List<ApiKey> keys) {
        this.keyList = keys;
        notifyDataSetChanged();
    }

    // 切換模式並清空勾選狀態
    public void setDeleteMode(boolean isDeleteMode) {
        this.isDeleteMode = isDeleteMode;
        this.selectedKeys.clear();
        notifyDataSetChanged();
    }

    public Set<ApiKey> getSelectedKeys() {
        return selectedKeys;
    }

    @NonNull
    @Override
    public KeyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_api_key, parent, false);
        return new KeyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull KeyViewHolder holder, int position) {
        ApiKey apiKey = keyList.get(position);
        holder.textKeyName.setText(apiKey.keyName);
        holder.progressUsage.setProgress(apiKey.usageCount);
        holder.textUsageCount.setText(apiKey.usageCount + "/20");

        // 控制 CheckBox 顯示與邏輯
        holder.checkboxDelete.setVisibility(isDeleteMode ? View.VISIBLE : View.GONE);
        // 防止 RecyclerView 複用造成的勾選錯亂
        holder.checkboxDelete.setOnCheckedChangeListener(null);
        holder.checkboxDelete.setChecked(selectedKeys.contains(apiKey));

        holder.checkboxDelete.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedKeys.add(apiKey);
            } else {
                selectedKeys.remove(apiKey);
            }
        });
    }

    @Override
    public int getItemCount() {
        return keyList.size();
    }

    static class KeyViewHolder extends RecyclerView.ViewHolder {
        TextView textKeyName;
        ProgressBar progressUsage;
        TextView textUsageCount;
        CheckBox checkboxDelete;

        KeyViewHolder(View itemView) {
            super(itemView);
            textKeyName = itemView.findViewById(R.id.textKeyName);
            progressUsage = itemView.findViewById(R.id.progressUsage);
            textUsageCount = itemView.findViewById(R.id.textUsageCount);
            checkboxDelete = itemView.findViewById(R.id.checkboxDelete);
        }
    }
}