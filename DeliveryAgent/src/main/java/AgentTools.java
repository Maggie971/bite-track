import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

// ✅ 核心设计：把三个子 agent 包装成 @Tool
// OrchestratorAgent 看到这些 tool，就能决定什么时候调哪个专家
public class AgentTools {

    private final SearchAgent searchAgent;
    private final NutritionAgent nutritionAgent;
    private final MemoryAgent memoryAgent;

    public AgentTools(SearchAgent searchAgent, NutritionAgent nutritionAgent, MemoryAgent memoryAgent) {
        this.searchAgent = searchAgent;
        this.nutritionAgent = nutritionAgent;
        this.memoryAgent = memoryAgent;
    }

    @Tool({
            "Delegate restaurant search and recommendation tasks to the Search specialist.",
            "Use this for: finding restaurants, getting reviews, recommending places to eat.",
            "Pass all relevant context: food type, location, user preferences if known."
    })
    public String askSearchAgent(
            @P("The full question or task for the search agent, including location and any preference constraints") String question
    ) {
        System.out.println("🔍 [ORCHESTRATOR → SearchAgent] " + question);
        String result = searchAgent.chat(question);
        System.out.println("🔍 [SearchAgent → ORCHESTRATOR] " + result.substring(0, Math.min(100, result.length())) + "...");
        return result;
    }

    @Tool({
            "Delegate nutrition and calorie tasks to the Nutrition specialist.",
            "Use this for: calorie lookup, nutrition info, processing image analysis results, setting calorie goals, recording meals.",
            "Also use this to check today's calorie summary before making meal recommendations."
    })
    public String askNutritionAgent(
            @P("The full question or task for the nutrition agent, including userId when relevant") String question
    ) {
        System.out.println("🥗 [ORCHESTRATOR → NutritionAgent] " + question);
        String result = nutritionAgent.chat(question);
        System.out.println("🥗 [NutritionAgent → ORCHESTRATOR] " + result.substring(0, Math.min(100, result.length())) + "...");
        return result;
    }

    @Tool({
            "Delegate user memory and personalization tasks to the Memory specialist.",
            "Use this for: fetching user taste profile, saving new preferences, getting browsing history, checking today's calorie budget.",
            "Always call this FIRST when making personalized recommendations."
    })
    public String askMemoryAgent(
            @P("The full question or task for the memory agent, including userId") String question
    ) {
        System.out.println("🧠 [ORCHESTRATOR → MemoryAgent] " + question);
        String result = memoryAgent.chat(question);
        System.out.println("🧠 [MemoryAgent → ORCHESTRATOR] " + result.substring(0, Math.min(100, result.length())) + "...");
        return result;
    }
}