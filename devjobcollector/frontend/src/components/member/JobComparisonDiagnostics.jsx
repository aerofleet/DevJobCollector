import { comparisonSummary } from '../../utils/jobComparison';

const issues = {
  NO_RESUME_PASSAGES: '전송된 이력서에 인용 가능한 구간이 없음',
  NO_JOB_PASSAGES: '전송된 공고에 인용 가능한 구간이 없음',
  JOB_REQUIREMENTS_NOT_COLLECTED: '공고의 상세 자격 요건이 수집되지 않음 — 이력서 부족과는 별개입니다',
  DUPLICATE_JOB_SLOT: '같은 공고의 AI 비교 결과가 중복됨',
  INVALID_RELEVANCE: 'AI 관련성 분류가 올바르지 않음',
  INVALID_CRITERIA_FORMAT: 'AI 비교 항목의 형식 또는 개수가 올바르지 않음',
  INVALID_RESUME_REFERENCE: 'AI가 지정한 이력서 인용 위치를 원문에서 찾을 수 없음',
  INVALID_JOB_REFERENCE: 'AI가 지정한 공고 인용 위치를 원문에서 찾을 수 없음',
  MISSING_COMPARISON_EXPLANATION: '비교 설명이 없거나 허용 길이를 초과함',
  INVALID_ASSESSMENT: '비교 항목의 판정 형식이 올바르지 않음',
  UNSUPPORTED_RESUME_CLAIM: '이력서 근거가 확인되지 않은 항목을 충족한 것으로 판단함',
  RESUME_QUOTE_NOT_VERBATIM: '이력서 인용문이 원문과 일치하지 않거나 비식별 처리된 내용을 포함함',
  UNSUPPORTED_NUMERIC_CLAIM: '비교 설명에 원문에서 확인되지 않는 수치가 포함됨',
  DECISION_NOT_GROUNDED: '최종 판정 설명이 인용 근거 검증을 통과하지 못함',
  UNSUPPORTED_RECOMMENDATION: '충족 근거 없이 추천으로 판단함',
  UNSUPPORTED_REJECTION: '지원자 근거 없이 부적합으로 판단함',
  INVALID_DECISION: '최종 적합성 판정이 없거나 형식이 올바르지 않음',
};

export default function JobComparisonDiagnostics({ review }) {
  if (!review) return null;
  const diagnostic = review.comparisonDiagnostics;
  if (!diagnostic) return comparisonSummary(review) && <p className="analysis-note">새 검증 기준으로 확인하려면 AI 내용 분석에서 전송 미리보기를 확인한 후 재검토해 주세요. 자동으로 AI를 다시 호출하지 않습니다.</p>;
  const failures = Object.entries(diagnostic.issues || {}).filter(([, count]) => count > 0);
  return <div className="comparison-diagnostics">
    <p>비교 항목 {diagnostic.submittedCriteria}개 중 {diagnostic.acceptedCriteria}개의 인용문을 확인했습니다. 이는 요건 충족을 뜻하지 않습니다.</p>
    <details><summary>인용 검증 상세</summary><p className="analysis-note">전송된 인용 구간: 이력서 {diagnostic.resumePassageCount}개 · 공고 {diagnostic.jobPassageCount}개</p></details>
    {failures.length > 0 && <ul>{failures.map(([code, count]) => <li key={code}>{issues[code] || '비교 결과 검증 실패'} ({count}건)</li>)}</ul>}
    {diagnostic.uncitedResumeCriteria > 0 && <p>비교 항목 {diagnostic.uncitedResumeCriteria}개에는 이력서 인용이 없습니다. 해당 경험이 없다는 뜻은 아니며, 아래 공고 요건과 연결할 등록 자료를 확인해 주세요.</p>}
  </div>;
}
