import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bson.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;

public class Main {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
        System.out.println("Initializing BiteTrack Multi-Agent System...");

        String GEMINI_API_KEY = dotenv.get("GEMINI_API_KEY");
        String MONGODB_URI = dotenv.get("MONGODB_URI");

        // 1. Models
        StreamingChatLanguageModel streamingModel = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(GEMINI_API_KEY).modelName("gemini-2.5-flash").build();
        ChatLanguageModel syncModel = GoogleAiGeminiChatModel.builder()
                .apiKey(GEMINI_API_KEY).modelName("gemini-2.5-flash").build();

        // 2. MongoDB
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://maggie917:HHVYHEljjFKkrXud@cluster0.oi5k0vh.mongodb.net/?appName=Cluster0");
        MongoDatabase mongoDatabase = mongoClient.getDatabase("food_agent_db");

        // ✅ 偏好库：只存 User Profile + Calorie Goal
        EmbeddingStore<TextSegment> embeddingStore = MongoDbEmbeddingStore.builder()
                .fromClient(mongoClient).databaseName("food_agent_db")
                .collectionName("user_memories").indexName("vector_index").build();

        // ✅ 摘要库：只存 Conversation Summary，完全隔离
        EmbeddingStore<TextSegment> summaryStore = MongoDbEmbeddingStore.builder()
                .fromClient(mongoClient).databaseName("food_agent_db")
                .collectionName("user_summaries").indexName("summary_vector_index").build();

        // RAG 只查偏好库，不混入对话历史
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore).embeddingModel(embeddingModel)
                .maxResults(3).minScore(0.75).build();

        // 3. Services
        ConversationService conversationService = new ConversationService(
                mongoDatabase.getCollection("conversations"));
        ImageAnalysisService imageService = new ImageAnalysisService();

        // 4. Tools
        FoodDeliveryTools foodTools = new FoodDeliveryTools();
        // ✅ MemoryTools 现在接收两个 store：偏好库 + 摘要库
        MemoryTools memoryTools = new MemoryTools(embeddingStore, summaryStore, embeddingModel);
        FootprintTools footprintTools = new FootprintTools(embeddingStore, embeddingModel, mongoDatabase);
        NutritionTools nutritionTools = new NutritionTools();

        // 5. Sub-Agents
        SearchAgent searchAgent = AiServices.builder(SearchAgent.class)
                .chatLanguageModel(syncModel).tools(foodTools).build();
        NutritionAgent nutritionAgent = AiServices.builder(NutritionAgent.class)
                .chatLanguageModel(syncModel).tools(nutritionTools, footprintTools).build();
        MemoryAgent memoryAgent = AiServices.builder(MemoryAgent.class)
                .chatLanguageModel(syncModel).tools(memoryTools, footprintTools)
                .contentRetriever(contentRetriever).build();

        AgentTools agentTools = new AgentTools(searchAgent, nutritionAgent, memoryAgent);
        System.out.println("✅ Multi-Agent system ready.");

        // 6. Chat handler
        ChatHandler chatHandler = new ChatHandler(
                streamingModel, syncModel, agentTools, footprintTools,
                imageService, conversationService, embeddingStore, contentRetriever);

        // 7. Server + Routes
        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.http.maxRequestSize = 10_000_000L;
        }).start(8080);
        System.out.println("Server started on http://localhost:8080");

        app.post("/api/footprint", ctx -> {
            try {
                JsonNode json = mapper.readTree(ctx.body());
                footprintTools.saveFootprint(json.get("userId").asText(),
                        json.get("type").asText(), json.get("content").asText());
                ctx.result("ok");
            } catch (Exception e) { ctx.status(500).result("error"); }
        });

        app.get("/api/footprint/history", ctx -> {
            try {
                String userId = ctx.queryParam("userId");
                String type = ctx.queryParam("type");
                int limit = Integer.parseInt(ctx.queryParamAsClass("limit", String.class).getOrDefault("5"));
                if (userId == null || userId.isBlank()) { ctx.json(List.of()); return; }
                MongoCollection<Document> h = mongoDatabase.getCollection("user_history");
                var records = h.find(Filters.and(
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

        app.post("/api/conversations/message", ctx -> {
            try {
                JsonNode json = mapper.readTree(ctx.body());
                conversationService.saveMessage(
                        json.get("userId").asText(), json.get("sessionId").asText(),
                        json.get("role").asText(), json.get("text").asText(),
                        json.has("timestamp") ? json.get("timestamp").asText() : null);
                ctx.result("ok");
            } catch (Exception e) { ctx.status(500).result("error"); }
        });

        app.get("/api/conversations", ctx -> {
            try {
                String userId = ctx.queryParam("userId");
                if (userId == null || userId.isBlank()) { ctx.json(List.of()); return; }
                ctx.json(conversationService.getSessionList(userId, 20));
            } catch (Exception e) { ctx.json(List.of()); }
        });

        app.get("/api/conversations/{sessionId}", ctx -> {
            try {
                ctx.json(conversationService.getMessages(ctx.pathParam("sessionId")));
            } catch (Exception e) { ctx.json(List.of()); }
        });

        app.delete("/api/conversations/{sessionId}", ctx -> {
            try {
                mongoDatabase.getCollection("conversations").updateOne(
                        Filters.eq("sessionId", ctx.pathParam("sessionId")),
                        new Document("$set", new Document("hidden", true)));
                ctx.result("ok");
            } catch (Exception e) { ctx.status(500).result("error"); }
        });

        app.post("/api/conversations/summarize", ctx -> {
            try {
                JsonNode json = mapper.readTree(ctx.body());
                String userId = json.get("userId").asText();
                String sessionId = json.get("sessionId").asText();
                if (userId.equals("guest")
                        || !conversationService.hasUserMessages(sessionId)
                        || conversationService.hasSummary(sessionId)) {
                    ctx.result("skipped"); return;
                }
                ctx.result("ok");
                CompletableFuture.runAsync(() -> {
                    try {
                        String convText = conversationService.getConversationText(sessionId);
                        if (convText.isBlank()) return;
                        String summary = imageService.generateConversationSummary(userId, convText);
                        if (summary == null || summary.isBlank()) return;
                        String memoryText = "Conversation Summary [ID: " + userId + "] ["
                                + LocalDateTime.now().toLocalDate() + "]: " + summary;
                        var embedding = embeddingModel.embed(memoryText).content();
                        // ✅ 摘要存进 summaryStore，不再混入偏好库
                        summaryStore.add(embedding, TextSegment.from(memoryText));
                        conversationService.markSummarized(sessionId);
                        System.out.println("📝 [SUMMARY SAVED to user_summaries] " + memoryText);
                    } catch (Exception e) { System.err.println("[SUMMARY ERROR] " + e.getMessage()); }
                });
            } catch (Exception e) { ctx.status(500).result("error"); }
        });

        app.post("/api/chat", chatHandler::handle);
        app.post("/api/location", ctx -> {
            try {
                JsonNode json = mapper.readTree(ctx.body());
                String userId = json.get("userId").asText();
                String location = json.get("location").asText();
                footprintTools.saveUserLocation(userId, location);
                ctx.result("ok");
            } catch (Exception e) { ctx.status(500).result("error"); }
        });
    }
}