import React from 'react';
import { Star, MapPin, Phone, Globe, Clock, ImagePlus } from 'lucide-react';
import { getPhotoUrl, formatPrice } from '../../utils/mapsHelper';

export default function RestaurantDetail({ selectedRestaurant, onOpenChat }) {
  if (!selectedRestaurant) {
    return (
      <div className="flex items-center justify-center h-full text-gray-400">
        Select a restaurant on the left.
      </div>
    );
  }

  return (
    <div className="p-8 lg:p-12 w-full">
      {/* 餐厅信息卡片 */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 overflow-hidden flex flex-col xl:flex-row w-full min-h-[350px]">
        <div className="w-full xl:w-[45%] h-72 xl:h-auto shrink-0 bg-gray-200">
          <img src={getPhotoUrl(selectedRestaurant.photos)} alt="cover" className="w-full h-full object-cover" />
        </div>
        <div className="p-8 xl:p-12 flex flex-col justify-center flex-grow">
          <h2 className="text-4xl lg:text-5xl font-extrabold text-gray-900 mb-4">
            {selectedRestaurant.displayName?.text}
          </h2>
          <div className="flex flex-wrap items-center gap-3 text-base text-gray-700 mb-8">
            <span className="flex items-center bg-amber-100 text-amber-800 px-3 py-1 rounded-md font-bold">
              <Star size={18} className="mr-1" /> {selectedRestaurant.rating}
            </span>
            <span className="text-gray-500 font-medium">{selectedRestaurant.userRatingCount} Reviews</span>
            {selectedRestaurant.priceLevel && (
              <span className="font-mono bg-green-50 text-green-700 border border-green-200 px-3 py-1 rounded-md font-bold">
                {formatPrice(selectedRestaurant.priceLevel)}
              </span>
            )}
          </div>
          <div className="space-y-5 text-gray-600">
            <div className="flex items-start">
              <MapPin size={22} className="mr-4 text-blue-500 shrink-0 mt-0.5" />
              <span className="text-lg leading-snug">{selectedRestaurant.formattedAddress}</span>
            </div>
            {selectedRestaurant.nationalPhoneNumber && (
              <div className="flex items-center">
                <Phone size={22} className="mr-4 text-blue-500 shrink-0" />
                <span className="text-lg">{selectedRestaurant.nationalPhoneNumber}</span>
              </div>
            )}
            {selectedRestaurant.regularOpeningHours && (
              <div className="flex items-center">
                <Clock size={22} className="mr-4 text-blue-500 shrink-0" />
                <span className={`text-lg font-bold ${selectedRestaurant.regularOpeningHours.openNow ? 'text-green-600' : 'text-red-500'}`}>
                  {selectedRestaurant.regularOpeningHours.openNow ? 'Open Now' : 'Closed Currently'}
                </span>
              </div>
            )}
            {selectedRestaurant.websiteUri && (
              <div className="flex items-center pt-2">
                <Globe size={22} className="mr-4 text-blue-500 shrink-0" />
                <a href={selectedRestaurant.websiteUri} target="_blank" rel="noreferrer" className="text-lg text-blue-600 hover:underline font-bold">
                  Visit Official Website
                </a>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* AI 入口按钮 */}
      <button
        onClick={onOpenChat}
        className="mt-8 bg-blue-50 hover:bg-blue-100 border border-blue-200 rounded-2xl p-10 flex flex-col items-center justify-center text-center text-blue-800 w-full shadow-sm transition-all cursor-pointer group"
      >
        <ImagePlus size={40} className="mb-4 text-blue-500 group-hover:scale-110 transition-transform" />
        <h3 className="text-xl font-bold mb-2">Have a photo of food?</h3>
        <p className="text-base opacity-80 max-w-md">Click here to upload a picture, and our AI will identify the dish and tell you the calories!</p>
      </button>
    </div>
  );
}