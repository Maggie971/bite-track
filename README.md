#   BiteTrack

FoodAgent is a multi-agent food discovery and delivery assistant that combines conversational AI, retrieval-augmented personalization, and image-based food recognition to help users find restaurants and meals tailored to their taste, nutrition goals, and past behavior.

The system is split into two independently deployable services: a Java backend (`DeliveryAgent`) that hosts the agent orchestration logic, and a React frontend (`food-agent-ui`) that provides the chat interface and restaurant discovery experience.

## Architecture

The backend follows an orchestrator pattern: a central `OrchestratorAgent` interprets user intent and delegates work to specialist agents, each backed by its own toolset.

```
                        ┌──────────────────────┐
                        │     ChatHandler       │
                        │  (HTTP entry point)   │
                        └──────────┬────────────┘
                                   │
                        ┌──────────▼────────────┐
                        │  ConversationService   │
                        └──────────┬────────────┘
                                   │
                        ┌──────────▼────────────┐
                        │   OrchestratorAgent     │
                        │  (intent routing +      │
                        │   task delegation)      │
                        └───┬───────┬────────┬───┘
                            │       │        │
                 ┌──────────▼─┐ ┌───▼─────┐ ┌▼────────────┐
                 │ SearchAgent │ │ NutritionAgent│ │ MemoryAgent │
                 └──────┬─────┘ └───┬─────┘ └┬────────────┘
                        │           │         │
              ┌─────────▼──┐ ┌──────▼─────┐ ┌▼────────────┐
              │FoodDelivery │ │Nutrition   │ │MemoryTools   │
              │Tools        │ │Tools       │ │(vector store)│
              └─────────────┘ └────────────┘ └──────────────┘

              ┌──────────────────────┐
              │  ImageAnalysisService │  ← analyzes user-uploaded food photos
              └──────────────────────┘

              ┌──────────────────────┐
              │   FootprintTools      │  ← tracks user behavioral footprints
              └──────────────────────┘
```

**OrchestratorAgent** owns the top-level conversation flow: it clarifies ambiguous user intent, decides which specialist agent should handle a request, and merges their outputs into a coherent response.

**SearchAgent** resolves restaurant and menu queries using `FoodDeliveryTools`, which wrap external food delivery / places data sources.

**NutritionAgent** reasons over calorie and nutrition data via `NutritionTools`, factoring dietary constraints into recommendations.

**MemoryAgent** maintains a two-tier memory system:
- Short-term: a sliding message window (`MessageWindowChatMemory`) for in-session context.
- Long-term: post-conversation summaries persisted to a MongoDB Atlas vector store, enabling cross-session personalization and preference-drift detection over time.

**FootprintTools** logs behavioral signals (searches, selections, ratings) that feed back into the personalization loop alongside taste embeddings.

**ImageAnalysisService** accepts user-uploaded food photos and extracts dish identification and nutritional estimates, which are then handed to `NutritionAgent` for downstream reasoning.

## Tech Stack

