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

    public void setMessages(List<ChatMessage> messages) {
        this.messageList = messages;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messageList.get(position);
        if ("Peggy".equalsIgnoreCase(message.speakerName)) {
            return VIEW_TYPE_ME;
        } else {
            return VIEW_TYPE_THEM;
        }
    }

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("a h:mm", Locale.TAIWAN);

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
        String timeString = timeFormat.format(new Date(message.createdTimestamp));

        if (holder.getItemViewType() == VIEW_TYPE_ME) {
            MeViewHolder meHolder = (MeViewHolder) holder;
            meHolder.messageContent.setText(message.messageContent);
            meHolder.textTimestamp.setText(timeString);
        } else {
            ThemViewHolder themHolder = (ThemViewHolder) holder;
            themHolder.speakerName.setText(message.speakerName);
            themHolder.messageContent.setText(message.messageContent);
            themHolder.textTimestamp.setText(timeString);

            int avatarResId = getAvatarResourceForSpeaker(message.speakerName);
            themHolder.imageAvatar.setImageResource(avatarResId);
        }
    }

    @Override
    public int getItemCount() {
        return messageList != null ? messageList.size() : 0;
    }

    private int getAvatarResourceForSpeaker(String speaker) {
        if (speaker == null) return R.drawable.avatar_erwin;

        switch (speaker.toLowerCase()) {
            case "erwin": return R.drawable.avatar_erwin;
            case "  levi": return R.drawable.avatar_levi;
            case "hange": return R.drawable.avatar_hange;
            case "rm": return R.drawable.avatar_rm;
            case "suga": return R.drawable.avatar_suga;
            case "j-hope":
            case "jhope": return R.drawable.avatar_jhope;
            default: return R.drawable.avatar_erwin;
        }
    }

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