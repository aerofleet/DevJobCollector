# DJC Zero Open Ports 1단계 실행 런북

## 목표와 범위

GitHub-hosted Runner의 유동 IP와 OCI SSH 허용 목록 불일치로 발생하는 배포 타임아웃을 제거한다. 기존 Cloudflare Tunnel에 SSH 경로를 추가하고 Access Service Token으로 CI 접근을 인증한다.

- 이번 단계에서는 OCI TCP 22 인바운드를 삭제하지 않는다.
- Cloudflare Access와 SSH 키의 이중 인증을 유지한다.
- HTTP Tunnel, DB, OCI Route Table은 변경하지 않는다.

## Cloudflare 설정

기존 DJC Tunnel의 Published application에 다음 경로를 추가한다.

```text
Hostname: <SSH_TUNNEL_HOST>
Service: ssh://localhost:22
```

Access에 Self-hosted application을 생성한다.

```text
Application domain: <SSH_TUNNEL_HOST>
Policy action: Service Auth
Include: Service Token = djc-github-actions
```

Service Token 생성 직후 표시되는 값을 GitHub Repository Secrets에 저장한다. Client Secret은 생성 시점에만 조회할 수 있다.

```text
CF_ACCESS_CLIENT_ID
CF_ACCESS_CLIENT_SECRET
```

## 워크플로 동작

배포 Runner는 `cloudflared access ssh`를 OpenSSH `ProxyCommand`로 사용한다. SSH 키는 origin의 sshd 인증에 계속 사용한다.

`cloudflared` 클라이언트는 `2026.5.1`로 고정하고 GitHub Release에 게시된 SHA-256 digest를 검증한 뒤 설치한다. `2026.6.0`에서 Access Service Token을 무시하고 브라우저 인증으로 전환되는 공개 회귀 이슈가 있어, 수정 버전의 동작이 검증될 때까지 자동 최신 버전을 사용하지 않는다.

## 검증 절차

GitHub Actions에서 배포 워크플로를 실행하고 다음 단계를 확인한다.

```text
Build and push deploy image                 success
Validate deployment secrets                success
Install cloudflared client                 success
Deploy container through Cloudflare Tunnel success
Deployment completed                       success
```

배포 후 공개 API를 검증한다.

```bash
curl --fail --silent https://<API_DOMAIN>/actuator/health
curl --fail --silent "https://<API_DOMAIN>/api/v1/jobs/search?page=0&size=1"
```

1단계 롤백 경로 확인을 위해 기존 직접 SSH 규칙은 유지한다. TCP 22 삭제는 Tunnel SSH, 시리얼 콘솔, 연속 배포 검증 후 별도 단계에서 수행한다.

## KPI / OKR / 평가셋

- 목표 KPI
  - GitHub Actions 배포 성공률: 3회 연속 100%
  - Tunnel SSH 연결 수립: 각 시도 20초 이내
  - 컨테이너 health 전환: 배포 시작 후 90초 이내 `UP`
  - 배포 후 공개 API 오류율: 검증 요청 2건 기준 0%
- OKR 연결
  - Runner IP 변경과 무관한 재현 가능한 DJC 배포 경로 확보
  - 운영 SSH 공격 표면 제거를 위한 2단계 진입 조건 충족
- 평가셋
  - `workflow_dispatch` 배포 3회
  - 각 실행에서 Tunnel SSH, Compose pull/up, health check
  - 공개 health 1건과 채용 검색 API 1건
- Before
  - 직접 SSH 배포가 TCP 22 timeout으로 실패
  - Docker 이미지는 GHCR에 게시됐으나 OCI 배포 단계 미실행
- After
  - 최초 배포 및 3회 연속 검증 후 실제 측정값을 기록한다.
- 합격 기준
  - 위 3회 배포 성공
  - health 응답 `UP`
  - 직접 TCP 22 규칙을 유지한 상태에서 Tunnel 경로로만 배포 완료
