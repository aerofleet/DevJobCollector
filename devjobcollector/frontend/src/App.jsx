import React from 'react';
import { BrowserRouter, Routes, Route, useNavigate } from 'react-router-dom';
import MainPage from './pages/MainPage';
import AllJobsPage from './pages/AllJobsPage';
import DetailPage from './pages/DetailPage';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import Resume from './pages/Resume';
import ProtectedRoute from './components/auth/ProtectedRoute';
import './styles/App.css';
import ScrollToTop from './components/common/ScrollToTop';
import Header from './pages/Header';

const AppRoutes = () => {
  const navigate = useNavigate();

  const handleSearch = (keyword = '') => {
    const query = keyword.trim() ? `?keyword=${encodeURIComponent(keyword.trim())}` : '';
    navigate(`/jobs${query}`);
  };

  return (
    <div className="app">
      <Header onSearch={handleSearch} />
      <Routes>
        <Route path="/" element={<MainPage onSearch={handleSearch} />} />
        <Route path="/jobs" element={<AllJobsPage />} />
        <Route path="/job/:id" element={<DetailPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/oauth/callback" element={<LoginPage />} />
        <Route
          path="/resume"
          element={(
            <ProtectedRoute>
              <Resume />
            </ProtectedRoute>
          )}
        />
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
