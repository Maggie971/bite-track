import React from 'react';
import { MessageSquare, X } from 'lucide-react';

export default function ChatFab({ isChatOpen, setIsChatOpen }) {
  return (
    <div className="fixed bottom-8 w-full max-w-5xl left-1/2 -translate-x-1/2 flex justify-end px-4 z-20 pointer-events-none">
      <button
        onClick={() => setIsChatOpen(!isChatOpen)}
        className={`pointer-events-auto w-16 h-16 rounded-full flex items-center justify-center text-white transition-all duration-300 ${
          isChatOpen
            ? 'bg-gray-800 shadow-lg scale-90'
            : 'bg-blue-600 shadow-[0_10px_25px_rgba(37,99,235,0.4)] hover:bg-blue-700 hover:scale-105'
        }`}
      >
        {isChatOpen ? <X size={28} /> : <MessageSquare size={28} />}
      </button>
    </div>
  );
}