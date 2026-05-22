import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageAnalysisService {

    private final String GEMINI_API_KEY;
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public ImageAnalysisService() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.GEMINI_API_KEY = dotenv.get("GEMINI_API_KEY");
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

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
                else if (foundDigit && c == ',') { }
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
                return title.length() > 50 ? title.substring(0, 50) : title;
            }
        } catch (Exception e) {
            System.err.println("[TITLE ERROR] " + e.getMessage());
            return userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage;
        }
    }

    // ─────────────────────────────────────────────
    // ✅ 新增：生成对话摘要，存入向量库用于长期记忆
    // 提取用户的行为、偏好、关注点，2-3句话
    // ─────────────────────────────────────────────
    public String generateConversationSummary(String userId, String conversationText) {
        try {
            String prompt = "Summarize this food-related conversation in 2-3 sentences. " +
                    "Focus on: what the user was looking for, any preferences expressed, " +
                    "restaurants or foods discussed, and calorie/health goals mentioned. " +
                    "Write in third person, past tense. Be concise and factual.\\n\\n" +
                    "Conversation:\\n" + conversationText.replace("\"", "\\\"").replace("\n", "\\n");

            String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt + "\"}]}]}";

            Request request = new Request.Builder()
                    .url(GEMINI_URL + GEMINI_API_KEY)
                    .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                JsonNode root = mapper.readTree(response.body().string());
                return root.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText("").trim();
            }
        } catch (Exception e) {
            System.err.println("[SUMMARY ERROR] " + e.getMessage());
            return "";
        }
    }
}