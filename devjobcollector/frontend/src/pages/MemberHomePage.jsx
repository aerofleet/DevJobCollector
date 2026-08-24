import React from 'react';
import { ArrowRight, Bookmark, BriefcaseBusiness, FileText, Search, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import MemberSidebar from '../components/member/MemberSidebar';
import '../styles/MemberPages.css';

const MemberHomePage = () => (
  <main className="member-page">
    <div className="member-layout">
      <MemberSidebar />
      <div className="member-main">
        <section className="member-welcome">
          <div>
            <span className="member-eyebrow"><Sparkles size={15} /> MY CAREER SPACE</span>
            <h1>내 커리어를 한곳에서<br />차근차근 완성해보세요.</h1>
            <p>관심 공고를 모으고, 지원 현황과 이력서를 한 번에 관리할 수 있어요.</p>
          </div>
          <div className="welcome-orbit" aria-hidden="true">
            <span><BriefcaseBusiness size={28} /></span>
            <span><FileText size={25} /></span>
            <strong>MY</strong>
          </div>
        </section>

        <section className="member-quick-grid" aria-label="커리어 관리 바로가기">
          <Link className="member-quick-card jobs" to="/my-devjobs">
            <span className="quick-icon"><Bookmark size={24} /></span>
            <div>
              <small>JOB ACTIVITY</small>
              <h2>마이데브잡</h2>
              <p>저장한 공고와 지원 현황을 모아서 확인하세요.</p>
            </div>
            <ArrowRight size={22} aria-hidden="true" />
          </Link>
          <Link className="member-quick-card resume" to="/resumes">
            <span className="quick-icon"><FileText size={24} /></span>
            <div>
              <small>MY RESUME</small>
              <h2>이력서 관리</h2>
              <p>경력과 프로젝트를 정리하고 이력서를 완성하세요.</p>
            </div>
            <ArrowRight size={22} aria-hidden="true" />
          </Link>
        </section>

        <section className="member-guide-card">
          <div>
            <span className="member-eyebrow">NEXT STEP</span>
            <h2>지금 채용 중인 포지션을 둘러보세요.</h2>
            <p>기술 스택과 경력 조건으로 나에게 맞는 개발자 공고를 찾아볼 수 있어요.</p>
          </div>
          <Link to="/jobs"><Search size={18} /> 채용공고 찾기</Link>
        </section>
      </div>
    </div>
  </main>
);

export default MemberHomePage;
