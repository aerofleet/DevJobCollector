import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router-dom';
import { ArrowUpRight, Check, FileText, Search, ShieldCheck, Sparkles, X } from 'lucide-react';
import { comparisonSummary } from '../../utils/jobComparison';
import JobComparisonDiagnostics from './JobComparisonDiagnostics';
import './RecommendationReasonDialog.css';

const decisions = {
  RECOMMENDED: { label: 'AI 추천', tone: 'match' },
  STRETCH: { label: '도전 지원', tone: 'check' },
  NOT_RECOMMENDED: { label: '부적합', tone: 'check' },
  INSUFFICIENT_INFORMATION: { label: '정보 부족', tone: 'check' },
  REVIEW_REQUIRED: { label: '재검토 필요', tone: 'check' },
};
const relevanceNames = { RELATED: '연결되는 경험 있음', REVIEW_NEEDED: '지원 전 추가 확인', LOW_RELEVANCE: '관련성 낮음' };
const isSupported = criterion => criterion.assessment === 'SUPPORTED' && Boolean(criterion.resumeQuote?.trim());

export default function RecommendationReasonDialog({ job, review, onClose }) {
  const dialogRef = useRef(null);
  const triggerRef = useRef(null);
  const backdropPress = useRef(false);
  const titleId = useId();
  const criteriaTitleId = useId();
  const criteria = review?.criteria || [];
  const supportedCount = criteria.filter(isSupported).length;
  const comparison = comparisonSummary(review);
  const decision = comparison ? { label: comparison.label, tone: 'check' } : decisions[review?.decision];
  const needsReview = Boolean(comparison || review?.decision === 'REVIEW_REQUIRED');

  useEffect(() => {
    const dialog = dialogRef.current;
    const trigger = triggerRef.current || document.activeElement;
    triggerRef.current = trigger;
    const previousOverflow = document.body.style.overflow;
    if (!dialog.open) dialog.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previousOverflow;
      if (trigger?.isConnected) trigger.focus({ preventScroll: true });
    };
  }, []);

  const close = () => dialogRef.current?.close();
  const isBackdrop = event => {
    if (event.target !== event.currentTarget) return false;
    const { left, right, top, bottom } = event.currentTarget.getBoundingClientRect();
    return event.clientX < left || event.clientX > right || event.clientY < top || event.clientY > bottom;
  };
  const keepFocusInDialog = event => {
    if (event.key !== 'Tab') return;
    const first = event.currentTarget.querySelector('.rr-close');
    const last = event.currentTarget.querySelector('.rr-footer a');
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault(); last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault(); first.focus();
    }
  };

  return createPortal(<dialog ref={dialogRef} className="recommendation-reason-dialog" aria-labelledby={titleId}
    onClose={onClose} onKeyDown={keepFocusInDialog} onPointerDown={event => { backdropPress.current = isBackdrop(event); }}
    onClick={event => { if (backdropPress.current && isBackdrop(event)) close(); backdropPress.current = false; }}>
    <header className="rr-header">
      <div className="rr-heading">
        <span className="rr-eyebrow"><Sparkles size={13} aria-hidden="true" /> 공고 적합도 분석</span>
        <h2 id={titleId}>추천 근거</h2>
        <p className="rr-role">{job.title}</p>
        {job.companyName && <p className="rr-company">{job.companyName}</p>}
      </div>
      <button type="button" className="rr-close" aria-label="추천 근거 닫기" onClick={close}><X size={20} aria-hidden="true" /></button>
    </header>

    <div className="rr-body" tabIndex={0} role="region" aria-label="추천 근거 상세 내용">
      <section className={`rr-summary${decision ? ` rr-summary-${decision.tone}` : ''}`} aria-label="추천 요약">
        <div className="rr-badges">
          {decision && <span className={`rr-badge rr-badge-${decision.tone}`}>{decision.label}</span>}
          <span className="rr-badge rr-badge-ai">{review ? <ShieldCheck size={13} aria-hidden="true" /> : <Search size={13} aria-hidden="true" />}
            {review ? (needsReview ? 'AI 검토 확인 필요' : 'AI 검토') : '기술·경력 기반'}</span>
        </div>
        <p>{comparison?.reason || review?.decisionReason || (review ? relevanceNames[review.relevance] : null) || '이력서의 희망 직무와 연결되는 공고예요.'}</p>
        {criteria.length > 0 && <div className="rr-counts" aria-label="비교 항목 요약">
          <span><i className="rr-dot rr-dot-match" aria-hidden="true" />관련 경험 <b>{supportedCount}</b></span>
          <span><i className="rr-dot rr-dot-check" aria-hidden="true" />추가 확인 <b>{criteria.length - supportedCount}</b></span>
        </div>}
      </section>

      {(job.matchPoints?.length > 0 || job.missingPoints?.length > 0) && <section className="rr-signals" aria-label="추천 조건">
        {job.matchPoints?.length > 0 && <div><h3>일치 조건</h3><ul className="rr-chips">{job.matchPoints.map((point, index) => <li className="rr-chip rr-chip-match" key={index}><Check size={13} aria-hidden="true" />{point}</li>)}</ul></div>}
        {job.missingPoints?.length > 0 && <div><h3>추가 확인</h3><ul className="rr-chips">{job.missingPoints.map((point, index) => <li className="rr-chip rr-chip-check" key={index}><Search size={13} aria-hidden="true" />{point}</li>)}</ul></div>}
      </section>}

      {(review?.lead_with || review?.gap) && <section className="rr-coaching" aria-label="지원 준비 포인트">
        {review.lead_with && <div><h3><Sparkles size={14} aria-hidden="true" />앞세울 경험</h3><p>{review.lead_with}</p></div>}
        {review.gap && <div className="rr-coaching-gap"><h3><Search size={14} aria-hidden="true" />확인할 점</h3><p>{review.gap}</p></div>}
      </section>}

      <section className="rr-matches" aria-labelledby={criteriaTitleId}>
        <div className="rr-section-heading"><h3 id={criteriaTitleId}>요건별 상세 매칭 결과</h3><span>{criteria.length}개 항목</span></div>
        {criteria.length ? <ol className="rr-match-list">{criteria.map((criterion, index) => {
          const supported = isSupported(criterion);
          const StatusIcon = supported ? Check : Search;
          return <li key={index} className={`rr-match-card rr-match-card-${supported ? 'match' : 'check'}`}>
            <div className="rr-card-heading"><span className={`rr-badge rr-badge-${supported ? 'match' : 'check'}`}><StatusIcon size={13} aria-hidden="true" />{supported ? '관련 경험' : '추가 검토 필요'}</span><span className="rr-card-index">{String(index + 1).padStart(2, '0')}</span></div>
            <div className="rr-requirement"><h4>공고 요건</h4><small>{({ REQUIRED: '필수', PREFERRED: '우대' })[criterion.requirementType] || '구분 확인 필요'}</small><p>{criterion.jobQuote || '공고 요건의 인용 근거를 확인해 주세요.'}</p></div>
            <div className="rr-evidence"><h4><FileText size={13} aria-hidden="true" />이력서 근거</h4><p>{criterion.resumeQuote?.trim() || '이 요건과 비교할 이력서 근거를 확인하지 못했어요.'}</p>
              {(criterion.label || criterion.page) && <small>{[criterion.label, criterion.page ? `${criterion.page}쪽` : null].filter(Boolean).join(' · ')}</small>}
            </div>
            {criterion.explanation && <div className="rr-interpretation"><h4>매칭 해석</h4><p>{supported ? '인용된 경험이 요건과 연결될 수 있습니다. 요건 충족 여부는 직접 확인해 주세요.' : '제공된 자료의 인용만으로 이 요건을 확인하기 어렵습니다. 경험이 없다는 뜻은 아닙니다.'}</p></div>}
          </li>;
        })}</ol> : <div className="rr-empty"><FileText size={22} aria-hidden="true" /><p>{review ? '공고와 이력서를 비교할 검증된 인용 근거가 없어요. 지원자의 경험이 부족하다는 판정은 아니에요.' : '요건별 인용 비교는 아직 없어요. 현재는 이력서의 희망 직무와 기술·경력 조건으로 연결한 공고예요.'}</p></div>}
        {review && needsReview && <div className="rr-diagnostics"><JobComparisonDiagnostics review={review} /></div>}
      </section>
    </div>

    <footer className="rr-footer"><span>이력서 매칭 리포트</span><Link to={`/job/${job.jobId}`} onClick={close}>공고 상세 보기 <ArrowUpRight size={15} aria-hidden="true" /></Link></footer>
  </dialog>, document.body);
}
