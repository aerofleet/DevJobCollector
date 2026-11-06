# 공고 추출 진단과 보정

진단과 보정은 서로 독립적이다. 진단은 읽기 전용 스냅샷에서 화면과 동일한 추출기를 실행해 빈 본문과 미인식 섹션을 집계한다. 미인식은 원문 미제공과 추출 실패를 모두 포함하므로 확정 누락이 아니다. 공공 ALIO는 상세 필드 구조가 달라 별도 건수로만 계산한다. 항목별 건수는 서로 중복된다.

DB에 읽기 권한만 가진 별도 계정을 사용한다. MySQL 옵션 파일에는 접속 정보만 넣고 접근 권한을 제한한다. 아래 명령은 저장 파일 없이 본문을 파이프로 전달하고 집계값만 출력한다. DB 세션 시간대와 서비스 날짜가 일치해야 한다. `--raw`가 없으면 JSON 문자열 이스케이프가 바뀔 수 있다.

```bash
mysql --defaults-extra-file=/path/to/read-only.cnf --batch --raw --skip-column-names DATABASE_NAME < ops/db/job-extraction-snapshot.sql | node frontend/scripts/audit-job-extraction.mjs
```

파이프 실행 시 `set -o pipefail`을 설정해 DB 조회 실패도 실패로 처리한다. 빈 입력·중복 ID·잘못된 JSON은 보고서를 생성하지 않는다. `.github/workflows/djc-job-extraction-audit.yml`은 매일 한국시간 04:15와 수동 실행을 지원하며 전용 읽기 계정의 `DJC_AUDIT_DB_USERNAME`, `DJC_AUDIT_DB_PASSWORD` 저장소 secret이 필요하다. 계정은 `devjob.job_posts` SELECT만 허용한다. 워크플로가 기본 브랜치에 반영되고 secrets가 설정되기 전까지 운영 스케줄은 활성화되지 않는다. DB 원문은 파이프로만 전달하고 집계 결과만 출력한다.

보정은 `frontend/src/utils/jobSectionRecovery.js`가 원문에 독립적으로 명시된 제목만 인식하고 상세 페이지에서 추출 결과를 계산하는 방식이다. DB 수정, 원문 재수집, 급여 백필, AI 호출은 없다. 새로운 제목이 허용 범위 밖이면 미인식으로 남고 수동 검토가 필요하다.

검증 기준: 합성 평가셋에서 명시된 업무·자격·우대 3종 구분과 모호한 제목 3종 거부가 모두 통과해야 한다. 운영 KPI 목표는 원문에 없는 항목의 자동 생성 0건이다. 원문 대조 평가셋과 운영 전수 진단 전이므로 실제 탐지율·오분류율·개선 전후 값은 미측정이며, 사업 목표는 공고 정보 확인에 필요한 원문 재방문 감소다. 전후 수치 확인 없이 성능 개선을 확정하지 않는다.
