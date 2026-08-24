import React, { useState } from 'react';
import { Bookmark, BriefcaseBusiness, ChevronRight, Clock3, Search } from 'lucide-react';
import { Link } from 'react-router-dom';
import MemberSidebar from '../components/member/MemberSidebar';
import '../styles/MemberPages.css';

const TABS = [
  { id: 'saved', label: '저장한 공고', icon: Bookmark },
  { id: 'applied', label: '지원 현황', icon: BriefcaseBusiness },
  { id: 'recent', label: '최근 본 공고', icon: Clock3 },
];

const EMPTY_COPY = {
  saved: ['아직 저장한 공고가 없어요.', '관심 있는 공고를 저장하면 여기에서 한 번에 볼 수 있어요.'],
  applied: ['진행 중인 지원 내역이 없어요.', '지원한 포지션의 진행 상태를 이곳에서 관리할 수 있어요.'],
  recent: ['최근 확인한 공고가 없어요.', '채용공고를 둘러보면 최근 본 내역이 차곡차곡 쌓여요.'],
};

const MyDevJobsPage = () => {
  const [activeTab, setActiveTab] = useState('saved');
  const [title, description] = EMPTY_COPY[activeTab];

  return (
    <main className="member-page">
      <div className="member-layout">
        <MemberSidebar />
        <div className="member-main">
          <header className="member-page-heading">
            <span className="member-eyebrow">MY DEVJOBS</span>
            <h1>마이데브잡</h1>
            <p>관심 공고부터 지원 과정까지 내 채용 활동을 모아보세요.</p>
          </header>

          <section className="activity-summary" aria-label="채용 활동 요약">
            {TABS.map(({ id, label, icon: Icon }) => (
              <button type="button" key={id} onClick={() => setActiveTab(id)} className={activeTab === id ? 'active' : ''}>
                <span>{React.createElement(Icon, { size: 19 })}</span>
                <strong>0</strong>
                <small>{label}</small>
                <ChevronRight size={17} aria-hidden="true" />
              </button>
            ))}
          </section>

          <section className="member-content-card">
            <div className="member-tabs" role="tablist" aria-label="마이데브잡 목록">
              {TABS.map(({ id, label }) => (
                <button
                  type="button"
                  role="tab"
                  aria-selected={activeTab === id}
                  className={activeTab === id ? 'active' : ''}
                  onClick={() => setActiveTab(id)}
                  key={id}
                >
                  {label}
                </button>
              ))}
            </div>
            <div className="member-empty-state">
              <span><Search size={28} /></span>
              <h2>{title}</h2>
              <p>{description}</p>
              <Link to="/jobs">채용공고 둘러보기</Link>
            </div>
          </section>
        </div>
      </div>
    </main>
  );
};

export default MyDevJobsPage;
