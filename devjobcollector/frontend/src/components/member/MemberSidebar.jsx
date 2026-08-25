import React from 'react';
import { BriefcaseBusiness, FileText, LayoutDashboard } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useMemberSession } from '../../contexts/memberSession';

const NAV_ITEMS = [
  { to: '/member', label: '커리어 홈', icon: LayoutDashboard, end: true },
  { to: '/my-devjobs', label: '마이데브잡', icon: BriefcaseBusiness },
  { to: '/resumes', label: '이력서 관리', icon: FileText },
];

const MemberSidebar = () => {
  const { member } = useMemberSession();
  const memberName = member?.name?.trim() || '개발자';
  const avatarLabel = memberName.charAt(0).toUpperCase();

  return (
    <aside className="member-sidebar" aria-label="내 커리어 메뉴">
      <div className="member-profile">
        <span className="member-avatar" aria-hidden="true">{avatarLabel}</span>
        <div>
          <strong>반가워요, {memberName}님</strong>
          <p>오늘도 좋은 기회를 찾아볼까요?</p>
        </div>
      </div>
      <nav className="member-side-nav">
        {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end}>
            {React.createElement(Icon, { size: 19, 'aria-hidden': true })}
            {label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
};

export default MemberSidebar;
