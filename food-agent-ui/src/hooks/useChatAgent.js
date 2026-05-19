import { useState, useRef } from 'react';

const API = 'http://localhost:8080';

// ✅ 每次调用都生成唯一 sessionId，用时间戳区分
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
      body: JSON.stringify({
        userId,
        sessionId,
        role,
        text,
        timestamp: new Date().toISOString(),
      })
    });
  } catch (e) {
    // 静默失败
  }
}

export function useChatAgent({ userId, context, initialMessage }) {
  const defaultMessage = { role: 'agent', text: initialMessage || "Hi! I'm your AI Food Expert. Tell me what you're craving!" };

  const [isChatOpen, setIsChatOpen] = useState(false);
  const [chatInput, setChatInput] = useState('');
  const [selectedImage, setSelectedImage] = useState(null);
  const [isTyping, setIsTyping] = useState(false);
  const [messages, setMessages] = useState([defaultMessage]);

  // ✅ 每次组件挂载生成新 sessionId
  const sessionIdRef = useRef(generateSessionId(userId || 'guest'));

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
        body: JSON.stringify({
          userId,
          sessionId,
          message: userMessageText,
          context,
          image: imageToSend
        })
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

  // ✅ 开启新对话：重置消息 + 生成新 sessionId
  const startNewSession = () => {
    sessionIdRef.current = generateSessionId(userId || 'guest');
    setMessages([defaultMessage]);
    setChatInput('');
    setSelectedImage(null);
    setIsTyping(false);
  };

  // ✅ 加载历史会话：替换消息列表 + 切换 sessionId
  const loadSession = async (targetSessionId) => {
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
    userId,
  };
}