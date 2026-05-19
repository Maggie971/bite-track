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

import okhttp3.OkHttpClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

    private static final String GEMINI_API_KEY = "AIzaSyA90FLcOCQBApw967TLNTbzxWPiVBCi13I";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, ChatMemory> userChatMemories = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Initializing FoodAgent Multi-Agent System...");

        // ─────────────────────────────────────────────
        // 1. Models
        // ─────────────────────────────────────────────
        StreamingChatLanguageModel streamingModel = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GEMINI_API_KEY).modelName("gemini-2.5-flash").build();

        ChatLanguageModel syncModel = GoogleAiGeminiChatModel.builder()
                .apiKey(GEMINI_API_KEY).modelName("gemini-2.5-flash").build();

        // ─────────────────────────────────────────────
        // 2. MongoDB
        // ─────────────────────────────────────────────
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://maggie917:HHVYHEljjFKkrXud@cluster0.oi5k0vh.mongodb.net/?appName=Cluster0"
        );
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
                .maxResults(3).minScore(0.6)
                .build();

        // ─────────────────────────────────────────────
        // 3. Services
        // ─────────────────────────────────────────────
        ConversationService conversationService = new ConversationService(
                mongoDatabase.getCollection("conversations")
        );
        ImageAnalysisService imageService = new ImageAnalysisService();

        // ─────────────────────────────────────────────
        // 4. Tools
        // ─────────────────────────────────────────────
        FoodDeliveryTools foodTools = new FoodDeliveryTools();
        MemoryTools memoryTools = new MemoryTools(embeddingStore, embeddingModel);
        FootprintTools footprintTools = new FootprintTools(embeddingStore, embeddingModel, mongoDatabase);
        NutritionTools nutritionTools = new NutritionTools();

        // ─────────────────────────────────────────────
        // 5. Sub-Agents
        // ─────────────────────────────────────────────
        SearchAgent searchAgent = AiServices.builder(SearchAgent.class)
                .chatLanguageModel(syncModel).tools(foodTools).build();

        NutritionAgent nutritionAgent = AiServices.builder(NutritionAgent.class)
                .chatLanguageModel(syncModel).tools(nutritionTools, footprintTools).build();

        MemoryAgent memoryAgent = AiServices.builder(MemoryAgent.class)
                .chatLanguageModel(syncModel)
                .tools(memoryTools, footprintTools)
                .contentRetriever(contentRetriever)
                .build();

        AgentTools agentTools = new AgentTools(searchAgent, nutritionAgent, memoryAgent);
        System.out.println("✅ Multi-Agent system ready: Orchestrator + SearchAgent + NutritionAgent + MemoryAgent");

        // ─────────────────────────────────────────────
        // 6. Server
        // ─────────────────────────────────────────────
        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.http.maxRequestSize = 10_000_000L;
        }).start(8080);
        System.out.println("Server started on http://localhost:8080");

        // ─────────────────────────────────────────────
        // 7. Routes
        // ─────────────────────────────────────────────

        // 足迹：存
        app.post("/api/footprint", ctx -> {
            try {
                JsonNode json = mapper.readTree(ctx.body());
                footprintTools.saveFootprint(
                        json.get("userId").asText(),
                        json.get("type").asText(),
                        json.get("content").asText()
                );
                ctx.result("ok");
            } catch (Exception e) { ctx.status(500).result("error"); }
        });

        // 足迹：搜索历史下拉
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
            } catch (Exception e) { ctx.json(List.of()); }
        });

        // 对话：存一条消息
        app.post("/api/conversations/message", ctx -> {
            try {
                JsonNode json = mapper.readTree(ctx.body());
                conversationService.saveMessage(
                        json.get("userId").asText(),
                        json.get("sessionId").asText(),
                        json.get("role").asText(),
                        json.get("text").asText(),
                        json.has("timestamp") ? json.get("timestamp").asText() : null
                );
                ctx.result("ok");
            } catch (Exception e) { ctx.status(500).result("error"); }
        });

        // 对话：会话列表
        app.get("/api/conversations", ctx -> {
            try {
                String userId = ctx.queryParam("userId");
                if (userId == null || userId.isBlank()) { ctx.json(List.of()); return; }
                ctx.json(conversationService.getSessionList(userId, 20));
            } catch (Exception e) { ctx.json(List.of()); }
        });

        // 对话：某次会话完整消息
        app.get("/api/conversations/{sessionId}", ctx -> {
            try {
                ctx.json(conversationService.getMessages(ctx.pathParam("sessionId")));
            } catch (Exception e) { ctx.json(List.of()); }
        });

        // 主聊天
        app.post("/api/chat", ctx -> {
            try {
                JsonNode jsonNode = mapper.readTree(ctx.body());

                String userMessageText = jsonNode.get("message").asText();
                String contextData = jsonNode.has("context") ? jsonNode.get("context").asText() : "";
                String base64Image = jsonNode.has("image") && !jsonNode.get("image").isNull()
                        ? jsonNode.get("image").asText() : null;
                String dynamicUserId = jsonNode.has("userId") ? jsonNode.get("userId").asText() : "guest";
                String sessionId = jsonNode.has("sessionId") ? jsonNode.get("sessionId").asText()
                        : dynamicUserId + "_" + LocalDateTime.now().toLocalDate();

                System.out.println("\n[RECEIVED] User [ID: " + dynamicUserId + "] asks: " + userMessageText);

                // 每个用户专属的对话 memory
                ChatMemory chatMemory = userChatMemories.computeIfAbsent(
                        dynamicUserId,
                        id -> MessageWindowChatMemory.withMaxMessages(20)
                );

                OrchestratorAgent orchestrator = AiServices.builder(OrchestratorAgent.class)
                        .streamingChatLanguageModel(streamingModel)
                        .tools(agentTools)
                        .chatMemory(chatMemory)
                        .build();

                // 构建 prompt
                String promptPrefix = "User [ID: " + dynamicUserId + "] asks: ";
                String finalPrompt;

                if (base64Image != null && !base64Image.isEmpty()) {
                    String cleanBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
                    String mediaType = cleanBase64.startsWith("iVBORw0") ? "image/png" : "image/jpeg";
                    System.out.println("[IMAGE] Analyzing via Gemini Vision...");

                    String nutritionAnalysis = imageService.analyzeFood(cleanBase64, mediaType);
                    System.out.println("[IMAGE] Analysis complete.");

                    int estimatedCalories = imageService.extractTotalCalories(nutritionAnalysis);
                    if (estimatedCalories > 0 && !dynamicUserId.equals("guest")) {
                        footprintTools.saveCalorieRecord(dynamicUserId,
                                userMessageText.length() > 5 ? "food from photo (" + userMessageText + ")" : "food from uploaded photo",
                                estimatedCalories);
                    }

                    finalPrompt = promptPrefix + userMessageText
                            + "\n\n[VISION ANALYSIS RESULT]:\n" + nutritionAnalysis
                            + "\n\n[INSTRUCTION]: Call askNutritionAgent with this analysis and userId "
                            + dynamicUserId + " to present results and check today's calorie budget.";
                } else {
                    finalPrompt = promptPrefix + userMessageText + "\nContext Area/Restaurant: " + contextData;
                }

                ctx.contentType("text/plain; charset=UTF-8");
                ctx.header("X-Session-Id", sessionId);
                ctx.header("Access-Control-Expose-Headers", "X-Session-Id");

                CompletableFuture<Void> future = new CompletableFuture<>();
                ctx.future(() -> future);

                StringBuilder agentReply = new StringBuilder();
                final String finalUserMsg = userMessageText;
                final String finalSessionId = sessionId;

                orchestrator.chat(UserMessage.from(finalPrompt))
                        .onNext(token -> {
                            try {
                                agentReply.append(token);
                                ctx.res().getOutputStream().print(token);
                                ctx.res().getOutputStream().flush();
                            } catch (Exception e) { e.printStackTrace(); }
                        })
                        .onComplete(response -> {
                            System.out.println("\n[INFO] Orchestrator finished. Memory: "
                                    + chatMemory.messages().size() + " messages");

                            // 存消息到 DB（异步，不阻塞响应）
                            CompletableFuture.runAsync(() -> {
                                String replyText = agentReply.toString();
                                conversationService.saveMessage(dynamicUserId, finalSessionId, "user", finalUserMsg, null);
                                conversationService.saveMessage(dynamicUserId, finalSessionId, "agent", replyText, null);

                                // 只在第一轮对话时生成标题
                                if (!conversationService.hasCustomTitle(finalSessionId)) {
                                    String title = imageService.generateSessionTitle(finalUserMsg, replyText);
                                    conversationService.updateTitle(finalSessionId, title);
                                }
                            });

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
}