### Backend (`DeliveryAgent`)
- Java 17, Maven
- [LangChain4j](https://github.com/langchain4j/langchain4j) for agent orchestration and tool calling
- Google Gemini (`langchain4j-google-ai-gemini`) as the underlying LLM
- MongoDB Atlas Vector Search (`langchain4j-mongodb-atlas`) for taste embeddings and long-term memory
- Local BGE embedding model (`langchain4j-embeddings-bge-small-en-v15-q`) — runs on-device, no external embedding API key required
- Javalin as the lightweight HTTP server
- Jackson for JSON serialization
- OkHttp for outbound HTTP calls to external data sources
- Jedis for Redis-backed response caching
- dotenv-java for environment configuration

### Frontend (`food-agent-ui`)
- React 19 + Vite
- Tailwind CSS v4
- Clerk for authentication
- react-markdown for rendering agent responses
- react-rnd for the draggable/resizable chat window
- react-router-dom for client-side routing

## Features

- **Multi-agent orchestration**: user requests are decomposed and routed to the appropriate specialist agent rather than handled by a single monolithic prompt.
- **RAG-based personalization**: restaurant and dish recommendations are filtered through a vector search over the user's taste embeddings, reducing irrelevant suggestions.
- **Two-tier memory**: short-term conversational context plus long-term summarized preferences, enabling the assistant to recall user habits across sessions.
- **Image-based food recognition**: users can upload a photo of a meal, and `ImageAnalysisService` identifies the dish and estimates nutritional content.
- **Behavioral footprint tracking**: user interactions are logged and factored into future recommendations.
- **Floating chat interface**: a draggable, resizable chat widget (`ChatFab` / `ChatWindow`) that overlays the main restaurant discovery experience.
- **Response caching layer**: a Redis-backed cache (1-hour TTL) sits in front of third-party restaurant/places API calls in `FoodDeliveryTools`, reducing redundant external calls and lowering average search latency on repeated queries.

## Planned Enhancements

The following components are part of the system design but not yet implemented in the current codebase:

- **Payload trimming for LLM context**: pre-processing of external API JSON payloads to strip irrelevant fields before model ingestion, aimed at reducing token consumption on large responses.

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- Node.js 18+
- A MongoDB Atlas cluster with Vector Search enabled
- A Google AI (Gemini) API key
- A Clerk account for frontend authentication

### Backend Setup

```bash
cd DeliveryAgent

# create a .env file in the project root with:
# GEMINI_API_KEY=your_key_here
# MONGODB_URI=your_atlas_connection_string

mvn clean install
mvn exec:java -Dexec.mainClass="edu.neu.mgen.Main"
```

The server starts on the port configured in `Main.java` (default: check `Main.java` for the Javalin port binding).

### Frontend Setup

```bash
cd food-agent-ui

npm install

# create a .env file with:
# VITE_CLERK_PUBLISHABLE_KEY=your_clerk_key
# VITE_API_BASE_URL=http://localhost:<backend_port>

npm run dev
```

The app will be available at `http://localhost:5173` by default.

### Running Both Services

A `start.sh` script is included at the project root to launch both backend and frontend together:

```bash
./start.sh
```

## API Overview

The frontend communicates with the backend through a single chat endpoint exposed by `ChatHandler`, which forwards requests to `ConversationService`. All agent routing, tool invocation, and memory retrieval happen server-side; the frontend only needs to send user messages (and optionally an image) and render the streamed or returned response.

| Layer | Responsibility |
|---|---|
| `ChatHandler` | HTTP entry point, request/response mapping |
| `ConversationService` | Session management, delegates to `OrchestratorAgent` |
| `OrchestratorAgent` | Intent clarification, agent routing |
| `SearchAgent` / `NutritionAgent` / `MemoryAgent` | Domain-specific reasoning |
| `*Tools` classes | External integrations (delivery data, nutrition data, memory store, footprint logging) |
| `ImageAnalysisService` | Food photo recognition |

## Project Structure

```
.
├── DeliveryAgent/          # Java backend
│   └── src/main/java/edu/
│       ├── AgentTools.java
│       ├── ChatHandler.java
│       ├── ConversationService.java
│       ├── FoodDeliveryTools.java
│       ├── FootprintTools.java
│       ├── ImageAnalysisService.java
│       ├── Main.java
│       ├── MemoryAgent.java
│       ├── MemoryTools.java
│       ├── NutritionAgent.java
│       ├── NutritionTools.java
│       ├── OrchestratorAgent.java
│       └── SearchAgent.java
├── food-agent-ui/          # React frontend
│   └── src/
│       ├── components/     # ChatFab, ChatWindow
│       ├── hooks/          # useChatAgent, useRestaurants
│       ├── pages/          # Landing, Results, RestaurantDetail, RestaurantList
│       └── utils/          # footprint.js, mapsHelper.js
└── start.sh                # launches both services
```
