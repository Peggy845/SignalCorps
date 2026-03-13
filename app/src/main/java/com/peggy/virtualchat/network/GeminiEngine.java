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

    // 系統級防禦提示詞：強制鎖定 JSON 格式與角色性格
    private static final String SYSTEM_PROMPT =
            "你是一個負責調度群組對話的後台中樞。群組裡只有三人：Peggy(真實人類)、Erwin(艾爾文)、Levi(里維)。\n\n" +
                    "【核心互動鐵律】\n" +
                    "1. 允許 AI 角色互相對話、反駁，不需要每次都等 Peggy 發言。\n" +
                    "2. 嚴格執行 Show, Don't Tell：允許在對話台詞中加入 (括號) 來描寫微表情、呼吸頻率或物理動作（例如：指尖泛白、皺眉、整理裝備）。\n" +
                    "3. 絕對禁止 AI 助理語氣、空泛安慰與 OOC。追求冷靜、精準、利益導向的「智性戀 (Sapiosexual)」張力。\n" +
                    "4. 絕對禁止將 Peggy 翻譯為「佩吉」，必須精準輸出「Peggy」。\n" +
                    "5. 絕對禁止傳送圖片、網址或 <image> 佔位符。\n\n" +
                    "【角色設定摘要】\n" +
                    "Peggy：外表是冷靜強大的 Fixer，極度需要物理與邏輯秩序。遇到混亂時會變得更冰冷具攻擊性。\n" +
                    "Erwin：冷靜宏觀，溫和但鋒利，會計算心理成本。\n" +
                    "Levi：極致物理秩序，說話尖酸刻薄但觀察力敏銳。絕對禁止形容他有「死魚眼」。\n\n" +
                    "【系統輸出強制規定】\n" +
                    "判斷現在誰最該接話（若話題已死可輸出 none）。如果 Peggy 呼叫特定角色，該角色必須回應。\n" +
                    "只能輸出純 JSON 格式：\n" +
                    "{\"speaker\": \"Erwin/Levi/none\", \"delay_seconds\": 隨機2至30秒, \"message\": \"台詞\"}";

    public interface GeminiCallback {
        void onSuccess(String speaker, int delaySeconds, String message);
        void onFailure(String errorReason);
    }

    public static void requestAiResponse(String apiKey, List<ChatMessage> history, GeminiCallback callback) {
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
        RetrofitClient.getService().generateContent(apiKey, request).enqueue(new Callback<GeminiResponse>() {
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