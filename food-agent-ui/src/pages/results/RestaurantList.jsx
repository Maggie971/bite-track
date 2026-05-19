import React from 'react';
import { Star, MapPin, Loader2 } from 'lucide-react';
import { getPhotoUrl } from '../../utils/mapsHelper';
import { recordFootprint } from '../../utils/footprint';

export default function RestaurantList({
  sortedRestaurants, loading, error,
  selectedRestaurant, setSelectedRestaurant,
  sortBy, setSortBy, userId
}) {

  const handleSelectRestaurant = (place) => {
    recordFootprint(
      userId,
      'view',
      `Viewed restaurant "${place.displayName?.text}" in ${place.formattedAddress}`
    );
    setSelectedRestaurant(place);
  };

  return (
    <div className="w-full md:w-[35%] lg:w-[30%] bg-white border-r border-gray-200 overflow-y-auto h-full">
      <div className="p-6 pb-2 sticky top-0 bg-white/95 backdrop-blur z-10 border-b border-gray-100">
        <div className="flex space-x-2 overflow-x-auto pb-2">
          <button
            onClick={() => setSortBy('best')}
            className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${sortBy === 'best' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}`}
          >
            Highest Rated
          </button>
          <button
            onClick={() => setSortBy('popular')}
            className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${sortBy === 'popular' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'}`}
          >
            Most Popular
          </button>
        </div>
      </div>

      {loading && (
        <div className="flex justify-center py-12">
          <Loader2 className="animate-spin text-blue-500" size={32} />
        </div>
      )}

      {!loading && !error && (
        <div className="flex flex-col">
          {sortedRestaurants.map((place) => (
            <div
              key={place.id}
              onClick={() => handleSelectRestaurant(place)}
              className={`flex gap-4 p-5 border-b border-gray-100 cursor-pointer transition-all ${
                selectedRestaurant?.id === place.id
                  ? 'bg-blue-50 border-l-4 border-l-blue-600 shadow-inner'
                  : 'hover:bg-gray-50 border-l-4 border-l-transparent'
              }`}
            >
              <div className="w-24 h-24 flex-shrink-0 rounded-lg bg-gray-200 overflow-hidden shadow-sm">
                <img
                  src={getPhotoUrl(place.photos)}
                  alt={place.displayName?.text}
                  className="w-full h-full object-cover"
                  loading="lazy"
                />
              </div>
              <div className="flex flex-col flex-grow overflow-hidden">
                <h3 className="font-bold text-gray-900 truncate">{place.displayName?.text}</h3>
                <div className="flex items-center mt-1 text-sm">
                  <Star size={15} className="text-amber-500 fill-current mr-1" />
                  <span className="font-bold text-gray-800">{place.rating || 'N/A'}</span>
                  <span className="text-gray-400 ml-1">({place.userRatingCount || 0})</span>
                </div>
                <p className="text-xs text-gray-500 mt-2 truncate">
                  <MapPin size={12} className="inline mr-1" />{place.formattedAddress}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}