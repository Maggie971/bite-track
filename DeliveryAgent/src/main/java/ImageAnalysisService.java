import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageAnalysisService {

    private static final String GEMINI_API_KEY = "AIzaSyA90FLcOCQBApw967TLNTbzxWPiVBCi13I";
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public ImageAnalysisService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    // ─────────────────────────────────────────────
    // Gemini Vision：分析食物图片，返回完整营养分析文本
    // ─────────────────────────────────────────────
    public String analyzeFood(String base64Image, String mediaType) {
        try {
            String prompt = "You are a professional nutritionist analyzing a food photo. Please provide:\\n" +
                    "1. 🍽️ All foods/dishes you can identify\\n" +
                    "2. 📏 Estimated portion size for each\\n" +
                    "3. 🔥 Estimated calories for each\\n" +
                    "4. 📊 Total estimated calories (format EXACTLY as 'Total: XXXX calories')\\n" +
                    "5. 💡 A brief health tip\\n" +
                    "Be specific, e.g., '8 pieces California roll' not just 'sushi'.";

            String requestBody = "{\"contents\":[{\"parts\":[" +
                    "{\"text\":\"" + prompt + "\"}," +
                    "{\"inline_data\":{\"mime_type\":\"" + mediaType + "\",\"data\":\"" + base64Image + "\"}}" +
                    "]}]}";

            Request request = new Request.Builder()
                    .url(GEMINI_URL + GEMINI_API_KEY)
                    .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                JsonNode root = mapper.readTree(response.body().string());
                return root.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText("Could not analyze image.");
            }
        } catch (Exception e) {
            System.err.println("[IMAGE ERROR] " + e.getMessage());
            return "Image analysis failed: " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────
    // 从分析文本里提取 "Total: XXXX calories" 的数字
    // ─────────────────────────────────────────────
    public int extractTotalCalories(String analysisText) {
        try {
            String lower = analysisText.toLowerCase();
            int idx = lower.indexOf("total:");
            if (idx == -1) idx = lower.indexOf("total estimated");
            if (idx == -1) return 0;

            StringBuilder numStr = new StringBuilder();
            boolean foundDigit = false;
            for (int i = idx; i < Math.min(idx + 60, lower.length()); i++) {
                char c = lower.charAt(i);
                if (Character.isDigit(c)) { numStr.append(c); foundDigit = true; }
                else if (foundDigit && c == ',') { /* skip thousands separator */ }
                else if (foundDigit) break;
            }
            if (numStr.length() > 0) {
                int cal = Integer.parseInt(numStr.toString());
                System.out.println("[CALORIE EXTRACT] Total: " + cal + " kcal");
                return cal;
            }
        } catch (Exception e) {
            System.err.println("[CALORIE EXTRACT ERROR] " + e.getMessage());
        }
        return 0;
    }

    // ─────────────────────────────────────────────
    // 用 Gemini 给对话生成简短标题（5个词以内）
    // 在第一轮对话结束后调用一次
    // ─────────────────────────────────────────────
    public String generateSessionTitle(String userMessage, String agentReply) {
        try {
            String prompt = "Based on this conversation snippet, generate a very short title (5 words max, no quotes, no punctuation):\\n" +
                    "User: " + userMessage.substring(0, Math.min(100, userMessage.length())) + "\\n" +
                    "Agent: " + agentReply.substring(0, Math.min(100, agentReply.length()));

            String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt + "\"}]}]}";

            Request request = new Request.Builder()
                    .url(GEMINI_URL + GEMINI_API_KEY)
                    .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                JsonNode root = mapper.readTree(response.body().string());
                String title = root.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText("").trim();
                // 最多50个字符
                return title.length() > 50 ? title.substring(0, 50) : title;
            }
        } catch (Exception e) {
            System.err.println("[TITLE ERROR] " + e.getMessage());
            // 生成失败就截取用户消息前30个字
            return userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage;
        }
    }
}