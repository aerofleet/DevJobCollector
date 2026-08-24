import React from 'react';
import { ArrowRight, CheckCircle2, FilePlus2, FileText, Lightbulb } from 'lucide-react';
import { Link } from 'react-router-dom';
import MemberSidebar from '../components/member/MemberSidebar';
import '../styles/MemberPages.css';

const RESUME_CHECKS = ['기본 정보', '기술 스택', '프로젝트', '경력'];

const ResumesPage = () => (
  <main className="member-page">
    <div className="member-layout">
      <MemberSidebar />
      <div className="member-main">
        <header className="member-page-heading resume-heading-row">
          <div>
            <span className="member-eyebrow">MY RESUME</span>
            <h1>이력서 관리</h1>
            <p>나의 경험과 강점을 정리해 지원 준비를 시작하세요.</p>
          </div>
          <Link className="primary-member-button" to="/resume"><FilePlus2 size={18} /> 이력서 작성하기</Link>
        </header>

        <section className="resume-manager-card">
          <div className="resume-document-icon"><FileText size={30} /></div>
          <div className="resume-card-copy">
            <span className="resume-status">작성 중</span>
            <h2>개발자 이력서</h2>
            <p>기본 정보부터 프로젝트와 경력까지 순서대로 작성할 수 있어요.</p>
            <div className="resume-check-list">
              {RESUME_CHECKS.map((item) => <span key={item}><CheckCircle2 size={15} /> {item}</span>)}
            </div>
          </div>
          <Link className="resume-edit-link" to="/resume">이어 작성하기 <ArrowRight size={17} /></Link>
        </section>

        <section className="resume-tip-card">
          <span><Lightbulb size={22} /></span>
          <div>
            <strong>좋은 이력서는 구체적인 결과를 보여줘요.</strong>
            <p>프로젝트에서 해결한 문제와 본인의 기여, 수치로 확인할 수 있는 결과를 함께 작성해보세요.</p>
          </div>
        </section>
      </div>
    </div>
  </main>
);

export default ResumesPage;
