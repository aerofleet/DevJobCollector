package kr.itsdev.devjobcollector.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import kr.itsdev.devjobcollector.domain.JobPost;
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
                        jobPost.endDate.goe(today),
                        keywordCondition(keywordTerms),
                        containsIgnoreCase(jobPost.location, location),
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
                        jobPost.endDate.goe(today),
                        keywordCondition(keywordTerms),
                        containsIgnoreCase(jobPost.location, location),
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
                .distinct()
                .from(jobPost)
                .join(jobPost.postTags, postTag)
                .join(postTag.techStack, techStack)
                .where(
                        techStack.stackName.in(stackNames),
                        jobPost.isActive.eq(true),
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

    private BooleanExpression techStackCondition(String techStackName) {
        return techStackName == null || techStackName.isBlank()
                ? null
                : techStackEquals(techStackName.trim());
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
            case "신입" -> jobPost.experience.containsIgnoreCase("신입");
            case "경력" -> jobPost.experience.containsIgnoreCase("경력")
                    .and(jobPost.experience.containsIgnoreCase("신입").not())
                    .and(jobPost.experience.containsIgnoreCase("무관").not());
            case "경력무관" -> jobPost.experience.containsIgnoreCase("무관");
            default -> jobPost.experience.containsIgnoreCase(experience.trim());
        };
    }

    private BooleanExpression jobRoleCondition(String role) {
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
