const statuses = {
  MODEL_OMITTED: ['AI 비교 누락', 'AI 응답에 이 공고의 비교 결과가 없습니다. 등록된 경력이나 기술이 부족하다고 확인된 것은 아닙니다.'],
  EMPTY_COMPARISON: ['AI 비교 미완료', 'AI가 공고별 비교 항목을 작성하지 않았습니다. 등록 자료 부족으로 판단할 수 없습니다.'],
  VALIDATION_FAILED: ['AI 근거 검증 실패', 'AI가 작성한 비교 또는 판정이 검증을 통과하지 못했습니다. 아래 사유를 확인해 주세요.'],
  INPUT_MISSING: ['전송 자료 확인 필요', '비교에 필요한 전송 자료가 부족합니다. 아래에 표시된 자료의 수집 상태와 전송 미리보기를 확인해 주세요.'],
};

export function comparisonSummary(review) {
  if (!review) return null;
  const summary = statuses[review.comparisonDiagnostics?.status];
  if (summary) return { label: summary[0], reason: summary[1] };
  if (!review.comparisonDiagnostics && review.decision === 'INSUFFICIENT_INFORMATION' && !review.criteria?.length) {
    return { label: '비교 원인 미기록', reason: '이전 결과에는 AI 비교 누락과 인용 검증 실패를 구분한 기록이 없습니다. 등록 자료가 부족한지 이 결과만으로 판단할 수 없습니다.' };
  }
  return null;
}
