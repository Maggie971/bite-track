import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

public class MemoryTools {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingStore<TextSegment> summaryStore;
    private final EmbeddingModel embeddingModel;

    public MemoryTools(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingStore<TextSegment> summaryStore,
            EmbeddingModel embeddingModel
    ) {
        this.embeddingStore = embeddingStore;
        this.summaryStore = summaryStore;
        this.embeddingModel = embeddingModel;
    }

    @Tool({
        "Use this tool ONLY when the user explicitly shares a new food preference, allergy, dietary restriction, or habit.",
        "Extract the user's ID from the prompt prefix.",
        "Save the preference clearly and concisely."
    })
    public void saveUserPreference(
            @P("The user's ID") String userId,
            @P("The new preference, e.g. 'dislikes spicy food' or 'is vegetarian'") String newPreference
    ) {
        String today = LocalDate.now().toString();
        String memoryText = "User Profile [ID: " + userId + "] [Updated: " + today + "]: " + newPreference;
        var newEmbedding = embeddingModel.embed(memoryText).content();

        // ✅ 检测完全重复（score > 0.92）才跳过，不再尝试删除旧条目
        // MongoDbEmbeddingStore 0.36.0 不支持 remove()，靠时间戳让 agent 判断最新
        var conflictSearch = embeddingStore.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(newEmbedding)
                .maxResults(3).minScore(0.92)
                .build()
        );

        for (var match : conflictSearch.matches()) {
            String existingText = match.embedded().text();
            if (!existingText.contains("[ID: " + userId + "]")) continue;
            // 完全重复 → 跳过不存
            System.out.println("⚠️ [MEMORY SKIP] Duplicate, skipping: " + existingText);
            return;
        }

        // 直接存新的，旧的留着让 agent 靠 [Updated: date] 判断最新
        embeddingStore.add(newEmbedding, TextSegment.from(memoryText));
        System.out.println("🤖 [PREF SAVED] " + memoryText);
    }

    @Tool({
        "Use this tool to retrieve the user's taste preferences and calorie goals.",
        "Call this when making personalized recommendations or when user asks about their profile.",
        "Input the user's ID."
    })
    public String getUserProfile(
            @P("The user's ID") String userId
    ) {
        System.out.println("🔍 [PROFILE] Fetching preferences for: " + userId);

        try {
            String query = "User Profile [ID: " + userId + "]";
            var embedding = embeddingModel.embed(query).content();

            var results = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(10).minScore(0.5)
                    .build()
            );

            List<String> profiles = new ArrayList<>();
            for (var match : results.matches()) {
                String text = match.embedded().text();
                if (!text.contains("[ID: " + userId + "]")) continue;
                profiles.add(text);
            }

            if (profiles.isEmpty()) return "No taste preferences found for user: " + userId;

            StringBuilder sb = new StringBuilder("=== Taste Preferences for [" + userId + "] ===\n");
            sb.append("(If conflicts exist, trust the most recent [Updated: date])\n");
            profiles.forEach(p -> sb.append("- ").append(p).append("\n"));

            System.out.println("🔍 [PROFILE FOUND] " + profiles.size() + " entries");
            return sb.toString();

        } catch (Exception e) {
            System.err.println("[ERROR] getUserProfile: " + e.getMessage());
            return "Error retrieving profile: " + e.getMessage();
        }
    }

    @Tool({
        "Use this tool when the user asks about past conversations, what they talked about before, or what they ate recently.",
        "Examples: 'what did we talk about last time', 'do you remember our last chat', 'what did I eat yesterday'.",
        "Returns the most recent conversation summaries for this user.",
        "Input the user's ID."
    })
    public String getRecentSummaries(
            @P("The user's ID") String userId
    ) {
        System.out.println("📖 [SUMMARIES] Fetching conversation history for: " + userId);

        try {
            String query = "Conversation Summary [ID: " + userId + "]";
            var embedding = embeddingModel.embed(query).content();

            var results = summaryStore.search(
                EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(10).minScore(0.5)
                    .build()
            );

            List<String> summaries = new ArrayList<>();
            for (var match : results.matches()) {
                String text = match.embedded().text();
                if (!text.contains("[ID: " + userId + "]")) continue;
                summaries.add(text);
            }

            if (summaries.isEmpty()) return "No past conversation history found for user: " + userId;

            summaries.sort((a, b) -> b.compareTo(a));
            List<String> recent = summaries.subList(0, Math.min(3, summaries.size()));

            StringBuilder sb = new StringBuilder("=== Recent Conversation History (latest first) ===\n");
            recent.forEach(s -> sb.append("- ").append(s).append("\n"));

            System.out.println("📖 [SUMMARIES FOUND] " + recent.size() + " recent summaries");
            return sb.toString();

        } catch (Exception e) {
            System.err.println("[ERROR] getRecentSummaries: " + e.getMessage());
            return "Error retrieving summaries: " + e.getMessage();
        }
    }
}