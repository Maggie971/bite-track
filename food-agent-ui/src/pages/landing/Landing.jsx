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
    context: '',
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
    <div className="min-h-screen bg-white flex flex-col font-sans text-gray-900 relative">
      <div className="fixed top-0 left-0 w-full h-full -z-10 pointer-events-none">
        <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-blue-50 rounded-full blur-3xl opacity-50" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-amber-50 rounded-full blur-3xl opacity-50" />
      </div>

      <nav className="w-full border-b border-gray-50/50 backdrop-blur">
        <div className="max-w-7xl mx-auto px-6 py-4 flex justify-between items-center">
          <div className="font-extrabold text-2xl text-blue-600 tracking-tight">BiteTrack</div>
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
        Your personal  <span className="text-blue-600">food AI.</span>
        </h1>
        <p className="text-xl text-gray-500 mb-12 max-w-2xl">
        Snap a photo, describe a craving, or ask anything — BiteTrack remembers your taste and guides every meal.
        </p>
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

      <ChatWindow
        {...chat}
        userId={isSignedIn ? user?.id : 'guest'}
        loadSession={chat.loadSession}
        startNewSession={chat.startNewSession}
        handleCloseChatWithSummary={chat.handleCloseChatWithSummary}
        userLocation={chat.userLocation}
        setUserLocation={chat.setUserLocation}
        saveLocation={chat.saveLocation}
      />
      <ChatFab isChatOpen={chat.isChatOpen} setIsChatOpen={chat.setIsChatOpen} />
    </div>
  );
}