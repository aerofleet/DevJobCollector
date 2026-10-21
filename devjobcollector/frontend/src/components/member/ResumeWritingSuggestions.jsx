import { Link } from 'react-router-dom';

const guidance = {
  MISSING_SKILLS: {
    title: '사용한 기술 스택을 정리해 주세요', status: '미작성',
    problem: '저장된 기술 스택 항목에서 기술명을 확인하지 못했습니다.',
    steps: ['프로젝트에서 직접 사용한 언어·프레임워크·데이터베이스를 골라 주세요.', '기술 스택 항목에 추가하고, 프로젝트 설명에는 그 기술을 어디에 사용했는지 적어 주세요.'],
    template: '[기술명]을 사용해 [직접 구현한 기능]을 개발했습니다.',
  },
  MISSING_PROJECT: {
    title: '프로젝트 경험을 추가해 주세요', status: '미작성',
    problem: '저장된 프로젝트 목록이 비어 있습니다.',
    steps: ['직접 참여한 프로젝트를 선택해 제목과 기간을 입력해 주세요.', '해결하려던 문제 → 본인의 역할 → 수행한 작업 → 확인한 결과 순서로 설명해 주세요.'],
    template: '[프로젝트명]에서 [문제]를 해결하기 위해 [내 역할·작업]을 수행했고, [확인한 결과]를 얻었습니다.',
  },
  MISSING_DESCRIPTION: {
    title: '프로젝트 설명을 채워 주세요', status: '미작성',
    problem: '아래 프로젝트의 설명이 비어 있습니다. 제목만으로는 수행한 역할과 결과를 확인하기 어렵습니다.',
    steps: ['각 프로젝트에서 해결한 문제와 본인이 담당한 범위를 구분해 주세요.', '직접 수행한 작업과 확인 가능한 결과를 적고, 관련 코드·문서가 있다면 링크를 덧붙여 주세요.'],
    template: '[문제] 상황에서 [담당 범위]를 맡아 [구체적인 작업]을 수행했습니다. 결과는 [검증 방법·자료]로 확인했습니다.',
  },
  METRIC_NOT_DETECTED: {
    title: '성과를 확인할 근거를 보완해 주세요', status: '보완 검토',
    problem: '이전 분석에서 아래 프로젝트 설명에 숫자를 찾지 못했습니다. 수치가 반드시 누락됐거나 모든 성과에 숫자가 필요하다는 뜻은 아닙니다.',
    steps: ['분석 당시 프로젝트 설명을 확인하고, 개선 결과를 뒷받침할 측정 기록이나 테스트 자료가 있는지 살펴보세요.', '측정값이 있다면 같은 조건에서의 전후 값과 단위를 적어 주세요. 측정값이 없다면 수행한 작업과 확인 방법을 구체적으로 설명해 주세요.'],
    template: '[측정 조건]에서 [지표]가 [측정 전 값·단위] → [측정 후 값·단위]로 달라졌습니다. 근거: [측정 자료].',
  },
};

function targetLabel(path) {
  const project = /^\/projects\/(\d+)(?:\/description)?$/.exec(path || '');
  if (project) return `프로젝트 ${Number(project[1]) + 1} · 설명`;
  if (path === '/projects') return '프로젝트 목록';
  if (path === '/techStack') return '기술 스택';
  return '위치 정보 없음';
}

export default function ResumeWritingSuggestions({ result }) {
  const groups = new Map();
  for (const item of result.improvements || []) {
    // New reports cover measurement suggestions with verified quotations above.
    if (result.review && item.issueType === 'METRIC_NOT_DETECTED') continue;
    const key = guidance[item.issueType] ? item.issueType : `${item.issueType}:${item.feedback}`;
    if (!groups.has(key)) groups.set(key, { item, targets: new Set() });
    groups.get(key).targets.add(targetLabel(item.sourcePath));
  }
  return <section className="writing-suggestions" aria-label="작성 항목별 보완 제안">
    <h3>작성 항목별 보완 제안 <span className="evidence-badge">규칙 검사</span></h3>
    <p className="analysis-note">같은 보완 내용은 한 번만 안내하고, 해당하는 항목을 함께 표시합니다. 위치는 분석 당시 저장된 이력서의 순서입니다. 첨부파일에 이미 설명했다면 중복 작성할 필요는 없습니다.</p>
    {!groups.size && <p>현재 점검 규칙에서 발견한 작성 항목의 보완 제안이 없습니다. 이력서의 완성도를 보장하는 결과는 아닙니다.</p>}
    {[...groups.entries()].map(([key, { item, targets }]) => {
      const guide = guidance[item.issueType];
      return <article key={key} className="writing-suggestion">
        <span className="evidence-badge">{guide?.status || '확인 필요'}</span>
        <h4>{guide?.title || '작성 내용을 확인해 주세요'}</h4>
        <div className="writing-targets"><strong>확인할 항목</strong><ul>{[...targets].map(target => <li key={target}>{target}</li>)}</ul></div>
        <p><strong>보완할 내용</strong><br />{guide?.problem || item.feedback}</p>
        {guide && <><strong>이렇게 진행해 보세요</strong><ol>{guide.steps.map(step => <li key={step}>{step}</li>)}</ol>
          <details className="evidence-template"><summary>작성 예시 보기</summary><p>{guide.template}</p><small>빈칸은 확인 가능한 사실로 채우세요. 수치를 임의로 만들지 마세요.</small></details></>}
        <Link to={`/resume?resumeId=${result.resumeId}`}>이력서에서 해당 항목 수정하기 →</Link>
      </article>;
    })}
  </section>;
}
