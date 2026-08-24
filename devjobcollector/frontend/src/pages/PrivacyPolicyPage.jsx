import { LEGAL_POLICY } from '../config/legalPolicy';
import '../styles/LegalPolicyPage.css';

const PrivacyPolicyPage = () => (
  <main className="legal-page">
    <article className="legal-document">
      <header className="legal-heading">
        <span>DEVJOBS PRIVACY</span>
        <h1>개인정보 처리방침</h1>
        <p>버전 {LEGAL_POLICY.version} · 시행일 {LEGAL_POLICY.effectiveDate}</p>
      </header>

      {LEGAL_POLICY.draft && (
        <aside className="legal-draft-notice" role="status">
          <strong>운영 전 확인이 필요한 초안입니다.</strong>
          <p>운영자·보호책임자·수탁자·국외 처리국가를 확정하고 실제 운영과 일치하는지 검토한 뒤 공개하세요.</p>
        </aside>
      )}

      <p>{LEGAL_POLICY.operatorName}(이하 “운영자”)는 개인정보 보호법 등 관계 법령을 준수하며, 데브잡스 이용자의 개인정보를 다음과 같이 처리합니다.</p>

      <nav className="legal-summary" aria-label="개인정보 처리방침 핵심 요약">
        <strong>핵심 요약</strong>
        <ul>
          <li>회원가입과 인증에 필요한 최소한의 이름·이메일·인증정보를 처리합니다.</li>
          <li>비밀번호 원문과 소셜 로그인 제공자의 비밀번호는 저장하지 않습니다.</li>
          <li>채용공고 열람은 로그인 없이 가능하며, 데브잡스가 이용자를 대신해 입사지원 정보를 기업에 전달하지 않습니다.</li>
        </ul>
      </nav>

      <section>
        <h2>1. 개인정보의 처리 목적, 항목 및 보유기간</h2>
        <div className="legal-table-wrap">
          <table>
            <thead><tr><th>구분</th><th>처리 목적</th><th>처리 항목</th><th>보유기간</th></tr></thead>
            <tbody>
              <tr>
                <td>이메일 회원가입</td>
                <td>본인 식별, 계정 생성, 로그인, 이메일 인증, 부정가입 방지</td>
                <td>이름, 이메일, 비밀번호 해시, 계정 상태, 이메일 인증시각, 인증코드 해시·만료시각·시도횟수</td>
                <td>회원 탈퇴 또는 계정 삭제 완료 시까지. 인증코드는 만료 후 인증 목적으로 사용하지 않음</td>
              </tr>
              <tr>
                <td>Google/GitHub 로그인</td>
                <td>외부 계정을 이용한 본인 식별, 로그인 및 계정 보안</td>
                <td>제공자, 제공자 이용자 식별자, 이름, 이메일, 이메일 확인 여부, 발급자, 최근 로그인 시각</td>
                <td>회원 탈퇴 또는 소셜 계정 연결 해제 및 관련 정보 삭제 완료 시까지</td>
              </tr>
              <tr>
                <td>동의 이력</td>
                <td>약관 및 개인정보 관련 동의 사실 증명</td>
                <td>회원 식별자, 동의 유형, 정책 버전, 동의·철회 구분, 발생시각</td>
                <td>분쟁 대응에 필요한 기간 또는 관계 법령상 보존기간까지</td>
              </tr>
              <tr>
                <td>서비스 보안·운영</td>
                <td>접근통제, 장애 분석, 부정이용 방지, 보안사고 대응</td>
                <td>IP 주소, 요청·접속 일시, 브라우저 및 기기 정보, 서비스 이용기록, 오류·보안 로그</td>
                <td>목적 달성에 필요한 최소 기간. 구체적인 운영 로그 보존기간은 내부 정책 확정 후 공개</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>서비스 이용 과정에서 위 서비스 보안·운영 정보가 자동으로 생성될 수 있습니다. 운영자는 주민등록번호, 건강정보 등 민감정보를 회원가입 항목으로 요구하지 않습니다.</p>
      </section>

      <section>
        <h2>2. 만 14세 미만 아동의 개인정보</h2>
        <p>데브잡스는 원칙적으로 만 14세 미만 아동을 대상으로 하지 않습니다. 만 14세 미만 아동의 정보가 법정대리인 동의 없이 수집된 사실을 확인하면 해당 정보를 지체 없이 삭제합니다.</p>
      </section>

      <section>
        <h2>3. 개인정보의 제3자 제공</h2>
        <p>운영자는 이용자의 개인정보를 제1항의 목적 범위에서 처리하며, 이용자의 별도 동의나 법률상 근거가 없는 한 제3자에게 제공하지 않습니다. 외부 채용공고 또는 입사지원 링크를 선택하면 해당 외부 서비스의 개인정보 처리방침이 적용되며, 데브잡스는 현재 이용자를 대신하여 지원서 개인정보를 기업에 전송하지 않습니다.</p>
      </section>

      <section>
        <h2>4. 개인정보 처리업무의 위탁</h2>
        <div className="legal-table-wrap">
          <table>
            <thead><tr><th>수탁자</th><th>위탁 업무</th></tr></thead>
            <tbody>
              <tr><td>Oracle Cloud Infrastructure</td><td>애플리케이션 및 데이터베이스 인프라 운영·보관</td></tr>
              <tr><td>Cloudflare, Inc.</td><td>프론트엔드 제공, 네트워크 보호·전송, 봇 방지(Turnstile)</td></tr>
            </tbody>
          </table>
        </div>
        <p>운영자는 위탁계약을 통해 목적 외 처리 금지, 안전조치, 재위탁 관리 및 감독 등 개인정보 보호에 필요한 사항을 관리합니다. 수탁자가 변경되면 이 방침을 통해 공개합니다.</p>
      </section>

      <section>
        <h2>5. 개인정보의 국외 처리·이전</h2>
        <p>다음 서비스는 회원 인증 또는 서비스 제공 과정에서 국외에 있는 시스템을 통해 개인정보를 처리할 수 있습니다. 정확한 처리국가와 이전 근거를 확정하기 전에는 해당 기능을 운영 환경에서 활성화하지 않습니다.</p>
        <div className="legal-table-wrap">
          <table>
            <thead><tr><th>이전받는 자</th><th>항목·목적</th><th>국가·시기·방법</th><th>보유기간</th></tr></thead>
            <tbody>
              <tr><td>Cloudflare, Inc.</td><td>IP 주소, 브라우저·기기 정보, Turnstile 토큰 / 네트워크 제공 및 부정이용 방지</td><td>{LEGAL_POLICY.cloudflareProcessingCountries} / 서비스 이용 시 암호화된 네트워크 전송</td><td>서비스 제공자의 계약 및 개인정보 정책에 따른 기간</td></tr>
              <tr><td>Google LLC</td><td>OAuth 요청정보 및 계정 식별·인증정보 / Google 로그인</td><td>{LEGAL_POLICY.googleProcessingCountries} / 이용자가 Google 로그인을 선택할 때 암호화 전송</td><td>인증 완료 및 제공자의 정책에 따른 기간</td></tr>
              <tr><td>GitHub, Inc.</td><td>OAuth 요청정보 및 계정 식별·인증정보 / GitHub 로그인</td><td>{LEGAL_POLICY.githubProcessingCountries} / 이용자가 GitHub 로그인을 선택할 때 암호화 전송</td><td>인증 완료 및 제공자의 정책에 따른 기간</td></tr>
            </tbody>
          </table>
        </div>
        <p>이용자는 Google 또는 GitHub 로그인을 선택하지 않고 이메일 가입을 이용할 수 있습니다. 다만 Cloudflare 네트워크 처리를 거부하면 서비스 접속 또는 봇 방지 기능 이용이 제한될 수 있습니다.</p>
      </section>

      <section>
        <h2>6. 개인정보의 파기</h2>
        <ol>
          <li>보유기간이 지나거나 처리 목적이 달성되어 개인정보가 불필요해지면 지체 없이 파기합니다.</li>
          <li>전자적 파일은 복구 또는 재생할 수 없도록 안전한 방법으로 삭제하고, 종이 문서가 있는 경우 분쇄하거나 소각합니다.</li>
          <li>다른 법령에 따라 보존해야 하는 정보는 다른 개인정보와 분리하여 해당 기간 동안만 보관합니다.</li>
        </ol>
      </section>

      <section>
        <h2>7. 정보주체의 권리와 행사 방법</h2>
        <p>이용자는 자신의 개인정보에 대해 열람, 정정·삭제, 처리정지, 동의 철회 및 회원 탈퇴를 요구할 수 있습니다. 현재 온라인 탈퇴 기능이 제공되지 않는 경우 {LEGAL_POLICY.privacyOfficerEmail}으로 요청할 수 있으며, 운영자는 본인 확인 후 관계 법령이 정한 기간 안에 처리합니다. 법정대리인이나 위임을 받은 사람도 적법한 위임을 증명하여 권리를 행사할 수 있습니다.</p>
      </section>

      <section>
        <h2>8. 쿠키와 브라우저 저장소</h2>
        <ul>
          <li>OAuth 인증 과정에서 위조 요청 방지를 위한 필수 세션 쿠키가 사용될 수 있습니다.</li>
          <li>로그인 상태 유지를 위해 액세스 토큰을 브라우저 localStorage에 저장하고, 로그인 후 이동할 경로를 sessionStorage에 임시 저장합니다.</li>
          <li>이용자는 브라우저 설정이나 저장소 삭제 기능으로 이를 삭제할 수 있으나, 삭제하거나 차단하면 로그인이 필요한 기능을 이용할 수 없습니다.</li>
        </ul>
      </section>

      <section>
        <h2>9. 안전성 확보조치</h2>
        <p>운영자는 접근권한 최소화, 전송구간 암호화, 비밀번호 단방향 해시, 비밀정보의 별도 관리, 접근통제, 보안 로그 점검 및 취약점 대응 등 개인정보의 분실·도난·유출·변조·훼손을 방지하기 위한 기술적·관리적 조치를 시행합니다.</p>
      </section>

      <section>
        <h2>10. 개인정보 보호책임자 및 문의</h2>
        <dl className="legal-definition-list">
          <div><dt>운영자</dt><dd>{LEGAL_POLICY.operatorName}</dd></div>
          <div><dt>개인정보 보호책임자</dt><dd>{LEGAL_POLICY.privacyOfficerName}</dd></div>
          <div><dt>개인정보 문의</dt><dd>{LEGAL_POLICY.privacyOfficerEmail}</dd></div>
          <div><dt>일반 고객지원</dt><dd>{LEGAL_POLICY.contactEmail}</dd></div>
        </dl>
        <p>개인정보 침해에 관한 상담이 필요한 경우 개인정보침해신고센터(국번 없이 118), 개인정보분쟁조정위원회(1833-6972), 경찰청(국번 없이 182) 등 관계기관에 문의할 수 있습니다.</p>
      </section>

      <section>
        <h2>11. 처리방침 변경</h2>
        <p>이 방침의 내용이 변경되면 시행일 최소 7일 전에 서비스에서 알립니다. 이용자 권리에 중대한 영향을 미치는 변경은 최소 30일 전에 알립니다. 이전 버전은 이용자가 확인할 수 있도록 별도로 제공합니다.</p>
      </section>
    </article>
  </main>
);

export default PrivacyPolicyPage;
