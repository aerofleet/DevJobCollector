import { Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import {
  Building2,
  ClipboardList,
  FileClock,
  LayoutDashboard,
  LogOut,
  Menu,
  ShieldCheck,
  Users,
  X,
} from 'lucide-react';
import { createElement, useState } from 'react';
import { AdminAuthProvider, useAdminAuth } from './auth/AdminAuthContext';
import DashboardPage from './pages/DashboardPage';
import LoginPage from './pages/LoginPage';
import ResourcePage from './pages/ResourcePage';

const navigation = [
  { to: '/', label: '대시보드', icon: LayoutDashboard, end: true },
  { to: '/users', label: '회원 관리', icon: Users },
  { to: '/companies', label: '기업 심사', icon: Building2 },
  { to: '/jobs', label: '공고 관리', icon: ClipboardList },
  { to: '/audit', label: '감사 기록', icon: FileClock, superAdminOnly: true },
  { to: '/admins', label: '관리자 계정', icon: ShieldCheck, superAdminOnly: true },
];

const routeTitles = {
  '/': '운영 대시보드',
  '/users': '회원 관리',
  '/companies': '기업 심사',
  '/jobs': '공고 관리',
  '/audit': '감사 기록',
  '/admins': '관리자 계정',
};

const LoadingScreen = () => (
  <div className="center-state" role="status">
    <span className="spinner" aria-hidden="true" />
    관리자 세션을 확인하고 있습니다.
  </div>
);

const ProtectedLayout = () => {
  const { admin, isLoading, logout } = useAdminAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  if (isLoading) return <LoadingScreen />;
  if (!admin) return <Navigate to="/login" replace state={{ from: location }} />;

  const visibleNavigation = navigation.filter((item) => (
    !item.superAdminOnly || admin.role === 'SUPER_ADMIN'
  ));

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="admin-shell">
      <button
        className="mobile-menu-button"
        type="button"
        aria-label="관리 메뉴 열기"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen(true)}
      >
        <Menu size={22} />
      </button>
      {menuOpen && <button className="sidebar-backdrop" type="button" aria-label="관리 메뉴 닫기" onClick={() => setMenuOpen(false)} />}
      <aside className={`sidebar${menuOpen ? ' is-open' : ''}`}>
        <div className="brand-block">
          <div className="brand-mark">D</div>
          <div>
            <strong>DevJobs</strong>
            <span>Operations</span>
          </div>
          <button className="sidebar-close" type="button" aria-label="관리 메뉴 닫기" onClick={() => setMenuOpen(false)}>
            <X size={20} />
          </button>
        </div>
        <nav className="admin-navigation" aria-label="관리자 메뉴">
          {visibleNavigation.map(({ to, label, icon, end }) => (
            <NavLink key={to} to={to} end={end} onClick={() => setMenuOpen(false)}>
              {createElement(icon, { size: 19 })}
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span className="environment-badge">PROTECTED CONSOLE</span>
          <p>모든 상태 변경은 감사 기록으로 남습니다.</p>
        </div>
      </aside>
      <div className="admin-workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">DJC CONTROL CENTER</p>
            <h1>{routeTitles[location.pathname] || '관리자'}</h1>
          </div>
          <div className="admin-profile">
            <div className="admin-avatar" aria-hidden="true">{admin.name?.slice(0, 1) || 'A'}</div>
            <div>
              <strong>{admin.name}</strong>
              <span>{admin.role}</span>
            </div>
            <button type="button" onClick={handleLogout} aria-label="로그아웃">
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main className="admin-content"><Outlet /></main>
      </div>
    </div>
  );
};

const AppRoutes = () => (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<ProtectedLayout />}>
      <Route index element={<DashboardPage />} />
      <Route path="users" element={<ResourcePage title="회원 관리" description="회원 검색과 상태 조치는 관리자 API P2에서 연결됩니다." />} />
      <Route path="companies" element={<ResourcePage title="기업 심사" description="인증 대기 기업과 심사 이력은 관리자 API P2에서 연결됩니다." />} />
      <Route path="jobs" element={<ResourcePage title="공고 관리" description="숨김과 마감을 분리한 상태 모델 확정 후 연결됩니다." />} />
      <Route path="audit" element={<ResourcePage title="감사 기록" description="성공한 업무 변경과 접근 기록을 검색합니다." />} />
      <Route path="admins" element={<ResourcePage title="관리자 계정" description="SUPER_ADMIN 전용 계정 및 MFA 관리 화면입니다." />} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
);

const App = () => (
  <AdminAuthProvider>
    <AppRoutes />
  </AdminAuthProvider>
);

export default App;
