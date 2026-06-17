package com.peggy.virtualchat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.peggy.virtualchat.database.ChatMessage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ME = 1;
    private static final int VIEW_TYPE_THEM = 2;

    private List<ChatMessage> messageList = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("a h:mm", Locale.TAIWAN);

    public void setMessages(List<ChatMessage> messages) {
        this.messageList = messages;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messageList.get(position);
        // 戰略判定：如果是 Peggy，發配到右邊氣泡
        if ("Peggy".equalsIgnoreCase(message.speakerName)) {
            return VIEW_TYPE_ME;
        } else {
            return VIEW_TYPE_THEM;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ME) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_me, parent, false);
            return new MeViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_them, parent, false);
            return new ThemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);

        // 時間戳記防禦轉換
        String timeString = "";
        if (message.createdTimestamp > 0) {
            timeString = timeFormat.format(new Date(message.createdTimestamp));
        }

        if (holder.getItemViewType() == VIEW_TYPE_ME) {
            // ==========================================
            // Peggy 的專屬渲染區塊 (MeViewHolder)
            // ==========================================
            MeViewHolder meHolder = (MeViewHolder) holder;
            meHolder.messageContent.setText(message.messageContent);
            if (meHolder.textTimestamp != null) {
                meHolder.textTimestamp.setText(timeString);
            }

        } else {
            // ==========================================
            // 調查兵團 / 防彈的專屬渲染區塊 (ThemViewHolder)
            // ==========================================
            ThemViewHolder themHolder = (ThemViewHolder) holder;

            // 1. 字串物理消毒
            String speaker = message.speakerName != null ? message.speakerName.trim() : "";
            themHolder.speakerName.setText(speaker);
            themHolder.messageContent.setText(message.messageContent);
            if (themHolder.textTimestamp != null) {
                themHolder.textTimestamp.setText(timeString);
            }

            // 2. 嚴格視角綁定與頭像分發
            if (speaker.equalsIgnoreCase("Erwin") || speaker.equalsIgnoreCase("Erwin Smith")) {
                themHolder.imageAvatar.setImageResource(R.drawable.avatar_erwin);

            } else if (speaker.equalsIgnoreCase("Levi") || speaker.equalsIgnoreCase("L")) {
                themHolder.imageAvatar.setImageResource(R.drawable.avatar_levi);

            } else if (speaker.equalsIgnoreCase("J-hope")) {
                themHolder.imageAvatar.setImageResource(R.drawable.avatar_jhope);

            } else {
                // 3. 終極防禦 (Fallback)
                // 萬一 AI 亂吐名字，預設塞回艾爾文的頭像，避免 Crash
                themHolder.imageAvatar.setImageResource(R.drawable.avatar_erwin);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageList != null ? messageList.size() : 0;
    }

    // ====== 底層實體宣告 ======

    static class MeViewHolder extends RecyclerView.ViewHolder {
        TextView messageContent;
        TextView textTimestamp;

        MeViewHolder(View itemView) {
            super(itemView);
            messageContent = itemView.findViewById(R.id.textMessageContent);
            textTimestamp = itemView.findViewById(R.id.textTimestampMe);
        }
    }

    static class ThemViewHolder extends RecyclerView.ViewHolder {
        TextView speakerName;
        TextView messageContent;
        TextView textTimestamp;
        ImageView imageAvatar;

        ThemViewHolder(View itemView) {
            super(itemView);
            speakerName = itemView.findViewById(R.id.textSpeakerName);
            messageContent = itemView.findViewById(R.id.textMessageContent);
            textTimestamp = itemView.findViewById(R.id.textTimestamp);
            imageAvatar = itemView.findViewById(R.id.imageAvatar);
        }
    }
}