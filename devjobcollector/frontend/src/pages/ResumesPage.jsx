import React, { useCallback, useEffect, useState } from 'react';
import { ArrowRight, CheckCircle2, FilePlus2, FileText, Lightbulb } from 'lucide-react';
import { Link } from 'react-router-dom';
import MemberSidebar from '../components/member/MemberSidebar';
import { listResumes } from '../api/resumeApi';
import '../styles/MemberPages.css';

const RESUME_CHECKS = ['기본 정보', '기술 스택', '프로젝트', '경력'];
const STATUS_LABELS = {
  DRAFT: '작성 중',
  READY: '작성 완료',
  ARCHIVED: '보관됨',
};

const formatUpdatedAt = (value) => {
  if (!value) return '수정 시각 없음';
  return `${new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))} 수정`;
};

const ResumesPage = () => {
  const [resumes, setResumes] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  const loadResumes = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      setResumes(await listResumes());
    } catch (error) {
      if (error.response?.status !== 401) {
        setErrorMessage('이력서 목록을 불러오지 못했습니다.');
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadResumes();
  }, [loadResumes]);

  return (
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

          {isLoading && <section className="member-activity-state">이력서 목록을 불러오는 중입니다.</section>}
          {!isLoading && errorMessage && (
            <section className="member-activity-state error">
              <p>{errorMessage}</p>
              <button type="button" onClick={loadResumes}>다시 시도</button>
            </section>
          )}
          {!isLoading && !errorMessage && resumes.length === 0 && (
            <section className="member-empty-state resume-empty-state">
              <span><FileText size={28} /></span>
              <h2>작성한 이력서가 없습니다.</h2>
              <p>첫 이력서를 작성하고 지원 준비를 시작해 보세요.</p>
              <Link to="/resume">이력서 작성하기</Link>
            </section>
          )}
          {!isLoading && !errorMessage && resumes.length > 0 && (
            <div className="resume-list">
              {resumes.map((resume) => (
                <section className="resume-manager-card" key={resume.id}>
                  <div className="resume-document-icon"><FileText size={30} /></div>
                  <div className="resume-card-copy">
                    <span className={`resume-status status-${resume.status.toLowerCase()}`}>
                      {STATUS_LABELS[resume.status] || resume.status}
                    </span>
                    <h2>{resume.title}</h2>
                    <p>{formatUpdatedAt(resume.updatedAt)}</p>
                    <div className="resume-check-list">
                      {RESUME_CHECKS.map((item) => <span key={item}><CheckCircle2 size={15} /> {item}</span>)}
                    </div>
                  </div>
                  <Link className="resume-edit-link" to={`/resume?resumeId=${resume.id}`}>
                    수정하기 <ArrowRight size={17} />
                  </Link>
                </section>
              ))}
            </div>
          )}

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
};

export default ResumesPage;
