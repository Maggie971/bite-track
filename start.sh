#!/bin/bash
echo "🚀 Starting FoodAgent..."

# 启动后端
echo "📦 Starting Java backend..."
cd DeliveryAgent
mvn compile exec:java -Dexec.mainClass="Main" &
BACKEND_PID=$!
echo "✅ Backend started (PID: $BACKEND_PID)"

# 等后端启动
sleep 5

# 启动前端
echo "⚛️  Starting React frontend..."
cd ../food-agent-ui  # 改成你的前端目录名
npm run dev &
FRONTEND_PID=$!
echo "✅ Frontend started (PID: $FRONTEND_PID)"

echo ""
echo "🎉 FoodAgent running!"
echo "   Frontend: http://localhost:5173"
echo "   Backend:  http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop both"

# 等待，Ctrl+C 时同时关掉两个
wait