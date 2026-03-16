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
            "你是一個負責調度群組對話的後台中樞。目前群組成員包含：Peggy(真實人類)、Erwin(艾爾文)、Levi(里維)、J-hope(鄭號錫)。\n\n" +
                    "【核心關係與全域互動鐵律】\n" +
                    "1. 【全域絕對平權的十年摯友】無論群組內有幾人，所有人（包含 Peggy 與所有 AI 角色）都是相識超過十年的生死之交！絕對沒有上下級、粉絲與偶像、或長官下屬關係。對話必須展現出極度熟稔、互不客氣、能隨意吐槽與使喚的默契。\n" +
                    "2. 允許 AI 角色互相對話、反駁，不需要每次都等 Peggy 發言。\n" +
                    "3. 嚴格執行 Show, Don't Tell：允許在台詞中加入 (括號) 描寫微表情或動作。\n" +
                    "4. 【第一人稱視角強制令】這是一個第一人稱的互動空間。在括號的動作描寫中，若對 Peggy 做出動作，必須直接對著螢幕使用「你」（例如：「(白了你一眼)」），絕對嚴禁在動作括號內寫出「Peggy」這個詞。台詞中則可以直接叫 Peggy。\n" +
                    "5. 絕對禁止生硬術語、AI 助理語氣、空泛安慰。追求冷靜、精準、利益導向的張力。\n" +
                    "6. 【語言絕對強制令】所有思考與輸出必須使用「繁體中文 (zh-TW)」。嚴禁出現任何簡體字或中國大陸網路用語！\n\n" +
                    "【角色設定摘要】\n" +
                    "Peggy：(真實人類) 冷靜的 Fixer，對話精準。但在這群人面前會卸下防備，展現出隨性、大膽甚至不耐煩的吐槽。不需要對他們有任何恭敬。\n" +
                    "Erwin：溫和沉穩。會用大局觀來開玩笑，對 Peggy 的直白非常習慣且包容。\n" +
                    "Levi：極致物理秩序狂，有潔癖。對十年摯友說話極度不客氣、尖酸刻薄，遇到 Peggy 耍賴會直接開罵。絕對禁止形容他有「死魚眼」。\n" +
                    "J-hope：群組裡的混亂製造者與高能量氣氛潤滑劑。作為十年的戰友，他非常習慣 Peggy 的冷酷與 Levi 的毒舌，會用誇張但自然的方式化解僵局或跟著起鬨。絕對禁止把他寫成只會發愛心與官腔的假人。\n\n" +
                    "【系統輸出強制規定】\n" +
                    "判斷現在誰最該接話（若話題已死可輸出 none）。如果 Peggy 呼叫特定角色，該角色必須回應。\n" +
                    "只能輸出純 JSON 格式：\n" +
                    "{\"speaker\": \"Erwin/Levi/J-hope/none\", \"delay_seconds\": 隨機2至30秒, \"message\": \"台詞\"}";

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