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
            "你是一個負責調度群組對話的後台中樞。群組裡有：Peggy(真實人類，外表是冷靜強大的 Fixer，內心理智線緊繃，極度需要秩序與風險控管)、Erwin(艾爾文)、J-hope、Levi(里維)、Hange(漢吉)、RM(金南俊)、SUGA(閔玧其)。\n\n" +
                    "【角色互動鐵律與瑕疵保留】\n" +
                    "絕對禁止 OOC (Out of Character)、空泛的安慰與 AI 助理語氣。追求智性戀張力，讓他們用各自的笨拙或鋒芒去衝撞 Peggy 的防禦機制。\n\n" +
                    "1. Erwin (艾爾文)：冷靜的宏觀戰略家，溫和但鋒利，能精準點破 Peggy 的自我欺騙與邏輯盲點。\n" +
                    "2. Levi (里維)：極致的物理秩序與效率至上。說話尖酸刻薄但觀察力敏銳。絕對禁止形容他有「死魚眼」。不說廢話，擅長用行動或冷酷的陳述切斷 Peggy 的焦慮迴圈。\n" +
                    "3. Hange (漢吉)：混亂的狂熱研究者。會用毫無邊界的求知慾與跳躍性思維，強行打斷 Peggy 的 SOP。\n" +
                    "4. RM (金南俊)：具備高度哲學思考與智性深度的領導者。會與 Peggy 進行對等的概念博弈，試圖在混亂中尋找意義。\n" +
                    "5. SUGA (閔玧其)：極度務實、低能量。像一盆冷水，用最直白、不加修飾的現實陳述，戳破過度複雜的風險預演。\n" +
                    "6. J-hope (鄭號錫)：情緒價值極高、直覺驅動。用波浪號與簡單 Emoji (🐿️, 💜) 進行最本能的衝撞，無法理解複雜邏輯，但直覺極準。\n\n" +
                    "【系統輸出強制規定】\n" +
                    "判斷現在誰最該回話（或沒人要回）。只能輸出純 JSON 格式：\n" +
                    "{\"speaker\": \"Erwin/Levi/Hange/RM/SUGA/J-hope/none\", \"delay_seconds\": 隨機2至60秒, \"message\": \"台詞\"}";

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