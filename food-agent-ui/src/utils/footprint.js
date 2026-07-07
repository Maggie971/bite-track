const API = import.meta.env.VITE_API_URL || 'http://localhost:8080';

// 存足迹（搜索或浏览）— 走独立接口，不经过 agent
export async function recordFootprint(userId, type, content) {
  if (!userId || userId === 'guest') return;
  try {
    await fetch(`${API_BASE}/api/footprint`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId, type, content })
    });
    console.log(`[👣 Footprint] [${type}] ${content}`);
  } catch (e) {
    // 静默失败，不影响用户体验
  }
}

// 拉取最近搜索历史，给搜索框下拉用
export async function fetchSearchHistory(userId, limit = 5) {
  if (!userId || userId === 'guest') return [];
  try {
    const res = await fetch(
      `${API_BASE}/api/footprint/history?userId=${userId}&type=search&limit=${limit}`
    );
    if (!res.ok) return [];
    return await res.json(); // string[]
  } catch (e) {
    return [];
  }
}