package kr.itsdev.devjobcollector.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.domain.JobModerationStatus;
import kr.itsdev.devjobcollector.domain.QPostTag;
import kr.itsdev.devjobcollector.domain.QTechStack;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import com.querydsl.jpa.JPAExpressions;

import static kr.itsdev.devjobcollector.domain.QJobPost.jobPost;
import static kr.itsdev.devjobcollector.domain.QPostTag.postTag;
import static kr.itsdev.devjobcollector.domain.QTechStack.techStack;

@Repository
@RequiredArgsConstructor
public class JobPostRepositoryCustomImpl implements JobPostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @SuppressWarnings("null") // Stream/JPA nullness inference noise
    @Override
    public Page<JobPost> searchByAllFieldsOptimized(
            String keyword,
            String location,
            String experience,
            String jobCategory,
            String techStackName,
            LocalDate today,
            Pageable pageable
    ) {
        List<List<String>> keywordTerms = JobSearchKeyword.termGroups(keyword);
        JPAQuery<JobPost> contentQuery = queryFactory
                .selectFrom(jobPost)
                .distinct()
                .leftJoin(jobPost.postTags, postTag).fetchJoin()
                .leftJoin(postTag.techStack, techStack).fetchJoin()
                .where(
                        jobPost.isActive.eq(true),
                        jobPost.moderationStatus.eq(JobModerationStatus.ACTIVE),
                        jobPost.endDate.goe(today),
                        keywordCondition(keywordTerms),
                        locationCondition(location),
                        experienceCondition(experience),
                        jobRoleCondition(jobCategory),
                        techStackCondition(techStackName)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize());

        boolean deadlineSort = pageable.getSort().stream()
                .findFirst()
                .map(order -> order.getProperty().equals("endDate"))
                .orElse(false);
        boolean ascending = pageable.getSort().stream()
                .findFirst()
                .map(order -> order.getDirection().isAscending())
                .orElse(false);

        if (!keywordTerms.isEmpty()) {
            contentQuery.orderBy(relevanceScore(keywordTerms).desc());
        }

        if (deadlineSort) {
            contentQuery.orderBy(
                    ascending ? jobPost.endDate.asc() : jobPost.endDate.desc(),
                    jobPost.createdAt.desc()
            );
        } else {
            contentQuery.orderBy(ascending ? jobPost.createdAt.asc() : jobPost.createdAt.desc());
        }

        List<JobPost> content = contentQuery.fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(jobPost.countDistinct())
                .from(jobPost)
                .where(
                        jobPost.isActive.eq(true),
                        jobPost.moderationStatus.eq(JobModerationStatus.ACTIVE),
                        jobPost.endDate.goe(today),
                        keywordCondition(keywordTerms),
                        locationCondition(location),
                        experienceCondition(experience),
                        jobRoleCondition(jobCategory),
                        techStackCondition(techStackName)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @SuppressWarnings("null") // Stream/JPA nullness inference noise
    @Override
    public Page<JobPost> findByTechStackNamesOptimized(List<String> stackNames, LocalDate today, Pageable pageable) {
        List<Long> jobPostIds = queryFactory
                .select(jobPost.id)
                .from(jobPost)
                .where(
                        JPAExpressions.selectOne().from(postTag)
                                .join(postTag.techStack, techStack)
                                .where(postTag.jobPost.eq(jobPost), techStack.stackName.in(stackNames)).exists(),
                        jobPost.isActive.eq(true),
                        jobPost.moderationStatus.eq(JobModerationStatus.ACTIVE),
                        jobPost.endDate.goe(today)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(jobPost.createdAt.desc())
                .fetch();

        List<JobPost> content = jobPostIds.isEmpty() ?
                List.of() :
                queryFactory
                        .selectFrom(jobPost)
                        .leftJoin(jobPost.postTags, postTag).fetchJoin()
                        .leftJoin(postTag.techStack, techStack).fetchJoin()
                        .where(jobPost.id.in(jobPostIds))
                        .orderBy(jobPost.createdAt.desc())
                        .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(jobPost.countDistinct())
                .from(jobPost)
                .join(jobPost.postTags, postTag)
                .join(postTag.techStack, techStack)
                .where(
                        techStack.stackName.in(stackNames),
                        jobPost.isActive.eq(true),
                        jobPost.moderationStatus.eq(JobModerationStatus.ACTIVE),
                        jobPost.endDate.goe(today)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression keywordCondition(List<List<String>> termGroups) {
        if (termGroups.isEmpty()) {
            return null;
        }

        BooleanExpression condition = null;
        for (List<String> alternatives : termGroups) {
            BooleanExpression tokenCondition = anySearchFieldContains(alternatives);
            condition = condition == null ? tokenCondition : condition.and(tokenCondition);
        }
        return condition;
    }

    private BooleanExpression anySearchFieldContains(List<String> alternatives) {
        return containsAny(jobPost.title, alternatives)
                .or(containsAny(jobPost.companyName, alternatives))
                .or(containsAny(jobPost.location, alternatives))
                .or(containsAny(jobPost.experience, alternatives))
                .or(containsAny(jobPost.jobCategory, alternatives))
                .or(containsAny(jobPost.hireType, alternatives))
                .or(containsAny(jobPost.applyQual, alternatives))
                .or(containsAny(jobPost.processInfo, alternatives))
                .or(techStackContainsAny(alternatives));
    }

    private NumberExpression<Integer> relevanceScore(List<List<String>> termGroups) {
        NumberExpression<Integer> score = Expressions.asNumber(0);
        for (List<String> alternatives : termGroups) {
            NumberExpression<Integer> tokenScore = new CaseBuilder()
                    .when(containsAny(jobPost.title, alternatives)).then(100)
                    .when(techStackContainsAny(alternatives)).then(80)
                    .when(containsAny(jobPost.jobCategory, alternatives)).then(60)
                    .when(containsAny(jobPost.companyName, alternatives)).then(40)
                    .when(containsAny(jobPost.applyQual, alternatives)
                            .or(containsAny(jobPost.processInfo, alternatives))).then(20)
                    .otherwise(10);
            score = score.add(tokenScore);
        }
        return score;
    }

    private BooleanExpression containsIgnoreCase(
            com.querydsl.core.types.dsl.StringPath path,
            String value
    ) {
        return value == null || value.isBlank() ? null : path.containsIgnoreCase(value.trim());
    }

    private BooleanExpression locationCondition(String location) {
        return anySelected(location, this::singleLocationCondition);
    }

    private BooleanExpression singleLocationCondition(String location) {
        String pattern = JobLocationFilter.pattern(location);
        return pattern == null ? containsIgnoreCase(jobPost.location, location)
                : Expressions.booleanTemplate("function('regexp_like', {0}, {1}) = true", jobPost.location.lower(), pattern);
    }

    private BooleanExpression techStackCondition(String techStackName) {
        return anySelected(techStackName, this::techStackEquals);
    }

    // Comma-separated selections preserve existing single-value search URLs.
    // OR within a filter; the surrounding where clauses AND the filter groups.
    private BooleanExpression anySelected(String values, java.util.function.Function<String, BooleanExpression> predicate) {
        if (values == null || values.isBlank()) return null;
        return java.util.Arrays.stream(values.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).distinct()
                .map(predicate).reduce(BooleanExpression::or).orElse(null);
    }

    private BooleanExpression techStackContainsAny(List<String> values) {
        QPostTag searchPostTag = new QPostTag("searchPostTagContains");
        QTechStack searchTechStack = new QTechStack("searchTechStackContains");
        return JPAExpressions.selectOne()
                .from(searchPostTag)
                .join(searchPostTag.techStack, searchTechStack)
                .where(
                        searchPostTag.jobPost.eq(jobPost),
                        containsAny(searchTechStack.stackName, values))
                .exists();
    }

    private BooleanExpression containsAny(StringPath path, List<String> values) {
        BooleanExpression condition = null;
        for (String value : values) {
            BooleanExpression next = path.containsIgnoreCase(value);
            condition = condition == null ? next : condition.or(next);
        }
        return condition;
    }

    private BooleanExpression techStackEquals(String value) {
        QPostTag searchPostTag = new QPostTag("searchPostTagEquals");
        QTechStack searchTechStack = new QTechStack("searchTechStackEquals");
        return JPAExpressions.selectOne()
                .from(searchPostTag)
                .join(searchPostTag.techStack, searchTechStack)
                .where(
                        searchPostTag.jobPost.eq(jobPost),
                        searchTechStack.stackName.equalsIgnoreCase(value))
                .exists();
    }

    private BooleanExpression experienceCondition(String experience) {
        if (experience == null || experience.isBlank()) {
            return null;
        }

        return switch (experience) {
            case "신입" -> jobPost.experienceCategory.in("ENTRY", "BOTH");
            case "경력" -> jobPost.experienceCategory.in("EXPERIENCED", "BOTH");
            case "경력무관" -> jobPost.experienceCategory.eq("ANY");
            default -> jobPost.experienceLabel.containsIgnoreCase(experience.trim());
        };
    }

    private BooleanExpression jobRoleCondition(String role) {
        return anySelected(role, this::singleJobRoleCondition);
    }

    private BooleanExpression singleJobRoleCondition(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }

        return switch (role.toLowerCase()) {
            case "backend" -> containsAny("백엔드", "backend", "back-end", "server");
            case "frontend" -> containsAny("프론트엔드", "frontend", "front-end", "web frontend");
            case "fullstack" -> containsAny("풀스택", "fullstack", "full-stack", "full stack");
            case "mobile" -> containsAny("모바일", "android", "ios", "flutter", "react native");
            case "data-ai" -> containsAny("데이터", "data engineer", "machine learning", "머신러닝", "ai engineer", "인공지능");
            case "devops-security" -> containsAny("devops", "sre", "platform engineer", "security engineer", "보안", "인프라");
            case "android" -> containsAny("android", "안드로이드");
            case "ios" -> containsAny("ios", "아이폰");
            case "cross-platform" -> containsAny("flutter", "react native", "크로스플랫폼", "크로스 플랫폼");
            case "web-publishing" -> containsAny("퍼블리셔", "퍼블리싱", "web publisher");
            case "data-engineer" -> containsAny("데이터 엔지니어", "데이터엔지니어", "data engineer");
            case "data-analyst" -> containsAny("데이터 분석", "데이터분석", "data analyst", "비즈니스 인텔리전스");
            case "data-scientist" -> containsAny("데이터 사이언", "데이터사이언", "data scientist");
            case "ml-engineer" -> containsAny("머신러닝", "machine learning", "딥러닝", "deep learning");
            case "ai-engineer" -> containsAny("인공지능", "ai engineer", "ai 엔지니어", "ai 개발", "ai 연구");
            case "mlops" -> containsAny("mlops", "ml ops");
            case "computer-vision" -> containsAny("컴퓨터 비전", "컴퓨터비전", "computer vision", "영상인식", "영상 인식");
            case "nlp" -> containsAny("자연어", "nlp", "llm", "생성형");
            case "devops" -> containsAny("devops", "데브옵스");
            case "sre" -> containsAny("sre", "site reliability");
            case "cloud" -> containsAny("클라우드", "cloud engineer", "cloud architect");
            case "infrastructure" -> containsAny("인프라", "시스템 엔지니어", "시스템엔지니어", "system engineer", "시스템 운영");
            case "network" -> containsAny("네트워크", "network engineer");
            case "security" -> containsAny("보안", "security", "모의해킹");
            case "dba" -> containsAny("dba", "데이터베이스 관리자", "database administrator");
            case "platform" -> containsAny("플랫폼 엔지니어", "플랫폼엔지니어", "platform engineer");
            case "game" -> containsAny("게임", "game developer", "game programmer", "unity", "unreal");
            case "embedded" -> containsAny("임베디드", "펌웨어", "embedded", "firmware");
            case "robotics" -> containsAny("로봇", "로보틱스", "robotics");
            case "iot" -> containsAny("iot", "사물인터넷");
            case "blockchain" -> containsAny("블록체인", "blockchain", "스마트 컨트랙트", "smart contract");
            case "desktop" -> containsAny("데스크톱", "데스크탑", "desktop", "windows 개발", "wpf");
            case "qa" -> containsAny("qa", "테스트 엔지니어", "테스트엔지니어", "test engineer", "quality assurance", "sdet");
            case "architect" -> containsAny("아키텍트", "architect");
            case "engineering-manager" -> containsAny("개발 리드", "개발팀장", "개발 팀장", "테크 리드", "tech lead", "engineering manager");
            case "product-manager" -> containsAny("프로덕트 매니저", "프로덕트매니저", "product manager", "product owner", "서비스 기획", "서비스기획");
            case "product-designer" -> containsAny("ux", "ui 디자", "ui디자", "product designer", "프로덕트 디자");
            case "technical-support" -> containsAny("기술지원", "기술 지원", "technical support", "솔루션 엔지니어", "solution engineer");
            default -> jobPost.title.containsIgnoreCase(role)
                    .or(jobPost.jobCategory.containsIgnoreCase(role));
        };
    }

    private BooleanExpression containsAny(String... values) {
        BooleanExpression condition = null;
        for (String value : values) {
            BooleanExpression next = jobPost.title.containsIgnoreCase(value)
                    .or(jobPost.jobCategory.containsIgnoreCase(value));
            condition = condition == null ? next : condition.or(next);
        }
        return condition;
    }
}
