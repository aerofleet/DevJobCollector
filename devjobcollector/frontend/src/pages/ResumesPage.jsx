import React, { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, FilePlus2, FileText, Lightbulb, MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import MemberSidebar from '../components/member/MemberSidebar';
import { deleteResume, listResumes } from '../api/resumeApi';
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
  const [deleteErrorMessage, setDeleteErrorMessage] = useState('');
  const [deletingResumeId, setDeletingResumeId] = useState(null);
  const [openMenuId, setOpenMenuId] = useState(null);

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

  useEffect(() => {
    const closeMenu = (event) => {
      if (!event.target.closest('[data-resume-menu]')) {
        setOpenMenuId(null);
      }
    };
    const closeMenuWithEscape = (event) => {
      if (event.key === 'Escape') {
        setOpenMenuId(null);
      }
    };

    document.addEventListener('pointerdown', closeMenu);
    document.addEventListener('keydown', closeMenuWithEscape);
    return () => {
      document.removeEventListener('pointerdown', closeMenu);
      document.removeEventListener('keydown', closeMenuWithEscape);
    };
  }, []);

  const handleDelete = async (resume) => {
    setOpenMenuId(null);
    if (!window.confirm(`"${resume.title}" 이력서를 삭제하시겠습니까?\n삭제한 이력서는 복구할 수 없습니다.`)) {
      return;
    }

    setDeletingResumeId(resume.id);
    setDeleteErrorMessage('');
    try {
      await deleteResume(resume.id);
      setResumes((current) => current.filter((item) => item.id !== resume.id));
    } catch (error) {
      if (error.response?.status !== 401) {
        setDeleteErrorMessage('이력서를 삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      setDeletingResumeId(null);
    }
  };

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
              {deleteErrorMessage && (
                <p className="resume-delete-error" role="alert">{deleteErrorMessage}</p>
              )}
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
                  <div className="resume-card-menu" data-resume-menu>
                    <button
                      className="resume-menu-trigger"
                      type="button"
                      aria-label={`${resume.title} 메뉴 열기`}
                      aria-haspopup="menu"
                      aria-expanded={openMenuId === resume.id}
                      aria-controls={`resume-menu-${resume.id}`}
                      onClick={() => setOpenMenuId((current) => (current === resume.id ? null : resume.id))}
                    >
                      <MoreHorizontal size={22} />
                    </button>
                    {openMenuId === resume.id && (
                      <div className="resume-overflow-menu" id={`resume-menu-${resume.id}`} role="menu">
                        <Link role="menuitem" to={`/resume?resumeId=${resume.id}`} onClick={() => setOpenMenuId(null)}>
                          <Pencil size={16} /> 수정
                        </Link>
                        <button
                          className="danger"
                          type="button"
                          role="menuitem"
                          disabled={deletingResumeId !== null}
                          onClick={() => handleDelete(resume)}
                        >
                          <Trash2 size={16} />
                          {deletingResumeId === resume.id ? '삭제 중' : '삭제'}
                        </button>
                      </div>
                    )}
                  </div>
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
