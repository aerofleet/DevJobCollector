import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, useNavigate } from 'react-router-dom';
import MainPage from './pages/MainPage';
import DetailPage from './pages/DetailPage';
import './styles/App.css';
import ScrollToTop from './components/common/ScrollToTop';
import Header from './pages/Header';

const AppRoutes = () => {
  const [searchParams, setSearchParams] = useState({ keyword: '' });
  const navigate = useNavigate();

  const handleSearch = (keyword = '') => {
    setSearchParams({ keyword });
    navigate('/');
  };

  return (
    <div className="app">
      <Header onSearch={handleSearch} />
      <Routes>
        <Route path="/" element={<MainPage searchParams={searchParams} />} />
        <Route path="/job/:id" element={<DetailPage />} />
      </Routes>
      <ScrollToTop />
    </div>
  );
};

function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}

export default App;
