import React, { useState, useEffect, useRef } from 'react';
import { Rnd } from 'react-rnd';
import ReactMarkdown from 'react-markdown';
import { X, ImagePlus, History, ChevronLeft, SquarePen } from 'lucide-react';

const API = 'http://localhost:8080';

export default function ChatWindow({
  isChatOpen, setIsChatOpen,
  messages, isTyping,
  chatInput, setChatInput,
  selectedImage, setSelectedImage,
  handleImageUpload, handleSendMessage,
  inputPlaceholder = "Ask me anything...",
  userId,
  loadSession,
  startNewSession,
}) {
  const [showHistory, setShowHistory] = useState(false);
  const [sessions, setSessions] = useState([]);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleOpenHistory = async () => {
    setShowHistory(true);
    if (!userId || userId === 'guest') return;
    setLoadingSessions(true);
    try {
      const res = await fetch(`${API}/api/conversations?userId=${userId}`);
      const data = await res.json();
      setSessions(data);
    } catch (e) {
      console.error('Failed to fetch sessions', e);
    } finally {
      setLoadingSessions(false);
    }
  };

  const handleSelectSession = async (sessionId) => {
    await loadSession(sessionId);
    setShowHistory(false);
  };

  // ✅ 新对话：关闭历史面板 + 重置消息
  const handleNewChat = () => {
    startNewSession();
    setShowHistory(false);
  };

  const formatTime = (isoString) => {
    if (!isoString) return '';
    try {
      const date = new Date(isoString);
      const now = new Date();
      const diffDays = Math.floor((now - date) / (1000 * 60 * 60 * 24));
      if (diffDays === 0) return 'Today';
      if (diffDays === 1) return 'Yesterday';
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    } catch { return ''; }
  };

  if (!isChatOpen) return null;

  return (
    <Rnd
      default={{
        x: window.innerWidth / 2 + 640 - 424 - 24,
        y: Math.max(window.innerHeight - 574, 16),
        width: Math.min(400, window.innerWidth - 32),
        height: Math.min(550, window.innerHeight - 100),
      }}
      minWidth={320}
      minHeight={400}
      bounds="window"
      dragHandleClassName="chat-drag-handle"
      style={{ position: 'fixed', zIndex: 9999 }}
    >
      <div className="w-full h-full bg-white rounded-2xl shadow-[0_20px_60px_rgba(0,0,0,0.25)] border border-gray-200 flex flex-col overflow-hidden">

        {/* 头部 */}
        <div className="chat-drag-handle bg-blue-600 text-white px-4 py-3 flex items-center justify-between shadow-md cursor-move select-none">
          <div className="flex items-center space-x-2">
            {showHistory ? (
              <button
                onMouseDown={(e) => e.stopPropagation()}
                onClick={() => setShowHistory(false)}
                className="hover:bg-blue-700 p-1 rounded-md transition-colors cursor-pointer"
              >
                <ChevronLeft size={18} />
              </button>
            ) : (
              <span className="text-xl">✨</span>
            )}
            <span className="font-bold text-sm">
              {showHistory ? 'Chat History' : 'FoodAgent Expert'}
            </span>
          </div>

          <div className="flex items-center space-x-1">
            {/* ✅ 新对话按钮 */}
            <button
              onMouseDown={(e) => e.stopPropagation()}
              onClick={handleNewChat}
              className="hover:bg-blue-700 p-1 rounded-md transition-colors cursor-pointer"
              title="New conversation"
            >
              <SquarePen size={18} />
            </button>

            {/* 历史按钮 */}
            {!showHistory && (
              <button
                onMouseDown={(e) => e.stopPropagation()}
                onClick={handleOpenHistory}
                className="hover:bg-blue-700 p-1 rounded-md transition-colors cursor-pointer"
                title="View chat history"
              >
                <History size={18} />
              </button>
            )}

            {/* 关闭按钮 */}
            <button
              onMouseDown={(e) => e.stopPropagation()}
              onClick={() => setIsChatOpen(false)}
              className="hover:bg-blue-700 p-1 rounded-md transition-colors cursor-pointer"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* 历史会话面板 */}
        {showHistory ? (
          <div className="flex-grow overflow-y-auto bg-gray-50 p-3">
            {loadingSessions ? (
              <div className="flex justify-center py-8 text-gray-400 text-sm">Loading...</div>
            ) : sessions.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-gray-400 text-sm">
                <History size={32} className="mb-3 opacity-30" />
                <p>No conversation history yet</p>
              </div>
            ) : (
              <div className="flex flex-col space-y-2">
                {sessions.map((session) => (
                  <button
                    key={session.sessionId}
                    onClick={() => handleSelectSession(session.sessionId)}
                    className="w-full text-left px-4 py-3 bg-white rounded-xl border border-gray-100 hover:border-blue-200 hover:bg-blue-50 transition-all shadow-sm"
                  >
                    <div className="font-medium text-gray-800 text-sm truncate">
                      {session.title}
                    </div>
                    <div className="text-xs text-gray-400 mt-1">
                      {formatTime(session.updatedAt)}
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        ) : (
          <>
            {/* 消息体 */}
            <div className="flex-grow p-4 overflow-y-auto bg-gray-50 flex flex-col space-y-4 text-sm">
              {messages.map((msg, index) => (
                <div
                  key={index}
                  className={`max-w-[90%] p-3 rounded-xl shadow-sm ${msg.role === 'agent'
                    ? 'bg-white border border-gray-100 rounded-tl-none self-start text-gray-800'
                    : 'bg-blue-600 text-white rounded-tr-none self-end'}`}
                >
                  {msg.image && (
                    <img src={msg.image} alt="uploaded food" className="w-48 h-48 object-cover rounded-lg mb-2 border border-white/20 shadow-sm" />
                  )}
                  {msg.role === 'agent' ? (
                    <div className="break-words">
                      <ReactMarkdown components={{
                        h3: ({ node, ...props }) => <h3 className="text-lg font-extrabold mt-3 mb-2 text-gray-900" {...props} />,
                        p: ({ node, ...props }) => <p className="mb-2 leading-relaxed last:mb-0" {...props} />,
                        ul: ({ node, ...props }) => <ul className="list-disc pl-5 mb-2 space-y-1" {...props} />,
                        ol: ({ node, ...props }) => <ol className="list-decimal pl-5 mb-2 space-y-1" {...props} />,
                        li: ({ node, ...props }) => <li className="leading-relaxed" {...props} />,
                        strong: ({ node, ...props }) => <strong className="font-bold text-blue-900" {...props} />
                      }}>
                        {msg.text}
                      </ReactMarkdown>
                    </div>
                  ) : (
                    <div>{msg.text}</div>
                  )}
                </div>
              ))}
              {isTyping && (
                <div className="bg-white border border-gray-100 p-3 rounded-xl rounded-tl-none self-start shadow-sm flex space-x-1">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0.4s' }} />
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* 输入框 */}
            <form onSubmit={handleSendMessage} className="p-3 bg-white border-t border-gray-100 flex flex-col shrink-0">
              {selectedImage && (
                <div className="relative w-16 h-16 mb-2 rounded-lg overflow-hidden border border-gray-200 shadow-sm">
                  <img src={selectedImage} alt="preview" className="w-full h-full object-cover" />
                  <button type="button" onClick={() => setSelectedImage(null)} className="absolute top-0 right-0 bg-black/60 text-white p-1 rounded-bl-lg hover:bg-black">
                    <X size={12} />
                  </button>
                </div>
              )}
              <div className="flex items-center bg-gray-100 rounded-2xl px-3 py-2 focus-within:ring-2 focus-within:ring-blue-500 transition-all">
                <label className="cursor-pointer text-gray-400 hover:text-blue-600 transition-colors p-1">
                  <ImagePlus size={22} />
                  <input type="file" accept="image/*" className="hidden" onChange={handleImageUpload} disabled={isTyping} />
                </label>
                <input
                  type="text"
                  placeholder={inputPlaceholder}
                  className="flex-grow bg-transparent focus:outline-none text-sm text-gray-800 ml-2"
                  value={chatInput}
                  onChange={(e) => setChatInput(e.target.value)}
                  disabled={isTyping}
                />
                <button
                  type="submit"
                  disabled={isTyping || (!chatInput.trim() && !selectedImage)}
                  className="text-blue-600 ml-2 font-bold text-sm shrink-0 hover:text-blue-800 disabled:text-gray-400 transition-colors px-1"
                >
                  Send
                </button>
              </div>
            </form>
          </>
        )}
      </div>
    </Rnd>
  );
}