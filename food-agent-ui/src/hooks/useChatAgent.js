import { useState } from 'react';

export function useChatAgent({ userId, context, initialMessage }) {
  const [isChatOpen, setIsChatOpen] = useState(false);
  const [chatInput, setChatInput] = useState('');
  const [selectedImage, setSelectedImage] = useState(null);
  const [isTyping, setIsTyping] = useState(false);
  const [messages, setMessages] = useState([
    { role: 'agent', text: initialMessage || "Hi! I'm your AI Food Expert. Tell me what you're craving!" }
  ]);

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

    const userMessage = chatInput || "What is this and where can I find it?";
    const imageToSend = selectedImage;

    setChatInput('');
    setSelectedImage(null);
    setIsTyping(true);
    setMessages(prev => [...prev, { role: 'user', text: userMessage, image: imageToSend }]);

    try {
      const response = await fetch('http://localhost:8080/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId, message: userMessage, context, image: imageToSend })
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

  return {
    isChatOpen, setIsChatOpen,
    chatInput, setChatInput,
    selectedImage, setSelectedImage,
    isTyping, messages,
    handleImageUpload, handleSendMessage,
  };
}