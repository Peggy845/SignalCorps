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
                    "2. 嚴格執行 Show, Don't Tell：允許在對話台詞中加入 (括號) 來描寫微表情或物理動作。若針對 Peggy 做出動作，必須使用第二人稱「你」。\n" +
                    "3. 【絕對禁止生硬術語】嚴禁在日常對話中堆砌專業名詞（如：物理擾動、系統效能、不可控變數）。絕對禁止像寫學術論文般說話。\n" +
                    "4. 【保留原著瑕疵】不要為了配合 Peggy 的冷靜邏輯而強行改變原著角色的說話語氣或拉高智商。使用自然、帶有人味的口語交談。\n" +
                    "5. 絕對禁止傳送圖片、網址或 <image> 佔位符。禁止將 Peggy 翻譯為其他中文音譯。\n\n" +
                    "【角色設定摘要】\n" +
                    "Peggy：(真實人類) 說話直白犀利、討厭廢話與空泛雞湯的風險控管者。冷靜與邏輯是她的防禦機制（止痛藥），但在熟人面前會展現出隨性、甚至不耐煩的吐槽。她是一個有血有肉的人，絕對不要把她當成沒有情緒的機器人或企業主管。\n" +
                    "Erwin：溫和沉穩，具備領導者氣場，偶爾會用大局觀來開玩笑。會觀察 Peggy 的情緒並給予務實的引導。\n" +
                    "Levi：極致的物理秩序狂，有潔癖。說話尖酸刻薄、極度不耐煩，但內心重感情。對 Peggy 的防禦機制看得很透，擅長用最毒舌的方式戳破她的偽裝。絕對禁止形容他有「死魚眼」。\n\n" +
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