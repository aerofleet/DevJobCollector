import React, { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import MainPage from './pages/MainPage';
import ProtectedRoute from './components/auth/ProtectedRoute';
import './styles/App.css';
import ScrollToTop from './components/common/ScrollToTop';
import Header from './pages/Header';
import MemberSessionProvider from './contexts/MemberSessionProvider';
import ScrollReveals from './motion/ScrollReveals';
import GrowthAnalytics from './components/common/GrowthAnalytics';
import { clearJobListPosition } from './utils/jobListPosition';

const AllJobsPage = lazy(() => import('./pages/AllJobsPage'));
const DetailPage = lazy(() => import('./pages/DetailPage'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const SignupPage = lazy(() => import('./pages/SignupPage'));
const TermsPage = lazy(() => import('./pages/TermsPage'));
const PrivacyPolicyPage = lazy(() => import('./pages/PrivacyPolicyPage'));
const Resume = lazy(() => import('./pages/Resume'));
const ResumeAnalysisPage = lazy(() => import('./pages/ResumeAnalysisPage'));
const MemberHomePage = lazy(() => import('./pages/MemberHomePage'));
const loadMyDevJobsPage = () => import('./pages/MyDevJobsPage');
const loadResumesPage = () => import('./pages/ResumesPage');
const MyDevJobsPage = lazy(loadMyDevJobsPage);
const ResumesPage = lazy(loadResumesPage);
const CompanyPage = lazy(() => import('./pages/CompanyPage'));

class RouteErrorBoundary extends React.Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() {
    return this.state.failed ? <main role="alert" style={{ padding: 32, minHeight: '60vh' }}>
      <h1>화면을 불러오지 못했습니다.</h1>
      <button type="button" onClick={() => window.location.reload()}>다시 불러오기</button>
    </main> : this.props.children;
  }
}

const AppRoutes = () => {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (location.pathname !== '/jobs' && !/^\/job\/[^/]+$/.test(location.pathname)) clearJobListPosition();
  }, [location.pathname]);

  const handleSearch = (keyword = '') => {
    const query = keyword.trim() ? `?keyword=${encodeURIComponent(keyword.trim())}` : '';
    navigate(`/jobs${query}`);
  };

  return (
    <div className="app">
      <ScrollReveals />
      <Header onSearch={handleSearch} />
      <RouteErrorBoundary key={location.pathname}>
      <Suspense fallback={<main role="status" aria-busy="true" style={{ padding: 32, minHeight: '60vh' }}>화면 불러오는 중…</main>}>
      <Routes>
        <Route path="/" element={<MainPage onSearch={handleSearch} />} />
        <Route path="/jobs" element={<AllJobsPage />} />
        <Route path="/job/:id" element={<DetailPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/terms" element={<TermsPage />} />
        <Route path="/privacy" element={<PrivacyPolicyPage />} />
        <Route path="/oauth/callback" element={<LoginPage />} />
        <Route path="/resume-analysis" element={<ProtectedRoute><ResumeAnalysisPage /></ProtectedRoute>} />
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
            <ProtectedRoute preload={loadMyDevJobsPage}>
              <MyDevJobsPage />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/company"
          element={(
            <ProtectedRoute>
              <CompanyPage />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/resumes"
          element={(
            <ProtectedRoute preload={loadResumesPage}>
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
      </Suspense>
      </RouteErrorBoundary>
      <ScrollToTop />
      <GrowthAnalytics />
    </div>
  );
};

function App(props) {
  const Router = props.Router || BrowserRouter;
  return (
    <Router>
      <MemberSessionProvider>
        <AppRoutes />
      </MemberSessionProvider>
    </Router>
  );
}

export default App;
