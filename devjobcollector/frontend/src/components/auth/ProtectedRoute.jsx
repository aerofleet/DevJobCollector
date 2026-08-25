import React, { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useMemberSession } from '../../contexts/memberSession';

const ProtectedRoute = ({ children }) => {
  const location = useLocation();
  const token = localStorage.getItem('accessToken');
  const { status, loadMember } = useMemberSession();

  useEffect(() => {
    if (token && status === 'idle') {
      loadMember();
    }
  }, [loadMember, status, token]);

  if (!token) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  if (status === 'idle' || status === 'loading') {
    return <main className="member-session-state" aria-live="polite">회원 정보를 불러오는 중입니다.</main>;
  }

  if (status === 'error') {
    return <main className="member-session-state" role="alert">회원 정보를 불러오지 못했습니다.</main>;
  }

  return children;
};

export default ProtectedRoute;
