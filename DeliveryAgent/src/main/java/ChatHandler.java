import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.javalin.http.Context;

public class ChatHandler {

    private final StreamingChatLanguageModel streamingModel;
    private final ChatLanguageModel syncModel;
    private final AgentTools agentTools;
    private final FootprintTools footprintTools;
    private final ImageAnalysisService imageService;
    private final ConversationService conversationService;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ContentRetriever contentRetriever;

    private final Map<String, ChatMemory> userChatMemories = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatHandler(
            StreamingChatLanguageModel streamingModel,
            ChatLanguageModel syncModel,
            AgentTools agentTools,
            FootprintTools footprintTools,
            ImageAnalysisService imageService,
            ConversationService conversationService,
            EmbeddingStore<TextSegment> embeddingStore,
            ContentRetriever contentRetriever
    ) {
        this.streamingModel = streamingModel;
        this.syncModel = syncModel;
        this.agentTools = agentTools;
        this.footprintTools = footprintTools;
        this.imageService = imageService;
        this.conversationService = conversationService;
        this.embeddingStore = embeddingStore;
        this.contentRetriever = contentRetriever;
    }

    private String classifyIntent(String message) {
        String lower = message.toLowerCase().trim();
        if (lower.length() < 25 && lower.matches(
                ".*(^hi$|^hello$|^hey$|^thanks$|^thank you$|^ok$|^okay$|^yes$|^no$|^sure$|^cool$|^great$|^got it$|^nice$).*"))
            return "simple";
        if (lower.matches(".*(calorie|calories|kcal|nutrition|how many cal|what did i eat|today.*eat|eat.*today|my goal|calorie goal|calorie target|set.*goal|set.*target|计算|热量|卡路里|摄入|多少卡).*"))
            return "nutrition";
        if (lower.matches(".*(i like|i love|i hate|i don.t like|i dislike|allergic|allergy|prefer|i.m vegetarian|i.m vegan|dietary|restriction|remember|my profile|what do you know).*")
                && !lower.matches(".*(recommend|suggest|find|search|what should|what can|dinner|lunch|breakfast|snack|eat|restaurant|record|log).*"))
            return "memory";
        if (lower.matches(".*(find|search|where|restaurant|near|nearby|best|top rated|around|in walnut|in san|in sf).*")
                && !lower.matches(".*(should i eat|recommend|suggest|what.*eat|today|calorie|budget).*"))
            return "search";
        return "orchestrate";
    }

    private boolean userConfirmedEating(String message) {
        String lower = message.toLowerCase();
        return lower.matches(".*(i ate|i had|i just ate|i finished|i already ate|i just finished).*");
    }

    public void handle(Context ctx) {
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

            // ✅ 从 DB 查用户保存的位置，优先于前端传来的 contextData
            String savedLocation = footprintTools.getUserLocation(dynamicUserId);
            String locationContext = (savedLocation != null && !savedLocation.isBlank())
                    ? savedLocation
                    : (contextData != null && !contextData.isBlank() ? contextData : "");

            if (!locationContext.isBlank()) {
                System.out.println("[LOCATION] Using location: " + locationContext);
            }

            ChatMemory chatMemory = userChatMemories.computeIfAbsent(
                    dynamicUserId, id -> MessageWindowChatMemory.withMaxMessages(20)
            );

            var memMessages = chatMemory.messages();
            if (!memMessages.isEmpty()) {
                var last = memMessages.get(memMessages.size() - 1);
                if (!(last instanceof dev.langchain4j.data.message.UserMessage)) {
                    chatMemory.clear();
                    System.out.println("[MEMORY] Cleared corrupted memory for: " + dynamicUserId);
                }
            }

            OrchestratorAgent orchestrator = AiServices.builder(OrchestratorAgent.class)
                    .streamingChatLanguageModel(streamingModel)
                    .tools(agentTools)
                    .chatMemory(chatMemory)
                    .build();

            String promptPrefix = "User [ID: " + dynamicUserId + "] asks: ";
            String finalPrompt;

            if (base64Image != null && !base64Image.isEmpty()) {
                String cleanBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
                String mediaType = cleanBase64.startsWith("iVBORw0") ? "image/png" : "image/jpeg";
                System.out.println("[IMAGE] Analyzing via Gemini Vision...");

                String nutritionAnalysis = imageService.analyzeFood(cleanBase64, mediaType);
                System.out.println("[IMAGE] Analysis complete.");

                boolean confirmedEating = userConfirmedEating(userMessageText);
                System.out.println("[IMAGE] User confirmed eating: " + confirmedEating);

                if (confirmedEating) {
                    int estimatedCalories = imageService.extractTotalCalories(nutritionAnalysis);
                    if (estimatedCalories > 0 && !dynamicUserId.equals("guest")) {
                        footprintTools.saveCalorieRecord(dynamicUserId,
                                "food from photo (" + userMessageText + ")", estimatedCalories);
                    }
                    finalPrompt = promptPrefix + userMessageText
                            + "\n\n[VISION ANALYSIS RESULT]:\n" + nutritionAnalysis
                            + "\n\n[INSTRUCTION]: Calories have been recorded. "
                            + "Call askNutritionAgent to present the nutritional breakdown "
                            + "and show today's calorie budget for userId " + dynamicUserId + ".";
                } else {
                    finalPrompt = promptPrefix + userMessageText
                            + "\n\n[VISION ANALYSIS RESULT - each item's calories listed]:\n" + nutritionAnalysis
                            + "\n\n[INSTRUCTION]: DO NOT record any calories. "
                            + "Step 1: Call askMemoryAgent to get today's remaining calorie budget for userId " + dynamicUserId + ". "
                            + "Step 2: Based on remaining budget and the items in the vision analysis, "
                            + "recommend exactly which items the user can eat and in what quantity. "
                            + "Be specific, e.g. '4 pieces California roll (112 kcal) + 2 salmon nigiri (110 kcal) = 222 kcal'. "
                            + "If entire platter exceeds budget, tell them which items to prioritize.";
                }

            } else {
                String intent = classifyIntent(userMessageText);
                System.out.println("[INTENT] " + intent + " → " + userMessageText);

                // ✅ 所有 prompt 都带上 locationContext
                String locationSuffix = locationContext.isBlank() ? "" : "\nUser's current location: " + locationContext;

                finalPrompt = switch (intent) {
                    case "simple" ->
                        promptPrefix + userMessageText
                        + "\n[ROUTE: simple chat, respond directly and briefly, do NOT call any sub-agents]";
                    case "search" ->
                        promptPrefix + userMessageText
                        + locationSuffix
                        + "\n[ROUTE: call askSearchAgent directly]";
                    case "nutrition" ->
                        promptPrefix + userMessageText
                        + "\n[ROUTE: call askNutritionAgent directly with userId " + dynamicUserId + "]";
                    case "memory" ->
                        promptPrefix + userMessageText
                        + "\n[ROUTE: call askMemoryAgent directly with userId " + dynamicUserId + "]";
                    default ->
                        promptPrefix + userMessageText
                        + locationSuffix
                        + "\nContext Area/Restaurant: " + locationContext;
                };
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
                        CompletableFuture.runAsync(() -> {
                            String replyText = agentReply.toString();
                            conversationService.saveMessage(dynamicUserId, finalSessionId, "user", finalUserMsg, null);
                            conversationService.saveMessage(dynamicUserId, finalSessionId, "agent", replyText, null);
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
    }
}