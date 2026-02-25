package kr.itsdev.devjobcollector.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import kr.itsdev.devjobcollector.domain.JobPost;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static kr.itsdev.devjobcollector.domain.QJobPost.jobPost;
import static kr.itsdev.devjobcollector.domain.QPostTag.postTag;
import static kr.itsdev.devjobcollector.domain.QTechStack.techStack;

@Repository
@RequiredArgsConstructor
public class JobPostRepositoryCustomImpl implements JobPostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @SuppressWarnings("null") // Stream/JPA nullness inference noise
    @Override
    public Page<JobPost> searchByAllFieldsOptimized(String keyword, LocalDate today, Pageable pageable) {
        List<JobPost> content = queryFactory
                .selectFrom(jobPost)
                .distinct()
                .leftJoin(jobPost.postTags, postTag).fetchJoin()
                .leftJoin(postTag.techStack, techStack).fetchJoin()
                .where(
                        jobPost.isActive.eq(true),
                        jobPost.endDate.goe(today),
                        keywordCondition(keyword)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(jobPost.createdAt.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(jobPost.countDistinct())
                .from(jobPost)
                .leftJoin(jobPost.postTags, postTag)
                .leftJoin(postTag.techStack, techStack)
                .where(
                        jobPost.isActive.eq(true),
                        jobPost.endDate.goe(today),
                        keywordConditionForCount(keyword)
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

    private BooleanExpression keywordCondition(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        String likeKeyword = "%" + keyword.toLowerCase() + "%";
        return jobPost.title.lower().like(likeKeyword)
                .or(jobPost.companyName.lower().like(likeKeyword))
                .or(jobPost.location.lower().like(likeKeyword))
                .or(jobPost.experience.lower().like(likeKeyword))
                .or(jobPost.jobCategory.lower().like(likeKeyword))
                .or(techStack.stackName.lower().like(likeKeyword));
    }

    private BooleanExpression keywordConditionForCount(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        String likeKeyword = "%" + keyword.toLowerCase() + "%";
        return jobPost.title.lower().like(likeKeyword)
                .or(jobPost.companyName.lower().like(likeKeyword))
                .or(jobPost.location.lower().like(likeKeyword))
                .or(jobPost.experience.lower().like(likeKeyword))
                .or(jobPost.jobCategory.lower().like(likeKeyword))
                .or(techStack.stackName.lower().like(likeKeyword));
    }
}
