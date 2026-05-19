import io.javalin.Javalin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.service.TokenStream;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;

import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import okhttp3.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

    private static final String GEMINI_API_KEY = "AIzaSyA90FLcOCQBApw967TLNTbzxWPiVBCi13I";
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    // ✅ 按 userId 存每个用户的对话历史，后端重启才清空
    // key = userId, value = 最近20条消息的滑动窗口
    private static final Map<String, ChatMemory> userChatMemories = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Initializing FoodAgent Multi-Agent System...");

        StreamingChatLanguageModel streamingModel = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .build();

        ChatLanguageModel syncModel = GoogleAiGeminiChatModel.builder()
                .apiKey(GEMINI_API_KEY)
                .modelName("gemini-2.5-flash")
                .build();

        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        String mongoUrl = "mongodb+srv://maggie917:HHVYHEljjFKkrXud@cluster0.oi5k0vh.mongodb.net/?appName=Cluster0";
        MongoClient mongoClient = MongoClients.create(mongoUrl);
        MongoDatabase mongoDatabase = mongoClient.getDatabase("food_agent_db");

        EmbeddingStore<TextSegment> embeddingStore = MongoDbEmbeddingStore.builder()
                .fromClient(mongoClient)
                .databaseName("food_agent_db")
                .collectionName("user_memories")
                .indexName("vector_index")
                .build();

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.6)
                .build();

        FoodDeliveryTools foodTools = new FoodDeliveryTools();
        MemoryTools memoryTools = new MemoryTools(embeddingStore, embeddingModel);
        FootprintTools footprintTools = new FootprintTools(embeddingStore, embeddingModel, mongoDatabase);
        NutritionTools nutritionTools = new NutritionTools();

        SearchAgent searchAgent = AiServices.builder(SearchAgent.class)
                .chatLanguageModel(syncModel)
                .tools(foodTools)
                .build();

        NutritionAgent nutritionAgent = AiServices.builder(NutritionAgent.class)
                .chatLanguageModel(syncModel)
                .tools(nutritionTools, footprintTools)
                .build();

        MemoryAgent memoryAgent = AiServices.builder(MemoryAgent.class)
                .chatLanguageModel(syncModel)
                .tools(memoryTools, footprintTools)
                .contentRetriever(contentRetriever)
                .build();

        AgentTools agentTools = new AgentTools(searchAgent, nutritionAgent, memoryAgent);

        System.out.println("✅ Multi-Agent system ready: Orchestrator + SearchAgent + NutritionAgent + MemoryAgent");

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.http.maxRequestSize = 10_000_000L;
        }).start(8080);
        System.out.println("Server started on http://localhost:8080");

        app.post("/api/footprint", ctx -> {
            try {
                JsonNode json = mapper.readTree(ctx.body());
                String userId = json.get("userId").asText();
                String type = json.get("type").asText();
                String content = json.get("content").asText();
                footprintTools.saveFootprint(userId, type, content);
                ctx.result("ok");
            } catch (Exception e) {
                ctx.status(500).result("error");
            }
        });

        app.get("/api/footprint/history", ctx -> {
            try {
                String userId = ctx.queryParam("userId");
                String type = ctx.queryParam("type");
                int limit = Integer.parseInt(ctx.queryParamAsClass("limit", String.class).getOrDefault("5"));

                if (userId == null || userId.isBlank()) { ctx.json(List.of()); return; }

                MongoCollection<Document> historyCollection = mongoDatabase.getCollection("user_history");
                var records = historyCollection.find(Filters.and(
                        Filters.eq("userId", userId),
                        Filters.eq("type", type != null ? type : "search")
                )).sort(Sorts.descending("timestamp")).limit(limit);

                List<String> results = new ArrayList<>();
                for (Document doc : records) {
                    String c = doc.getString("content");
                    if (c != null && !results.contains(c)) results.add(c);
                }
                ctx.json(results);
            } catch (Exception e) {
                ctx.json(List.of());
            }
        });

        app.post("/api/chat", ctx -> {
            try {
                String body = ctx.body();
                JsonNode jsonNode = mapper.readTree(body);

                String userMessageText = jsonNode.get("message").asText();
                String contextData = jsonNode.has("context") ? jsonNode.get("context").asText() : "";
                String base64Image = jsonNode.has("image") && !jsonNode.get("image").isNull()
                        ? jsonNode.get("image").asText() : null;

                String dynamicUserId = jsonNode.has("userId") ? jsonNode.get("userId").asText() : "guest";
                String promptPrefix = "User [ID: " + dynamicUserId + "] asks: ";
                System.out.println("\n[RECEIVED] " + promptPrefix + userMessageText);

                // ✅ 核心：每个用户有自己的对话历史
                // computeIfAbsent：有就用，没有就新建一个20条窗口的 memory
                ChatMemory chatMemory = userChatMemories.computeIfAbsent(
                        dynamicUserId,
                        id -> {
                            System.out.println("[MEMORY] Creating new chat memory for: " + id);
                            return MessageWindowChatMemory.withMaxMessages(20);
                        }
                );

                // ✅ 每次请求用该用户的 memory 构建 Orchestrator 实例
                // 注意：每次都 build 新实例，但共享同一个 chatMemory 对象
                OrchestratorAgent orchestrator = AiServices.builder(OrchestratorAgent.class)
                        .streamingChatLanguageModel(streamingModel)
                        .tools(agentTools)
                        .chatMemory(chatMemory)  // ← 关键！传入用户专属 memory
                        .build();

                String finalPrompt;

                if (base64Image != null && !base64Image.isEmpty()) {
                    String cleanBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
                    String mediaType = cleanBase64.startsWith("iVBORw0") ? "image/png" : "image/jpeg";
                    System.out.println("[IMAGE] Analyzing via Gemini Vision...");

                    String nutritionAnalysis = analyzeImageNutrition(cleanBase64, mediaType);
                    System.out.println("[IMAGE] Analysis complete.");

                    int estimatedCalories = extractTotalCalories(nutritionAnalysis);
                    if (estimatedCalories > 0 && !dynamicUserId.equals("guest")) {
                        String shortDesc = userMessageText.length() > 5
                                ? "food from photo (" + userMessageText + ")"
                                : "food from uploaded photo";
                        footprintTools.saveCalorieRecord(dynamicUserId, shortDesc, estimatedCalories);
                    }

                    finalPrompt = promptPrefix + userMessageText
                            + "\n\n[VISION ANALYSIS RESULT]:\n" + nutritionAnalysis
                            + "\n\n[INSTRUCTION]: Call askNutritionAgent with this analysis and userId "
                            + dynamicUserId + " to present results and check today's calorie budget.";
                } else {
                    finalPrompt = promptPrefix + userMessageText + "\nContext Area/Restaurant: " + contextData;
                }

                UserMessage userMessage = UserMessage.from(finalPrompt);

                ctx.contentType("text/plain; charset=UTF-8");
                CompletableFuture<Void> future = new CompletableFuture<>();
                ctx.future(() -> future);

                TokenStream stream = orchestrator.chat(userMessage);

                stream.onNext(token -> {
                            try {
                                ctx.res().getOutputStream().print(token);
                                ctx.res().getOutputStream().flush();
                            } catch (Exception e) { e.printStackTrace(); }
                        })
                        .onComplete(response -> {
                            System.out.println("\n[INFO] Orchestrator finished. Memory size: "
                                    + chatMemory.messages().size() + " messages");
                            future.complete(null);
                        })
                        .onError(error -> {
                            System.err.println("Streaming Error: " + error.getMessage());
                            future.completeExceptionally(error);
                        })
                        .start();

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
                ctx.status(500).result("Error!");
            }
        });
    }

    private static String analyzeImageNutrition(String base64Image, String mediaType) {
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
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + GEMINI_API_KEY)
                    .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
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

    private static int extractTotalCalories(String analysisText) {
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
}