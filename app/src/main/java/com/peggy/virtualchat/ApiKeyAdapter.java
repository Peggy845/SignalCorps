package com.peggy.virtualchat;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
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
    private final Set<ApiKey> selectedKeys = new HashSet<>();

    // 擴建雙態行為介面
    public interface OnKeyActionListener {
        void onKeyActionClick(ApiKey apiKey);
    }

    private OnKeyActionListener actionListener;

    public void setOnKeyActionListener(OnKeyActionListener listener) {
        this.actionListener = listener;
    }

    public void setKeys(List<ApiKey> keys) {
        this.keyList = keys;
        notifyDataSetChanged();
    }

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

        // 雙態閥門邏輯
        holder.buttonAction.setVisibility(View.VISIBLE);
        if (apiKey.usageCount >= 20) {
            // 耗盡狀態：顯示「重新裝填 (輪迴)」圖示，染成兵團綠
            holder.buttonAction.setImageResource(android.R.drawable.ic_popup_sync);
            holder.buttonAction.setColorFilter(Color.parseColor("#2A4B3C"));
        } else {
            // 正常狀態：顯示「快轉跳過」圖示，染成灰色
            holder.buttonAction.setImageResource(android.R.drawable.ic_media_next);
            holder.buttonAction.setColorFilter(Color.parseColor("#888888"));
        }

        // 綁定點擊事件，將判斷權交給 Activity
        holder.buttonAction.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onKeyActionClick(apiKey);
            }
        });

        holder.checkboxDelete.setVisibility(isDeleteMode ? View.VISIBLE : View.GONE);
        holder.checkboxDelete.setOnCheckedChangeListener(null);
        holder.checkboxDelete.setChecked(selectedKeys.contains(apiKey));
        holder.checkboxDelete.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) selectedKeys.add(apiKey);
            else selectedKeys.remove(apiKey);
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
        ImageButton buttonAction;

        KeyViewHolder(View itemView) {
            super(itemView);
            textKeyName = itemView.findViewById(R.id.textKeyName);
            progressUsage = itemView.findViewById(R.id.progressUsage);
            textUsageCount = itemView.findViewById(R.id.textUsageCount);
            checkboxDelete = itemView.findViewById(R.id.checkboxDelete);
            // 沿用 XML 中原本的 id: buttonExhaustKey
            buttonAction = itemView.findViewById(R.id.buttonExhaustKey);
        }
    }
}