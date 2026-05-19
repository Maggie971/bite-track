import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Landing from './pages/landing/Landing';
import Results from './pages/results/Results';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* The home page */}
        <Route path="/" element={<Landing />} />
        {/* The new results page */}
        <Route path="/results" element={<Results />} />
      </Routes>
    </BrowserRouter>
  );
}