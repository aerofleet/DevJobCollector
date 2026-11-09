package kr.itsdev.devjobcollector.service;

import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/** Identifies explicitly targeted industrial personnel recruitment, not incidental mentions. */
public final class JobRecruitmentType {
    private static final Pattern CAMPAIGN = Pattern.compile("산업\\s*기능\\s*요원.{0,30}(?:집중\\s*)?(?:채용|모집|선발|전형)");
    private static final Pattern ELIGIBILITY = Pattern.compile("(?:지원\\s*가능\\s*(?:유형|대상)|모집\\s*대상)[^\\n]{0,120}(?:보충역|현역)[^\\n]{0,100}(?:편입|전직)");

    private JobRecruitmentType() {}

    public static boolean industrialPersonnel(String title, String description) {
        String text = HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape((title == null ? "" : title) + "\n" + (description == null ? "" : description)))
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6])>", "\n")
                .replaceAll("<[^>]+>", " ").replace('\u00a0', ' ');
        return CAMPAIGN.matcher(title == null ? "" : title).find()
                || (text.contains("산업기능요원") && ELIGIBILITY.matcher(text).find());
    }
}
