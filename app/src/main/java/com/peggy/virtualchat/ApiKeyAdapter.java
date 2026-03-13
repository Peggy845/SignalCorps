package com.peggy.virtualchat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.peggy.virtualchat.database.ApiKey;
import java.util.ArrayList;
import java.util.List;

public class ApiKeyAdapter extends RecyclerView.Adapter<ApiKeyAdapter.KeyViewHolder> {

    private List<ApiKey> keyList = new ArrayList<>();
    private final OnKeyDeleteListener deleteListener;

    public interface OnKeyDeleteListener {
        void onDeleteClick(ApiKey apiKey);
    }

    public ApiKeyAdapter(OnKeyDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void setKeys(List<ApiKey> keys) {
        this.keyList = keys;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public KeyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_api_key, parent, false);
        return new KeyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull KeyViewHolder holder, int position) {
        ApiKey apiKey = keyList.get(position);
        holder.textKeyName.setText(apiKey.keyName);

        // 渲染剩餘彈藥量
        holder.progressUsage.setProgress(apiKey.usageCount);
        holder.textUsageCount.setText(apiKey.usageCount + "/20");

        // 綁定刪除事件
        holder.buttonDelete.setOnClickListener(v -> deleteListener.onDeleteClick(apiKey));
    }

    @Override
    public int getItemCount() {
        return keyList.size();
    }

    static class KeyViewHolder extends RecyclerView.ViewHolder {
        TextView textKeyName;
        ProgressBar progressUsage;
        TextView textUsageCount;
        ImageButton buttonDelete;

        KeyViewHolder(View itemView) {
            super(itemView);
            textKeyName = itemView.findViewById(R.id.textKeyName);
            progressUsage = itemView.findViewById(R.id.progressUsage);
            textUsageCount = itemView.findViewById(R.id.textUsageCount);
            buttonDelete = itemView.findViewById(R.id.buttonDeleteKey);
        }
    }
}