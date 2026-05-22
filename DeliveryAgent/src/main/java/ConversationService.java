import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

public class ConversationService {

    private final MongoCollection<Document> conversationsCollection;

    public ConversationService(MongoCollection<Document> conversationsCollection) {
        this.conversationsCollection = conversationsCollection;
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public void saveMessage(String userId, String sessionId, String role, String text, String timestamp) {
        if (userId == null || userId.equals("guest") || text == null || text.isBlank()) return;

        String ts = (timestamp != null && !timestamp.isBlank()) ? timestamp : now();

        Document existing = conversationsCollection.find(Filters.eq("sessionId", sessionId)).first();
        if (existing == null) {
            Document session = new Document()
                    .append("sessionId", sessionId)
                    .append("userId", userId)
                    .append("title", "New conversation")
                    .append("createdAt", ts)
                    .append("updatedAt", ts)
                    .append("summarized", false)   // ✅ 新增：标记是否已生成摘要
                    .append("hidden", false)
                    .append("messages", new ArrayList<>());
            conversationsCollection.insertOne(session);
        }

        conversationsCollection.updateOne(
                Filters.eq("sessionId", sessionId),
                new Document("$push", new Document("messages", new Document()
                        .append("role", role)
                        .append("text", text)
                        .append("timestamp", ts)
                ))
        );

        conversationsCollection.updateOne(
                Filters.eq("sessionId", sessionId),
                new Document("$set", new Document("updatedAt", ts))
        );

        System.out.println("[CONV] Saved [" + role + "] to session: " + sessionId);
    }

    public void updateTitle(String sessionId, String title) {
        if (title == null || title.isBlank()) return;
        conversationsCollection.updateOne(
                Filters.eq("sessionId", sessionId),
                new Document("$set", new Document("title", title))
        );
        System.out.println("[CONV] Updated title for " + sessionId + ": " + title);
    }

    public boolean hasCustomTitle(String sessionId) {
        Document doc = conversationsCollection.find(Filters.eq("sessionId", sessionId)).first();
        if (doc == null) return false;
        String title = doc.getString("title");
        return title != null && !title.equals("New conversation");
    }

    // ─────────────────────────────────────────────
    // ✅ 新增：检查这个 session 是否已经生成过摘要
    // ─────────────────────────────────────────────
    public boolean hasSummary(String sessionId) {
        Document doc = conversationsCollection.find(Filters.eq("sessionId", sessionId)).first();
        if (doc == null) return false;
        Boolean summarized = doc.getBoolean("summarized");
        return Boolean.TRUE.equals(summarized);
    }

    // ─────────────────────────────────────────────
    // ✅ 新增：标记这个 session 已生成摘要
    // ─────────────────────────────────────────────
    public void markSummarized(String sessionId) {
        conversationsCollection.updateOne(
                Filters.eq("sessionId", sessionId),
                new Document("$set", new Document("summarized", true))
        );
        System.out.println("[CONV] Marked session as summarized: " + sessionId);
    }

    // ─────────────────────────────────────────────
    // ✅ 新增：拿到这个 session 的所有消息，用于生成摘要
    // 只返回 user 和 agent 的文字内容，拼成对话文本
    // ─────────────────────────────────────────────
    public String getConversationText(String sessionId) {
        Document doc = conversationsCollection.find(Filters.eq("sessionId", sessionId)).first();
        if (doc == null) return "";

        List<Document> messages = doc.getList("messages", Document.class);
        if (messages == null || messages.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Document msg : messages) {
            String role = msg.getString("role");
            String text = msg.getString("text");
            if (text == null || text.isBlank()) continue;
            // 截取每条消息前200字，避免 prompt 太长
            String truncated = text.length() > 200 ? text.substring(0, 200) + "..." : text;
            sb.append(role.equals("user") ? "User: " : "Agent: ")
              .append(truncated).append("\n");
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────
    // ✅ 新增：检查 session 是否有至少一条用户消息
    // 空对话不需要生成摘要
    // ─────────────────────────────────────────────
    public boolean hasUserMessages(String sessionId) {
        Document doc = conversationsCollection.find(Filters.eq("sessionId", sessionId)).first();
        if (doc == null) return false;
        List<Document> messages = doc.getList("messages", Document.class);
        if (messages == null) return false;
        return messages.stream().anyMatch(m -> "user".equals(m.getString("role")));
    }

    public List<Map<String, String>> getSessionList(String userId, int limit) {
        var sessions = conversationsCollection.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.ne("hidden", true)
        )
        ).sort(Sorts.descending("updatedAt")).limit(limit);

        List<Map<String, String>> results = new ArrayList<>();
        for (Document doc : sessions) {
            results.add(Map.of(
                    "sessionId", doc.getString("sessionId") != null ? doc.getString("sessionId") : "",
                    "title", doc.getString("title") != null ? doc.getString("title") : "Conversation",
                    "updatedAt", doc.getString("updatedAt") != null ? doc.getString("updatedAt") : ""
            ));
        }
        return results;
    }

    public List<Map<String, String>> getMessages(String sessionId) {
        Document doc = conversationsCollection.find(Filters.eq("sessionId", sessionId)).first();
        if (doc == null) return List.of();

        List<Document> messages = doc.getList("messages", Document.class);
        List<Map<String, String>> result = new ArrayList<>();
        if (messages != null) {
            for (Document msg : messages) {
                result.add(Map.of(
                        "role", msg.getString("role") != null ? msg.getString("role") : "",
                        "text", msg.getString("text") != null ? msg.getString("text") : "",
                        "timestamp", msg.getString("timestamp") != null ? msg.getString("timestamp") : ""
                ));
            }
        }
        return result;
    }
}