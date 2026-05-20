import java.time.LocalDate;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

public class MemoryTools {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public MemoryTools(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
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
        // ✅ 方案A：加时间戳，让 agent 知道哪条最新
        String memoryText = "User Profile [ID: " + userId + "] [Updated: " + today + "]: " + newPreference;
        var newEmbedding = embeddingModel.embed(memoryText).content();

        // ✅ 方案B：检测冲突
        // 用新偏好内容搜索，找语义相近的旧记忆
        var conflictSearch = embeddingStore.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(newEmbedding)
                .maxResults(3)
                .minScore(0.80)  // 0.80 能捞到"loves spicy" vs "dislikes spicy"这类反义冲突
                .build()
        );

        boolean foundConflict = false;
        for (var match : conflictSearch.matches()) {
            String existingText = match.embedded().text();

            // 只处理同一用户的记忆
            if (!existingText.contains("[ID: " + userId + "]")) continue;

            // 完全重复（相似度 > 0.92）→ 跳过，不存
            if (match.score() > 0.92) {
                System.out.println("⚠️ [MEMORY SKIP] Duplicate detected, skipping.");
                System.out.println("   Existing: " + existingText);
                return;
            }

            // 相似但不完全重复（0.80~0.92）→ 可能是冲突，删旧存新
            // 例如 "loves spicy" 和 "dislikes spicy" 相似度约 0.83
            System.out.println("🔄 [MEMORY CONFLICT] Found conflicting preference, replacing:");
            System.out.println("   Old: " + existingText);
            System.out.println("   New: " + memoryText);

            // 删除旧记忆（通过 ID 删除）
            embeddingStore.remove(match.embeddingId());
            foundConflict = true;
        }

        // 存新记忆
        embeddingStore.add(newEmbedding, TextSegment.from(memoryText));
        if (foundConflict) {
            System.out.println("✅ [MEMORY UPDATED] Replaced old preference with new one: " + memoryText);
        } else {
            System.out.println("🤖 [MEMORY SAVED] " + memoryText);
        }
    }

    @Tool({
        "Use this tool to retrieve the full preference profile of a user.",
        "Call this at the START of a conversation or when making personalized recommendations.",
        "Input the user's ID."
    })
    public String getUserProfile(
            @P("The user's ID") String userId
    ) {
        System.out.println("🔍 [TOOL TRIGGERED] Fetching profile for: " + userId);

        try {
            String query = "User Profile [ID: " + userId + "]";
            var embedding = embeddingModel.embed(query).content();

            var results = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(10)
                    .minScore(0.5)
                    .build()
            );

            if (results.matches().isEmpty()) {
                return "No profile found for user: " + userId;
            }

            // ✅ 方案A：返回时提示 agent 优先信最新的
            StringBuilder profile = new StringBuilder(
                "User Profile for [" + userId + "] (IMPORTANT: if there are conflicting preferences, trust the one with the most recent [Updated: date]):\n"
            );
            for (var match : results.matches()) {
                profile.append("- ").append(match.embedded().text()).append("\n");
            }

            System.out.println("🔍 [PROFILE FOUND]\n" + profile);
            return profile.toString();

        } catch (Exception e) {
            System.err.println("[ERROR] getUserProfile failed: " + e.getMessage());
            return "Error retrieving profile: " + e.getMessage();
        }
    }
}