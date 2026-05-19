import { useState, useEffect } from 'react';
import { MAPS_API_KEY } from '../../utils/mapsHelper';

export function useRestaurants(query, location) {
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [sortBy, setSortBy] = useState('best');

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await fetch("https://places.googleapis.com/v1/places:searchText", {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Goog-Api-Key': MAPS_API_KEY,
            'X-Goog-FieldMask': 'places.id,places.displayName,places.rating,places.userRatingCount,places.formattedAddress,places.priceLevel,places.photos,places.nationalPhoneNumber,places.websiteUri,places.regularOpeningHours'
          },
          body: JSON.stringify({ textQuery: `${query} in ${location}` })
        });
        if (!response.ok) throw new Error(`API Error: ${response.status}`);
        const data = await response.json();
        if (data.places) {
          setRestaurants(data.places);
          if (data.places.length > 0) setSelectedRestaurant(data.places[0]);
        } else {
          setRestaurants([]);
        }
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [query, location]);

  const sortedRestaurants = [...restaurants].sort((a, b) => {
    if (sortBy === 'best') return (b.rating || 0) - (a.rating || 0);
    if (sortBy === 'popular') return (b.userRatingCount || 0) - (a.userRatingCount || 0);
    return 0;
  });

  return { sortedRestaurants, loading, error, selectedRestaurant, setSelectedRestaurant, sortBy, setSortBy };
}