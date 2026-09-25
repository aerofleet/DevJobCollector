import { AlertTriangle, ArrowUpRight, Building2, BriefcaseBusiness, RefreshCw, UserPlus, Users } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api/adminApi';

const cards = [
  { key: 'totalUsers', label: '전체 일반 회원', icon: Users, tone: 'blue' },
  { key: 'weeklySignups', label: '금주 신규 가입', icon: UserPlus, tone: 'cyan' },
  { key: 'pendingCompanies', label: '인증 대기 기업', icon: Building2, tone: 'amber' },
  { key: 'activeJobs', label: '활성 공고', icon: BriefcaseBusiness, tone: 'violet' },
];

const MetricCard = ({ definition, metric }) => {
  const Icon = definition.icon;
  const available = metric?.dataAvailable !== false && metric?.value !== null && metric?.value !== undefined;
  return (
    <article className="metric-card">
      <div className={`metric-icon ${definition.tone}`}><Icon size={20} /></div>
      <span>{definition.label}</span>
      <strong>{available ? Number(metric.value).toLocaleString('ko-KR') : 'N/A'}</strong>
      <small>{available ? metric.description || '집계 기준 시각 현재' : '데이터 연결 예정'}</small>
    </article>
  );
};

const DashboardPage = () => {
  const [state, setState] = useState({ loading: true, data: null, error: '' });

  const load = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: '' }));
    try {
      const response = await adminApi.dashboardSummary();
      setState({ loading: false, data: response?.data || response, error: '' });
    } catch (error) {
      setState({ loading: false, data: null, error: error.requestId ? `${error.message} · ${error.requestId}` : error.message });
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  if (state.loading) {
    return <div className="panel-state" role="status"><span className="spinner" /> 운영 지표를 불러오고 있습니다.</div>;
  }

  if (state.error) {
    return (
      <div className="panel-state error-state" role="alert">
        <AlertTriangle size={24} />
        <div><strong>대시보드 데이터를 불러오지 못했습니다.</strong><p>{state.error}</p></div>
        <button type="button" onClick={load}><RefreshCw size={16} /> 다시 시도</button>
      </div>
    );
  }

  const metrics = state.data?.metrics || {};
  const signupTrend = state.data?.signupTrend || [];
  const trendMaximum = Math.max(...signupTrend.map((point) => Number(point.value) || 0), 1);
  const pendingVerifications = Number(
    state.data?.reviewQueue?.pendingCompanyVerifications ?? metrics.pendingCompanies?.value ?? 0,
  );
  return (
    <div className="dashboard-grid">
      <section className="page-heading">
        <div><p className="eyebrow">OVERVIEW</p><h2>오늘의 운영 현황</h2><p>{state.data?.timezone || 'Asia/Seoul'} · {state.data?.asOf ? new Date(state.data.asOf).toLocaleString('ko-KR') : '기준 시각 없음'}</p></div>
        <button className="secondary-button" type="button" onClick={load}><RefreshCw size={16} /> 새로고침</button>
      </section>
      <section className="metric-grid" aria-label="핵심 운영 지표">
        {cards.map((card) => <MetricCard key={card.key} definition={card} metric={metrics[card.key]} />)}
      </section>
      <section className="dashboard-panel wide-panel">
        <div className="panel-header"><div><span>SIGNUP TREND</span><h3>최근 7일 신규 가입</h3></div><UserPlus size={20} /></div>
        {signupTrend.length === 0 ? (
          <div className="empty-state"><UserPlus size={28} /><strong>가입 추이 데이터가 없습니다.</strong><p>집계가 시작되면 일별 가입자 수를 표시합니다.</p></div>
        ) : (
          <div className="trend-chart" aria-label="최근 7일 신규 가입 추이">
            {signupTrend.map((point) => (
              <div className="trend-column" key={point.date}>
                <span>{Number(point.value).toLocaleString('ko-KR')}</span>
                <div className="trend-track"><i style={{ height: `${Math.max((Number(point.value) / trendMaximum) * 100, 4)}%` }} /></div>
                <small>{new Intl.DateTimeFormat('ko-KR', { month: 'numeric', day: 'numeric' }).format(new Date(`${point.date}T00:00:00+09:00`))}</small>
              </div>
            ))}
          </div>
        )}
      </section>
      <section className="dashboard-panel">
        <div className="panel-header"><div><span>REVIEW QUEUE</span><h3>우선 확인할 운영 항목</h3></div><ArrowUpRight size={20} /></div>
        <div className="queue-summary"><Building2 size={28} /><span>기업 인증 검토 대기</span><strong>{pendingVerifications.toLocaleString('ko-KR')}건</strong><p>요청 시간이 오래된 항목부터 확인하세요.</p></div>
      </section>
    </div>
  );
};

export default DashboardPage;
