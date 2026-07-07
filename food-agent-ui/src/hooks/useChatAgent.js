import { useState, useRef } from 'react';

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080';

function generateSessionId(userId) {
  const ts = Date.now();
  return `${userId}_${ts}`;
}

async function saveMessage(userId, sessionId, role, text) {
  if (!userId || userId === 'guest' || !text.trim()) return;
  try {
    await fetch(`${API}/api/conversations/message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId, sessionId, role, text, timestamp: new Date().toISOString() })
    });
  } catch (e) {}
}

// ✅ 触发摘要，三个地方都会调这个
async function triggerSummary(userId, sessionId) {
  if (!userId || userId === 'guest' || !sessionId) return;
  try {
    await fetch(`${API}/api/conversations/summarize`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId, sessionId })
    });
    console.log('[📝 Summary triggered] session:', sessionId);
  } catch (e) {
    // 静默失败
  }
}

export function useChatAgent({ userId, context, initialMessage }) {
  const defaultMessage = {
    role: 'agent',
    text: initialMessage || "Hi! I'm your AI Food Expert. Tell me what you're craving!"
  };

  const [isChatOpen, setIsChatOpen] = useState(false);
  const [chatInput, setChatInput] = useState('');
  const [selectedImage, setSelectedImage] = useState(null);
  const [isTyping, setIsTyping] = useState(false);
  const [messages, setMessages] = useState([defaultMessage]);
  const [userLocation, setUserLocation] = useState('');

  const sessionIdRef = useRef(generateSessionId(userId || 'guest'));

  const saveLocation = async (location) => {
    if (!userId || userId === 'guest' || !location) return;
    try {
      await fetch(`${API}/api/location`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, location })
      });
    } catch (e) {}
  };

  const handleImageUpload = (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => setSelectedImage(reader.result);
      reader.readAsDataURL(file);
    }
  };

  const handleSendMessage = async (e) => {
    e.preventDefault();
    if (!chatInput.trim() && !selectedImage) return;

    const userMessageText = chatInput || "What is this and where can I find it?";
    const imageToSend = selectedImage;
    const sessionId = sessionIdRef.current;

    setChatInput('');
    setSelectedImage(null);
    setIsTyping(true);
    setMessages(prev => [...prev, { role: 'user', text: userMessageText, image: imageToSend }]);

    try {
      const response = await fetch(`${API}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, sessionId, message: userMessageText, context: userLocation, image: imageToSend })
      });

      if (!response.ok) throw new Error("Server error");

      setIsTyping(false);
      setMessages(prev => [...prev, { role: 'agent', text: "" }]);

      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let agentText = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        agentText += decoder.decode(value, { stream: true });
        setMessages(prev => {
          const updated = [...prev];
          updated[updated.length - 1].text = agentText;
          return updated;
        });
      }
    } catch {
      setMessages(prev => [...prev, { role: 'agent', text: "Backend connection failed. Please ensure Java is running on 8080." }]);
      setIsTyping(false);
    }
  };

  // ✅ 关闭聊天框时触发摘要
  const handleCloseChatWithSummary = () => {
    triggerSummary(userId, sessionIdRef.current);
    setIsChatOpen(false);
  };

  // ✅ 新建对话时触发摘要，再重置
  const startNewSession = () => {
    triggerSummary(userId, sessionIdRef.current);
    sessionIdRef.current = generateSessionId(userId || 'guest');
    setMessages([defaultMessage]);
    setChatInput('');
    setSelectedImage(null);
    setIsTyping(false);
  };

  // ✅ 加载历史会话时，先对当前 session 触发摘要
  const loadSession = async (targetSessionId) => {
    triggerSummary(userId, sessionIdRef.current);
    try {
      const res = await fetch(`${API}/api/conversations/${targetSessionId}`);
      if (!res.ok) return;
      const data = await res.json();
      if (data.length > 0) {
        sessionIdRef.current = targetSessionId;
        setMessages(data.map(m => ({ role: m.role, text: m.text })));
        setIsChatOpen(true);
      }
    } catch (e) {
      console.error('Failed to load session', e);
    }
  };

  return {
    isChatOpen, setIsChatOpen,
    chatInput, setChatInput,
    selectedImage, setSelectedImage,
    isTyping, messages,
    handleImageUpload, handleSendMessage,
    sessionId: sessionIdRef.current,
    loadSession,
    startNewSession,
    handleCloseChatWithSummary,  // ✅ 新增，给 ChatWindow 用
    userId,
    userLocation, setUserLocation,
    saveLocation, 
  };
}