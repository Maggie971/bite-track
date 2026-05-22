import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

public class FootprintTools {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final MongoCollection<Document> historyCollection;

    public FootprintTools(EmbeddingStore<TextSegment> embeddingStore,
                          EmbeddingModel embeddingModel,
                          MongoDatabase mongoDatabase) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.historyCollection = mongoDatabase.getCollection("user_history");
    }

    private String getDietDay() {
        LocalDateTime now = LocalDateTime.now();
        if (now.toLocalTime().isBefore(LocalTime.of(6, 0))) {
            return now.toLocalDate().minusDays(1).toString();
        }
        return now.toLocalDate().toString();
    }

    private String getNowTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    @Tool({
        "Use this tool to record what the user searched for or which restaurant they viewed.",
        "Call this whenever the user mentions searching for food or asks about a specific restaurant.",
        "This builds a browsing history for better future recommendations."
    })
    public void saveFootprint(
            @P("The user's ID") String userId,
            @P("Type: 'search' or 'view'") String type,
            @P("Description, e.g., 'Searched sushi in Walnut Creek' or 'Viewed Tanoshi Japanese Bistro'") String content
    ) {
        String dietDay = getDietDay();
        Document doc = new Document()
                .append("userId", userId)
                .append("type", type)
                .append("content", content)
                .append("calories", null)
                .append("dietDay", dietDay)
                .append("timestamp", getNowTimestamp());

        historyCollection.insertOne(doc);
        System.out.println("👣 [FOOTPRINT SAVED] [" + type + "] " + content);
    }

    @Tool({
        "Use this tool to record calories the user has ALREADY consumed.",
        "ONLY call this when the user explicitly says they ATE or FINISHED eating something.",
        "Valid triggers: 'I ate', 'I had', 'I just ate', 'I finished eating'.",
        "DO NOT call this if the user says 'I want to eat', 'I am thinking of eating',",
        "'can I eat', 'what if I eat', 'how many calories in' — those are hypothetical or questions.",
        "Estimate calories reasonably if the user does not specify an amount."
    })
    public void saveCalorieRecord(
            @P("The user's ID") String userId,
            @P("Description of what was eaten, e.g., 'Ramen with chashu and egg'") String foodDescription,
            @P("Estimated calories as integer, e.g., 650") int calories
    ) {
        String dietDay = getDietDay();
        Document doc = new Document()
                .append("userId", userId)
                .append("type", "calorie")
                .append("content", foodDescription)
                .append("calories", calories)
                .append("dietDay", dietDay)
                .append("timestamp", getNowTimestamp());

        historyCollection.insertOne(doc);
        System.out.println("🍽️ [CALORIE SAVED] " + foodDescription + " → " + calories + " kcal (DietDay: " + dietDay + ")");
    }

    @Tool({
        "Use this tool when the user sets or updates their daily calorie goal.",
        "Example triggers: 'my goal is 1800 calories', 'I want to eat 1500 kcal today', 'set my calorie target to 2200'.",
        "This goal is used to calculate remaining calories for the day."
    })
    public void saveCalorieGoal(
            @P("The user's ID") String userId,
            @P("Daily calorie goal as integer, e.g., 1800") int goalCalories
    ) {
        String dietDay = getDietDay();
        String today = LocalDate.now().toString();

        // 普通 MongoDB：upsert 今天的目标（只保留最新）
        historyCollection.deleteMany(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", "goal"),
                Filters.eq("dietDay", dietDay)
        ));

        Document doc = new Document()
                .append("userId", userId)
                .append("type", "goal")
                .append("content", "Daily calorie goal: " + goalCalories + " kcal")
                .append("calories", goalCalories)
                .append("dietDay", dietDay)
                .append("timestamp", getNowTimestamp());

        historyCollection.insertOne(doc);

        // ✅ 向量库：直接存新的，不再尝试删旧的（remove() 不支持）
        // agent 靠 [Updated: date] 判断最新目标
        String newMemoryText = "User Calorie Goal [ID: " + userId + "] [Updated: " + today + "]: daily calorie target is " + goalCalories + " kcal";
        var newEmbedding = embeddingModel.embed(newMemoryText).content();
        embeddingStore.add(newEmbedding, TextSegment.from(newMemoryText));

        System.out.println("🎯 [GOAL SAVED] " + userId + " → " + goalCalories + " kcal/day");
        System.out.println("🧠 [GOAL MEMORY SAVED] " + newMemoryText);
    }

    @Tool({
        "Use this tool to get the user's calorie summary for today.",
        "Call this when user asks 'how many calories today', 'what's my calorie count', or 'how much can I still eat'.",
        "Also call this before making meal recommendations to know remaining calorie budget."
    })
    public String getTodayCalories(
            @P("The user's ID") String userId
    ) {
        String dietDay = getDietDay();
        System.out.println("📊 [CALORIE QUERY] userId=" + userId + " dietDay=" + dietDay);

        Document goalDoc = historyCollection.find(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", "goal"),
                Filters.eq("dietDay", dietDay)
        )).sort(Sorts.descending("timestamp")).first();

        int dailyGoal = 2000;
        String goalNote = "";

        if (goalDoc != null) {
            dailyGoal = goalDoc.getInteger("calories", 2000);
        } else {
            try {
                String query = "User Calorie Goal [ID: " + userId + "] daily calorie target";
                var embedding = embeddingModel.embed(query).content();
                var results = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(3).minScore(0.75)
                        .build()
                );

                boolean found = false;
                for (var match : results.matches()) {
                    String memText = match.embedded().text();
                    if (!memText.contains("[ID: " + userId + "]")) continue;
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("is (\\d+) kcal").matcher(memText);
                    if (m.find()) {
                        dailyGoal = Integer.parseInt(m.group(1));
                        System.out.println("🧠 [GOAL FROM MEMORY] " + dailyGoal + " kcal");
                        found = true;
                        break;
                    }
                }
                if (!found) goalNote = " (default — tell me your goal and I'll remember it!)";
            } catch (Exception e) {
                goalNote = " (default, you can set your own goal by telling me!)";
            }
        }

        var records = historyCollection.find(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", "calorie"),
                Filters.eq("dietDay", dietDay)
        )).sort(Sorts.ascending("timestamp"));

        int totalCalories = 0;
        StringBuilder breakdown = new StringBuilder();
        for (Document record : records) {
            int cal = record.getInteger("calories", 0);
            String content = record.getString("content");
            String time = record.getString("timestamp");
            String timeShort = time != null && time.length() >= 16 ? time.substring(11, 16) : "";
            totalCalories += cal;
            breakdown.append("  • [").append(timeShort).append("] ")
                    .append(content).append(": ").append(cal).append(" kcal\n");
        }

        int remaining = dailyGoal - totalCalories;
        String status;
        if (totalCalories == 0) {
            status = "No meals logged yet today!";
        } else if (remaining > 0) {
            status = "✅ " + remaining + " kcal remaining today.";
        } else {
            status = "⚠️ Exceeded daily goal by " + Math.abs(remaining) + " kcal!";
        }

        String result = String.format(
                "📊 Calorie Summary (DietDay: %s)\n%s\n🔥 Total: %d / %d kcal%s\n%s",
                dietDay,
                breakdown.length() > 0 ? breakdown.toString() : "  (no meals logged)\n",
                totalCalories, dailyGoal, goalNote,
                status
        );

        System.out.println("[CALORIE RESULT]\n" + result);
        return result;
    }

    @Tool({
        "Use this tool to get the user's recent search and restaurant browsing history.",
        "Call this when making personalized recommendations to avoid suggesting places they've already visited.",
        "Also useful when user asks 'what have I been searching for lately'."
    })
    public String getRecentHistory(
            @P("The user's ID") String userId
    ) {
        System.out.println("🔍 [HISTORY QUERY] userId=" + userId);

        var records = historyCollection.find(Filters.and(
                Filters.eq("userId", userId),
                Filters.in("type", "search", "view")
        )).sort(Sorts.descending("timestamp")).limit(10);

        StringBuilder history = new StringBuilder("Recent activity:\n");
        int count = 0;
        for (Document record : records) {
            String type = record.getString("type");
            String content = record.getString("content");
            String dietDay = record.getString("dietDay");
            history.append("  • [").append(dietDay).append("] [").append(type).append("] ").append(content).append("\n");
            count++;
        }

        if (count == 0) return "No browsing history found for this user.";

        System.out.println("[HISTORY RESULT]\n" + history);
        return history.toString();
    }
    public void saveUserLocation(String userId, String location) {
        historyCollection.deleteMany(Filters.eq("userId", userId));  // 不对，只删location类型
        // 先删旧的location记录
        historyCollection.deleteMany(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", "location")
        ));
        Document doc = new Document()
                .append("userId", userId)
                .append("type", "location")
                .append("content", location)
                .append("timestamp", getNowTimestamp());
        historyCollection.insertOne(doc);
        System.out.println("📍 [LOCATION SAVED] " + userId + " → " + location);
    }
    
    public String getUserLocation(String userId) {
        Document doc = historyCollection.find(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("type", "location")
        )).sort(Sorts.descending("timestamp")).first();
        return doc != null ? doc.getString("content") : null;
    }
}