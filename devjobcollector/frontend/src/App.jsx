import React, { useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import MainPage from './pages/MainPage';
import DetailPage from './pages/DetailPage';
import './styles/App.css';
import ScrollToTop from './components/common/ScrollToTop';
import Header from './pages/Header';

function App() {
  const [searchParams, setSearchParams] = useState({ keyword: '' });

  const handleSearch = (keyword = '') => {
    setSearchParams({ keyword });
  };

  return (
    <BrowserRouter>
      <div className="app">
        <Header onSearch={handleSearch} />
        <Routes>
          <Route path="/" element={<MainPage searchParams={searchParams} />} />
          <Route path="/job/:id" element={<DetailPage />} />
        </Routes>
        <ScrollToTop />
      </div>
    </BrowserRouter>
  );
}

export default App;
