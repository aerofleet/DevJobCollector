package kr.itsdev.devjobcollector.collection.discovery;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Conservative keyword classifier used only to prioritize SME seed review and scanning. */
@Component
public class SmeJobPostingRelevance {
    private static final Pattern IT_ROLE = Pattern.compile(
            "개발|소프트웨어|프로그래머|엔지니어|데이터\\s*(분석|엔지니어|사이언티스트)|"
                    + "정보\\s*보안|인공지능|머신러닝|딥러닝|프론트엔드|백엔드|풀스택|앱\\s*개발|웹\\s*개발|"
                    + "클라우드|데브옵스|DevOps|Java|Python|Kotlin|프로그래밍|IT\\s*(직군|개발)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public boolean isRelevant(String jobTitle) {
        return jobTitle != null && IT_ROLE.matcher(jobTitle).find();
    }
}
