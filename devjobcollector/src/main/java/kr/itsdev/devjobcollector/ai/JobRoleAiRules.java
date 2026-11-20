package kr.itsdev.devjobcollector.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict syntactic and source-evidence validation; semantic correctness requires evaluation. */
public final class JobRoleAiRules {
    public static final String VERSION = "job-role-v1";
    public static final Set<String> EXISTING_ROLES = Set.of(
            "백엔드", "프론트엔드", "풀스택", "모바일", "데이터 엔지니어", "데이터 분석가", "데이터 사이언티스트",
            "AI·머신러닝", "DevOps", "SRE", "정보보안", "QA·테스트", "프로덕트 매니저", "UX·UI 디자인",
            "게임 개발", "임베디드·펌웨어", "소프트웨어 개발", "재무·자금", "재무·금융", "법률 자문",
            "응용과학 연구원", "산업안전·위기관리", "영업 개발 담당자", "IT 운영 엔지니어", "인사 운영",
            "리테일 가격 운영", "계정 관리 매니저", "채용 운영", "기술 지원·품질", "IT 지원", "IT 인프라 운영", "영업·계정 관리");
    private static final Set<String> FORBIDDEN = Set.of("기타", "미분류", "직무 확인 필요", "확인 필요", "알 수 없음");
    private static final Pattern ROLE_TEXT = Pattern.compile("[가-힣A-Za-z0-9][가-힣A-Za-z0-9 ·&/+-]{0,30}[가-힣A-Za-z0-9]");

    private JobRoleAiRules() {}

    public static Result validate(JsonNode output, String source) {
        if (output == null || !output.isObject() || output.size() != 3 || source == null
                || !output.path("mode").isTextual() || !output.path("role").isTextual() || !output.path("evidence").isTextual())
            throw new IllegalArgumentException("INVALID_ROLE_OUTPUT");
        String mode = output.path("mode").asText();
        String role = output.path("role").asText();
        String evidence = output.path("evidence").asText();
        if ("ABSTAIN".equals(mode)) {
            if (!role.isEmpty() || !evidence.isEmpty()) throw new IllegalArgumentException("INVALID_ROLE_ABSTENTION");
            return new Result(mode, role, evidence);
        }
        if (!Set.of("EXISTING", "NEW").contains(mode) || !role.equals(role.trim()) || !ROLE_TEXT.matcher(role).matches()
                || FORBIDDEN.contains(role) || evidence.isBlank() || evidence.length() > 300 || !source.contains(evidence))
            throw new IllegalArgumentException("INVALID_ROLE_EVIDENCE");
        if (("EXISTING".equals(mode) && !EXISTING_ROLES.contains(role))
                || ("NEW".equals(mode) && EXISTING_ROLES.contains(role))) throw new IllegalArgumentException("INVALID_ROLE_CATALOG");
        return new Result(mode, role, evidence);
    }

    public record Result(String mode, String role, String evidence) {}
}
