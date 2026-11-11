package kr.itsdev.devjobcollector.company.jobs;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Embeddable
@Getter
@NoArgsConstructor
public class CompanyJobContent {
    @Column(length = 255) private String title;
    @Column(length = 255) private String jobCategory;
    @Column(length = 255) private String experience;
    @Column(length = 255) private String location;
    @Column(length = 255) private String hireType;
    @Column(length = 1000) private String techStacks;
    @Column(columnDefinition = "LONGTEXT") private String description;
    private LocalDate endDate;
    @Column(length = 2000) private String applyUrl;
    @Column(length = 40) private String workArrangement;
    @Column(length = 40) private String koreanLevel;
    @Column(length = 40) private String englishLevel;
    @Column(length = 2000) private String recruiterNote;
    @Column(length = 40) private String referralSource;

    public record Input(@Size(max=255) String title, @Size(max=255) String jobCategory,
            @Size(max=255) String experience, @Size(max=255) String location,
            @Size(max=255) String hireType, @Size(max=1000) String techStacks,
            @Size(max=20000) String description, LocalDate endDate,
            @Size(max=2000) String applyUrl,
            @Size(max=40) String workArrangement, @Size(max=40) String koreanLevel,
            @Size(max=40) String englishLevel, @Size(max=2000) String recruiterNote,
            @Size(max=40) String referralSource) {
        public Input(String title, String jobCategory, String experience, String location, String hireType,
                     String techStacks, String description, LocalDate endDate, String applyUrl) {
            this(title, jobCategory, experience, location, hireType, techStacks, description, endDate, applyUrl, null, null, null, null, null);
        }
    }

    public static CompanyJobContent from(Input input) {
        CompanyJobContent c = new CompanyJobContent();
        c.title = clean(input.title()); c.jobCategory = clean(input.jobCategory());
        c.experience = clean(input.experience()); c.location = clean(input.location());
        c.hireType = clean(input.hireType()); c.techStacks = clean(input.techStacks());
        c.description = clean(input.description()); c.endDate = input.endDate();
        c.applyUrl = clean(input.applyUrl());
        c.workArrangement = clean(input.workArrangement()); c.koreanLevel = clean(input.koreanLevel());
        c.englishLevel = clean(input.englishLevel()); c.recruiterNote = clean(input.recruiterNote());
        c.referralSource = clean(input.referralSource());
        return c;
    }

    public static CompanyJobContent fromEdit(Input input, CompanyJobContent previous) {
        CompanyJobContent result = from(input);
        if (input.workArrangement() == null) result.workArrangement = previous.workArrangement;
        if (input.koreanLevel() == null) result.koreanLevel = previous.koreanLevel;
        if (input.englishLevel() == null) result.englishLevel = previous.englishLevel;
        if (input.recruiterNote() == null) result.recruiterNote = previous.recruiterNote;
        if (input.referralSource() == null) result.referralSource = previous.referralSource;
        return result;
    }

    public String publicDescription() {
        StringBuilder result = new StringBuilder(description);
        if (hasText(workArrangement) || hasText(koreanLevel) || hasText(englishLevel)) result.append("\n\n[추가 근무 조건]");
        appendPublicField(result, "근무 방식", workArrangement);
        appendPublicField(result, "한국어 수준", koreanLevel);
        appendPublicField(result, "영어 수준", englishLevel);
        return result.toString();
    }

    private static void appendPublicField(StringBuilder result, String label, String value) {
        if (hasText(value)) result.append("\n").append(label).append(": ").append(value.trim());
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }

    public void validateSubmission(LocalDate today) {
        if (title.isBlank() || jobCategory.isBlank() || experience.isBlank()
                || location.isBlank() || hireType.isBlank() || description.isBlank()
                || techStacks.isBlank() || endDate == null || endDate.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JOB_REQUIRED_FIELDS");
        }
        if (techStacks.split(",").length > 20 || java.util.Arrays.stream(techStacks.split(",", -1))
                .anyMatch(s -> s.isBlank() || s.trim().length() > 50)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JOB_INVALID_TECH_STACKS");
        }
        try {
            URI url = URI.create(applyUrl);
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
                    || !url.getHost().contains(".") || url.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JOB_HTTPS_APPLY_URL_REQUIRED");
        }
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
