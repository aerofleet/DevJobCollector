import React, { useCallback, useEffect, useState } from 'react';
import { ArrowRight, CheckCircle2, FilePlus2, FileText, Lightbulb, MoreHorizontal, Pencil, Sparkles, Trash2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import MemberSidebar from '../components/member/MemberSidebar';
import { deleteResume, listResumes, updateResumeVisibility } from '../api/resumeApi';
import { getAnalysisCapabilities } from '../api/resumeAnalysisApi';
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
  const [savingVisibilityId, setSavingVisibilityId] = useState(null);
  const [visibilityError, setVisibilityError] = useState('');
  const [analysisAvailable, setAnalysisAvailable] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getAnalysisCapabilities().then((value) => {
      if (!cancelled) setAnalysisAvailable(value.mode === 'LOCAL');
    }).catch(() => {});
    return () => { cancelled = true; };
  }, []);

  const changeVisibility = async (resume, visibility) => {
    setSavingVisibilityId(resume.id);
    setVisibilityError('');
    try {
      const updated = await updateResumeVisibility(resume.id, visibility);
      setResumes((current) => current.map((item) => item.id === resume.id ? updated : item));
    } catch (error) {
      if (error.response?.status !== 401) setVisibilityError('공개 설정을 저장하지 못했습니다. 다시 시도해 주세요.');
    } finally {
      setSavingVisibilityId(null);
    }
  };

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
          {visibilityError && <p role="alert" className="resume-delete-error">{visibilityError}</p>}
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
                    <div className="resume-visibility">
                      <button type="button" className="resume-visibility-switch" role="switch"
                        aria-label={`${resume.title} 공개 설정`}
                        aria-checked={resume.visibility === 'PUBLIC'}
                        disabled={savingVisibilityId !== null || deletingResumeId !== null}
                        onClick={() => changeVisibility(resume, resume.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC')}
                      />
                      <span>{resume.visibility === 'PUBLIC' ? '공개' : '비공개'}</span>
                      <details className="resume-visibility-help">
                        <summary aria-label="이력서 공개 범위 안내">?</summary>
                        <p>{resume.visibility === 'PUBLIC'
                          ? '인증된 기업의 채용 담당자가 이력서의 모든 정보를 확인할 수 있습니다.'
                          : '모든 정보가 비공개됩니다. 본인과 입사지원한 기업의 채용 담당자만 이력서를 확인할 수 있습니다.'}</p>
                      </details>
                      {savingVisibilityId === resume.id && <span role="status">공개 설정 저장 중…</span>}
                    </div>
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
                  {analysisAvailable && (
                    <div className="resume-ai-panel">
                      <div className="resume-ai-copy">
                        <span className="resume-ai-badge"><Sparkles size={14} aria-hidden="true" /> AI 커리어 도우미</span>
                        <h3>내 이력서에서 다음 기회를 찾아보세요</h3>
                        <p>이력서를 점검하고 내 조건에 맞는 공고를 확인하세요.</p>
                        <span className="resume-ai-note">AI 추천 이유 안내는 별도 동의 후 제공됩니다.</span>
                      </div>
                      <Link className="resume-ai-action" to={`/resume-analysis?resumeId=${resume.id}`}
                        aria-label={`${resume.title} 이력서 점검 · 맞춤 공고 보기`}>
                        <Sparkles size={18} aria-hidden="true" />
                        <span>이력서 점검 · 맞춤 공고</span>
                        <ArrowRight size={18} aria-hidden="true" />
                      </Link>
                    </div>
                  )}
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
