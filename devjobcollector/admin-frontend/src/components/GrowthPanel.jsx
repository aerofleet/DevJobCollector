import { useEffect, useState } from 'react';
import { adminRequest } from '../api/adminApi';

const labels = { PAGE_VIEW: '페이지 방문', SEARCH: '검색 결과 진입', JOB_VIEW: '공고 상세 조회', BOOKMARK: '공고 저장 성공', APPLY_CLICK: '지원 클릭', SIGNUP: '이메일 가입 완료', LOGIN: '로그인 완료', SHARE: '공유 완료' };
export default function GrowthPanel() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(false);
  const load = () => { setError(false); adminRequest('/dashboard/growth').then(setData).catch(() => setError(true)); };
  useEffect(() => { let active = true; adminRequest('/dashboard/growth').then((value) => { if (active) setData(value); }).catch(() => { if (active) setError(true); }); return () => { active = false; }; }, []);
  return <section className="dashboard-panel wide-panel">
    <div className="panel-header"><div><span>ACQUISITION & ACTIVATION</span><h3>최근 28일 유입과 활성화</h3></div><button type="button" className="secondary-button" onClick={load}>통계 새로고침</button></div>
    {error ? <p role="alert">이용 통계를 불러오지 못했습니다. 새로고침으로 다시 시도해주세요.</p> : !data ? <p role="status">이용 통계를 불러오는 중입니다.</p> : <>
      <p>{data.scope} · 날짜 기준 UTC</p>
      <p>활성화 = 공고 저장 성공 또는 지원 클릭. 아래 단계는 순서 전환율이 아닌 독립 행동 집계입니다.</p>
      {!data.stages?.length ? <p>수집된 데이터가 없습니다. 이용 통계에 동의한 방문부터 집계합니다.</p> : <div style={{ overflowX: 'auto' }}><table><thead><tr><th>행동</th><th>이벤트</th><th>브라우저</th><th>세션</th></tr></thead><tbody>{data.stages.map((row) => <tr key={row.kind}><td>{labels[row.kind] || row.kind}</td><td>{row.events}</td><td>{row.visitors}</td><td>{row.sessions}</td></tr>)}</tbody></table></div>}
      <h4>유입 채널별 세션 활성화율</h4>
      <div style={{ overflowX: 'auto' }}><table><thead><tr><th>출처 / 매체 / 캠페인</th><th>세션</th><th>활성 세션</th><th>활성화율</th></tr></thead><tbody>{data.channels?.map((row) => <tr key={`${row.source}/${row.medium}/${row.campaign}`}><td>{row.source} / {row.medium} / {row.campaign || '—'}</td><td>{row.sessions}</td><td>{row.activatedSessions}</td><td>{row.sessions ? `${(100 * row.activatedSessions / row.sessions).toFixed(1)}%` : 'N/A'}</td></tr>)}</tbody></table></div>
      <h4>D7 재방문</h4><p>{Number(data.retention?.eligibleVisitors) > 0 ? `${data.retention.returnedVisitors} / ${data.retention.eligibleVisitors}명 (${(100 * data.retention.returnedVisitors / data.retention.eligibleVisitors).toFixed(1)}%)` : 'N/A · 7일차 관찰이 끝난 방문자가 아직 없습니다.'}</p>
      <p>최근 90일 내 최초 관측일부터 정확히 7일 뒤 방문을 집계합니다. 첫 방문 7일차가 완전히 지난 최근 28개 코호트만 포함합니다. 기기·브라우저 변경 또는 식별자 삭제 시 다른 방문자로 계산됩니다. 소셜 신규 가입은 로그인에 포함됩니다.</p>
    </>}
  </section>;
}
