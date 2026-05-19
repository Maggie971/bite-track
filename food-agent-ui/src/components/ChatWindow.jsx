import React from 'react';
import { Rnd } from 'react-rnd';
import ReactMarkdown from 'react-markdown';
import { X, ImagePlus } from 'lucide-react';

export default function ChatWindow({
  isChatOpen, setIsChatOpen,
  messages, isTyping,
  chatInput, setChatInput,
  selectedImage, setSelectedImage,
  handleImageUpload, handleSendMessage,
  inputPlaceholder = "Ask me anything..."
}) {
  if (!isChatOpen) return null;

  return (
    <Rnd
      default={{
        // 从右下角往左上偏移，留出 24px 边距
        x: window.innerWidth / 2 + 640 - 424 - 24,  // #root右边界往左 424+24px
        y: Math.max(window.innerHeight - 574, 16),
        width: Math.min(400, window.innerWidth - 32),
        height: Math.min(550, window.innerHeight - 100),
      }}
      minWidth={320}
      minHeight={400}
      // ✅ 修复：bounds="window" 确保不会拖出视口
      bounds="window"
      dragHandleClassName="chat-drag-handle"
      // ✅ 修复：position fixed + 超高 z-index，完全脱离文档流
      style={{ position: 'fixed', zIndex: 9999 }}
    >
      <div className="w-full h-full bg-white rounded-2xl shadow-[0_20px_60px_rgba(0,0,0,0.25)] border border-gray-200 flex flex-col overflow-hidden">

        {/* 头部拖拽把手 */}
        <div className="chat-drag-handle bg-blue-600 text-white px-4 py-3 flex items-center justify-between shadow-md cursor-move select-none">
          <div className="flex items-center space-x-2">
            <span className="text-xl">✨</span>
            <span className="font-bold text-sm">FoodAgent Expert</span>
          </div>
          <button
            onMouseDown={(e) => e.stopPropagation()}
            onClick={() => setIsChatOpen(false)}
            className="hover:bg-blue-700 p-1 rounded-md transition-colors cursor-pointer"
          >
            <X size={18} />
          </button>
        </div>

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
                  <ReactMarkdown
                    components={{
                      h3: ({ node, ...props }) => <h3 className="text-lg font-extrabold mt-3 mb-2 text-gray-900" {...props} />,
                      p: ({ node, ...props }) => <p className="mb-2 leading-relaxed last:mb-0" {...props} />,
                      ul: ({ node, ...props }) => <ul className="list-disc pl-5 mb-2 space-y-1" {...props} />,
                      ol: ({ node, ...props }) => <ol className="list-decimal pl-5 mb-2 space-y-1" {...props} />,
                      li: ({ node, ...props }) => <li className="leading-relaxed" {...props} />,
                      strong: ({ node, ...props }) => <strong className="font-bold text-blue-900" {...props} />
                    }}
                  >
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
        </div>

        {/* 底部输入框 */}
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
      </div>
    </Rnd>
  );
}