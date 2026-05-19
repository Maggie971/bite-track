export const MAPS_API_KEY = "AIzaSyAnVFyhgwBov2ZRkLAXpSTYpuSC9FqwE5c";

export function getPhotoUrl(photos) {
  if (!photos || photos.length === 0)
    return "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=400&q=80";
  return `https://places.googleapis.com/v1/${photos[0].name}/media?key=${MAPS_API_KEY}&maxWidthPx=800`;
}

export function formatPrice(level) {
  const mapping = {
    'PRICE_LEVEL_INEXPENSIVE': '$',
    'PRICE_LEVEL_MODERATE': '$$',
    'PRICE_LEVEL_EXPENSIVE': '$$$',
    'PRICE_LEVEL_VERY_EXPENSIVE': '$$$$'
  };
  return mapping[level] || 'N/A';
}