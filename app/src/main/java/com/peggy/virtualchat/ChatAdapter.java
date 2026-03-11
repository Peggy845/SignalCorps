package com.peggy.virtualchat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.peggy.virtualchat.database.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> messageList = new ArrayList<>();

    public void setMessages(List<ChatMessage> messages) {
        this.messageList = messages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);
        holder.speakerName.setText(message.speakerName);
        holder.messageContent.setText(message.messageContent);
    }

    @Override
    public int getItemCount() {
        return messageList != null ? messageList.size() : 0;
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView speakerName;
        TextView messageContent;

        ChatViewHolder(View itemView) {
            super(itemView);
            speakerName = itemView.findViewById(R.id.textSpeakerName);
            messageContent = itemView.findViewById(R.id.textMessageContent);
        }
    }
}