import React from 'react';
import { BrowserRouter, Routes, Route, useNavigate } from 'react-router-dom';
import MainPage from './pages/MainPage';
import AllJobsPage from './pages/AllJobsPage';
import DetailPage from './pages/DetailPage';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import TermsPage from './pages/TermsPage';
import PrivacyPolicyPage from './pages/PrivacyPolicyPage';
import Resume from './pages/Resume';
import MemberHomePage from './pages/MemberHomePage';
import MyDevJobsPage from './pages/MyDevJobsPage';
import ResumesPage from './pages/ResumesPage';
import ProtectedRoute from './components/auth/ProtectedRoute';
import './styles/App.css';
import ScrollToTop from './components/common/ScrollToTop';
import Header from './pages/Header';
import MemberSessionProvider from './contexts/MemberSessionProvider';

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
        <Route path="/terms" element={<TermsPage />} />
        <Route path="/privacy" element={<PrivacyPolicyPage />} />
        <Route path="/oauth/callback" element={<LoginPage />} />
        <Route
          path="/member"
          element={(
            <ProtectedRoute>
              <MemberHomePage />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/my-devjobs"
          element={(
            <ProtectedRoute>
              <MyDevJobsPage />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/resumes"
          element={(
            <ProtectedRoute>
              <ResumesPage />
            </ProtectedRoute>
          )}
        />
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
      <MemberSessionProvider>
        <AppRoutes />
      </MemberSessionProvider>
    </BrowserRouter>
  );
}

export default App;
