import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;

public class MemoryTools {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    // 构造函数：把我们在 Main.java 里建好的数据库和向量模型传进来
    public MemoryTools(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Tool({
            "Use this tool ONLY when the user explicitly shares a new food preference, allergy, dietary restriction, or habit.",
            "Extract the user's ID from the prompt prefix.",
            "Save the preference clearly and concisely."
    })
    public void saveUserPreference(String userId, String newPreference) {
        String memoryText = "User Profile [ID: " + userId + "]: " + newPreference;

        // 存之前先查有没有语义相似的记忆
        var embedding = embeddingModel.embed(memoryText).content();
        var existing = embeddingStore.search(
                dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(1)
                        .minScore(0.92)   // 相似度很高才认为是重复
                        .build()
        );

        if (!existing.matches().isEmpty()) {
            String found = existing.matches().get(0).embedded().text();
            System.out.println("⚠️ [MEMORY SKIP] Too similar to existing, skipping save.");
            System.out.println("   Existing: " + found);
            System.out.println("   New:      " + memoryText);
            return;  // 不存了
        }

        embeddingStore.add(embedding, TextSegment.from(memoryText));
        System.out.println("🤖 [MEMORY SAVED] " + memoryText);
    }

    @Tool({
            "Use this tool to retrieve the full preference profile of a user.",
            "Call this at the START of a conversation or when making personalized recommendations.",
            "Input the user's ID extracted from the prompt prefix."
    })
    public String getUserProfile(String userId) {
        System.out.println("🔍 [TOOL TRIGGERED] Fetching profile for: " + userId);

        try {
            // 用用户ID作为查询，检索所有相关记忆
            String query = "User Profile [ID: " + userId + "]";
            var embedding = embeddingModel.embed(query).content();

            var results = embeddingStore.search(
                    dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                            .queryEmbedding(embedding)
                            .maxResults(10)      // 最多拉10条记忆
                            .minScore(0.5)       // 稍微放宽阈值，确保捞全
                            .build()
            );

            if (results.matches().isEmpty()) {
                return "No profile found for user: " + userId;
            }

            StringBuilder profile = new StringBuilder("User Profile for [" + userId + "]:\n");
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