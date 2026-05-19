import React, { useState, useEffect, useRef } from 'react';
import { Search, MapPin, Navigation, Clock } from 'lucide-react';
import { recordFootprint, fetchSearchHistory } from '../../utils/footprint';

export default function SearchBar({
  searchQuery, setSearchQuery,
  location, setLocation,
  isLocating, handleGetLocation,
  handleSearch, userId
}) {
  const [history, setHistory] = useState([]);
  const [showHistory, setShowHistory] = useState(false);
  const inputRef = useRef(null);
  const dropdownRef = useRef(null);

  // 聚焦搜索框时拉取历史
  const handleFocus = async () => {
    if (!userId || userId === 'guest') return;
    const data = await fetchSearchHistory(userId, 6);
    setHistory(data);
    if (data.length > 0) setShowHistory(true);
  };

  // 点击搜索框外面关闭下拉
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (
        dropdownRef.current && !dropdownRef.current.contains(e.target) &&
        inputRef.current && !inputRef.current.contains(e.target)
      ) {
        setShowHistory(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // 点击历史记录填入搜索框
  const handleHistoryClick = (item) => {
    // 历史格式是 'Searched "sushi" in Walnut Creek'，提取引号里的内容
    const match = item.match(/Searched "(.+)" in/);
    if (match) {
      setSearchQuery(match[1]);
    } else {
      setSearchQuery(item);
    }
    setShowHistory(false);
    inputRef.current?.focus();
  };

  const handleSubmit = (e) => {
    setShowHistory(false);
    if (searchQuery.trim()) {
      recordFootprint(userId, 'search', `Searched "${searchQuery}" in ${location || 'Walnut Creek'}`);
    }
    handleSearch(e);
  };

  return (
    <div className="relative w-full">
      <form
        onSubmit={handleSubmit}
        className="w-full bg-white rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.08)] border border-gray-100 flex flex-col md:flex-row items-center overflow-hidden transition-shadow hover:shadow-[0_8px_40px_rgb(0,0,0,0.12)]"
      >
        <div className="flex-grow w-full flex items-center px-6 py-5 md:border-r border-gray-200">
          <Search size={22} className="text-gray-400 mr-3 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            placeholder="Craving spicy chicken, sushi, or boba?"
            className="w-full focus:outline-none text-lg"
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              if (e.target.value) setShowHistory(false); // 输入时关闭历史
            }}
            onFocus={handleFocus}
          />
        </div>
        <div className="w-full md:w-[35%] flex items-center px-6 py-5 bg-gray-50/50 border-t md:border-t-0">
          <MapPin size={22} className="text-blue-500 mr-3 shrink-0" />
          <input
            type="text"
            placeholder="Zip or City"
            className="w-full focus:outline-none text-lg bg-transparent"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
          />
          <button
            type="button"
            onClick={handleGetLocation}
            disabled={isLocating}
            className="p-2 text-blue-500 hover:bg-blue-100 rounded-full ml-2 transition-colors"
          >
            <Navigation size={20} className={isLocating ? "animate-pulse text-blue-300" : ""} />
          </button>
        </div>
        <button type="submit" className="px-10 py-6 bg-blue-600 hover:bg-blue-700 text-white font-bold text-lg transition-colors">
          Search
        </button>
      </form>

      {/* 搜索历史下拉 */}
      {showHistory && history.length > 0 && (
        <div
          ref={dropdownRef}
          className="absolute top-full left-0 right-0 mt-2 bg-white rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.12)] border border-gray-100 overflow-hidden z-50"
        >
          <div className="px-4 py-2 text-xs text-gray-400 font-medium border-b border-gray-50">
            Recent searches
          </div>
          {history.map((item, idx) => {
            // 显示时提取引号里的内容，更干净
            const match = item.match(/Searched "(.+)" in (.+)/);
            const display = match ? match[1] : item;
            const loc = match ? match[2] : '';
            return (
              <button
                key={idx}
                type="button"
                onClick={() => handleHistoryClick(item)}
                className="w-full flex items-center px-4 py-3 hover:bg-gray-50 transition-colors text-left"
              >
                <Clock size={16} className="text-gray-300 mr-3 shrink-0" />
                <span className="text-gray-700 font-medium">{display}</span>
                {loc && <span className="text-gray-400 text-sm ml-2">in {loc}</span>}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}