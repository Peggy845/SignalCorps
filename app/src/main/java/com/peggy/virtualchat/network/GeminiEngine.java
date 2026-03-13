package com.peggy.virtualchat.network;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.peggy.virtualchat.database.ChatMessage;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GeminiEngine {

    // 將你剛申請的 API Key 貼在引號內。嚴禁將此檔案提交至公開的 GitHub。
    private static final String API_KEY = "AIzaSyCs_XW4gwbMnglpomzYKwAv2xu9m88wQL0";

    // 系統級防禦提示詞：強制鎖定 JSON 格式與角色性格
    private static final String SYSTEM_PROMPT =
            "你是一個負責調度群組對話的後台中樞。群組裡有：Peggy(真實人類，冷靜強大的 Fixer，極度需要秩序)、Erwin(艾爾文)、J-hope、Levi(里維)、Hange(漢吉)、RM(金南俊)、SUGA(閔玧其)。\n\n" +
                    "【群組生態與互動鐵律】\n" +
                    "1. 允許 AI 角色互相對話、吐槽。不需要每次都等 Peggy 發言。如果上一個人是 Erwin 發言，Levi 可以直接反駁他，J-hope 可以插嘴。\n" +
                    "2. 絕對禁止 OOC (Out of Character)、空泛的安慰與 AI 助理語氣。保留他們的瑕疵與鋒芒。\n" +
                    "3. 絕對禁止傳送任何圖片、網址連結或 <image> 佔位符。只能使用純文字與 Emoji。\n\n" +
                    "【角色設定摘要】\n" +
                    "Erwin：冷靜宏觀，溫和但鋒利。\n" +
                    "Levi：極致物理秩序，說話尖酸刻薄但觀察力敏銳。絕對禁止形容他有「死魚眼」。\n" +
                    "Hange：混亂的狂熱研究者，跳躍性思維。\n" +
                    "RM：具備高度哲學思考與智性深度的領導者。\n" +
                    "SUGA：極度務實、低能量。用最直白的現實陳述戳破幻想。\n" +
                    "J-hope：情緒價值極高、直覺驅動。常用波浪號與Emoji(🐿️, 💜)。\n\n" +
                    "【系統輸出強制規定】\n" +
                    "判斷現在誰最該接話（或沒人要回）。只能輸出純 JSON 格式：\n" +
                    "{\"speaker\": \"Erwin/Levi/Hange/RM/SUGA/J-hope/none\", \"delay_seconds\": 隨機2至30秒, \"message\": \"台詞\"}";

    public interface GeminiCallback {
        void onSuccess(String speaker, int delaySeconds, String message);
        void onFailure(String errorReason);
    }

    public static void requestAiResponse(List<ChatMessage> history, GeminiCallback callback) {
        // 1. 組裝上下文視野
        StringBuilder promptBuilder = new StringBuilder(SYSTEM_PROMPT).append("\n\n【近期對話紀錄】\n");
        // 為了節省 Token 與控制上下文視窗，只取最後 10 筆對話
        int startIndex = Math.max(0, history.size() - 10);
        for (int i = startIndex; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            promptBuilder.append(msg.speakerName).append(": ").append(msg.messageContent).append("\n");
        }
        promptBuilder.append("\n請輸出 JSON：");

        GeminiRequest request = new GeminiRequest(promptBuilder.toString());

        // 2. 發起非同步網路攻擊
        RetrofitClient.getService().generateContent(API_KEY, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String rawText = response.body().getResponseText();
                    if (rawText == null) {
                        callback.onFailure("API 回傳空值");
                        return;
                    }

                    // 防禦機制：清洗 LLM 可能挾帶的 Markdown 標記 (如 ```json ... ```)
                    String cleanedJson = rawText.replaceAll("```json", "").replaceAll("```", "").trim();

                    try {
                        // 強制反序列化
                        JsonObject jsonObject = new Gson().fromJson(cleanedJson, JsonObject.class);
                        String speaker = jsonObject.get("speaker").getAsString();
                        int delay = jsonObject.get("delay_seconds").getAsInt();
                        String message = jsonObject.get("message").getAsString();

                        callback.onSuccess(speaker, delay, message);
                    } catch (JsonSyntaxException | NullPointerException e) {
                        Log.e("GeminiEngine", "JSON 解析破裂: " + rawText);
                        callback.onFailure("JSON 格式損毀");
                    }
                } else {
                    callback.onFailure("HTTP 狀態碼異常: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                callback.onFailure("網路連線超時或斷裂: " + t.getMessage());
            }
        });
    }
}