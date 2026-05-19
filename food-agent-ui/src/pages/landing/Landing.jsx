import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { SignedIn, SignedOut, SignInButton, UserButton, useUser } from "@clerk/clerk-react";
import { useChatAgent } from '../../hooks/useChatAgent';
import ChatWindow from '../../components/ChatWindow';
import ChatFab from '../../components/ChatFab';
import SearchBar from './SearchBar';

export default function Landing() {
  const [searchQuery, setSearchQuery] = useState('');
  const [location, setLocation] = useState('');
  const [isLocating, setIsLocating] = useState(false);
  const navigate = useNavigate();
  const { user, isSignedIn } = useUser();

  const chat = useChatAgent({
    userId: isSignedIn ? user?.id : "guest",
    context: location || "Walnut Creek general area",
    initialMessage: "Hi! I'm your AI Food Expert. Tell me what you're craving, or upload a photo, and I'll tell you where to find it nearby!"
  });

  const handleGetLocation = () => {
    setIsLocating(true);
    if (!navigator.geolocation) { alert("Geolocation not supported."); setIsLocating(false); return; }
    navigator.geolocation.getCurrentPosition(
      () => { setTimeout(() => { setLocation("94596"); setIsLocating(false); }, 800); },
      () => { alert("Location access denied."); setIsLocating(false); }
    );
  };

  const handleSearch = (e) => {
    e.preventDefault();
    const query = encodeURIComponent(searchQuery || 'food');
    const loc = encodeURIComponent(location || 'Walnut Creek');
    navigate(`/results?q=${query}&loc=${loc}`);
  };

  return (
    // ✅ 关键修复：去掉 overflow-hidden！
    // overflow-hidden 会把所有 fixed 子元素裁切掉，导致聊天框在全屏时不可见
    // 背景装饰改用独立 fixed 层来实现，不再依赖父容器裁切
    <div className="min-h-screen bg-white flex flex-col font-sans text-gray-900 relative">

      {/* 装饰性背景：改为 fixed 独立层，完全不影响任何子元素布局 */}
      <div className="fixed top-0 left-0 w-full h-full -z-10 pointer-events-none">
        <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-blue-50 rounded-full blur-3xl opacity-50" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-amber-50 rounded-full blur-3xl opacity-50" />
      </div>

      {/* ✅ 留白修复：max-w-5xl → max-w-7xl */}
      <nav className="w-full border-b border-gray-50/50 backdrop-blur">
        <div className="max-w-7xl mx-auto px-6 py-4 flex justify-between items-center">
          <div className="font-extrabold text-2xl text-blue-600 tracking-tight">FoodAgent</div>
          <div className="flex items-center">
            <SignedOut>
              <SignInButton mode="modal">
                <button className="bg-blue-600 text-white px-5 py-2 rounded-full font-medium hover:bg-blue-700 transition-colors shadow-sm">
                  Sign In
                </button>
              </SignInButton>
            </SignedOut>
            <SignedIn>
              <UserButton appearance={{ elements: { userButtonAvatarBox: "w-10 h-10", userButtonTrigger: "w-10 h-10" } }} />
            </SignedIn>
          </div>
        </div>
      </nav>

      <main className="flex-grow flex flex-col items-center justify-center px-4 pt-16 pb-24 text-center z-10">
        <h1 className="text-6xl md:text-7xl font-extrabold tracking-tight mb-6 text-gray-900">
          Find your <span className="text-blue-600">favorites.</span>
        </h1>
        <p className="text-xl text-gray-500 mb-12 max-w-2xl">
          Search for top-rated spots, or click the ✨ icon to chat with your AI expert and get personalized recommendations based on photos or cravings.
        </p>
        {/* 搜索框保持适中宽度，视觉上更聚焦 */}
        <div className="w-full max-w-3xl">
          <SearchBar
            searchQuery={searchQuery} setSearchQuery={setSearchQuery}
            location={location} setLocation={setLocation}
            isLocating={isLocating}
            handleGetLocation={handleGetLocation}
            handleSearch={handleSearch}
            userId={isSignedIn ? user?.id : 'guest'}
          />
        </div>
      </main>

      <ChatWindow {...chat} />
      <ChatFab isChatOpen={chat.isChatOpen} setIsChatOpen={chat.setIsChatOpen} />
    </div>
  );
}