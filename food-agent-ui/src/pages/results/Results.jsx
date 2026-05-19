import React from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useUser } from '@clerk/clerk-react';
import { ArrowLeft } from 'lucide-react';
import { useChatAgent } from '../../hooks/useChatAgent';
import { useRestaurants } from './useRestaurants';
import RestaurantList from './RestaurantList';
import RestaurantDetail from './RestaurantDetail';
import ChatWindow from '../../components/ChatWindow';
import ChatFab from '../../components/ChatFab';

export default function Results() {
  const [searchParams] = useSearchParams();
  const query = searchParams.get('q') || 'food';
  const location = searchParams.get('loc') || 'Walnut Creek';
  const navigate = useNavigate();
  const { user, isSignedIn } = useUser();

  const { sortedRestaurants, loading, error, selectedRestaurant, setSelectedRestaurant, sortBy, setSortBy } = useRestaurants(query, location);

  const chat = useChatAgent({
    userId: isSignedIn ? user?.id : "guest",
    context: selectedRestaurant?.displayName?.text || location,
    initialMessage: "Hi there! I'm your AI Food Expert. You can type questions or upload a photo of food to let me analyze it!"
  });

  return (
    // ✅ 关键修复：去掉 overflow-hidden！原因同 Landing.jsx
    <div className="min-h-screen bg-gray-50 flex flex-col font-sans">

      <header className="bg-white border-b px-6 py-4 flex items-center sticky top-0 z-10 shadow-sm shrink-0">
        <button onClick={() => navigate('/')} className="p-2 hover:bg-gray-100 rounded-full transition-colors mr-4">
          <ArrowLeft size={20} />
        </button>
        <div>
          <h1 className="font-bold text-xl text-gray-900">Live Results for "{query}"</h1>
          <p className="text-sm text-gray-500">Location target: {location}</p>
        </div>
      </header>

      {/* 主体容器也不要 overflow-hidden */}
      <div className="flex-grow flex flex-col md:flex-row w-full h-[calc(100vh-73px)]">
        <RestaurantList
          sortedRestaurants={sortedRestaurants}
          loading={loading}
          error={error}
          selectedRestaurant={selectedRestaurant}
          setSelectedRestaurant={setSelectedRestaurant}
          sortBy={sortBy}
          setSortBy={setSortBy}
          userId={isSignedIn ? user?.id : 'guest'}
        />
        <div className="hidden md:flex flex-col w-full md:w-[65%] lg:w-[70%] bg-gray-50 h-full overflow-y-auto">
          <RestaurantDetail
            selectedRestaurant={selectedRestaurant}
            onOpenChat={() => chat.setIsChatOpen(true)}
          />
        </div>
      </div>

      <ChatWindow
        {...chat}
        inputPlaceholder={selectedRestaurant ? `Ask about ${selectedRestaurant.displayName?.text}...` : "Ask me anything..."}
      />
      <ChatFab isChatOpen={chat.isChatOpen} setIsChatOpen={chat.setIsChatOpen} />
    </div>
  );
}