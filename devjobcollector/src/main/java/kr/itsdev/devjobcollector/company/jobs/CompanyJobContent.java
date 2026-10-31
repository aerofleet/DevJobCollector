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
    private LocalDate originalPostedDate;
    @Column(nullable = false) private boolean completeDescriptionConfirmed;
    @Column(length = 2000) private String applyUrl;

    public record Input(@Size(max=255) String title, @Size(max=255) String jobCategory,
            @Size(max=255) String experience, @Size(max=255) String location,
            @Size(max=255) String hireType, @Size(max=1000) String techStacks,
            @Size(max=20000) String description, LocalDate endDate,
            @Size(max=2000) String applyUrl, LocalDate originalPostedDate, Boolean completeDescriptionConfirmed) {
        public Input(String title, String jobCategory, String experience, String location, String hireType,
                     String techStacks, String description, LocalDate endDate, String applyUrl) {
            this(title, jobCategory, experience, location, hireType, techStacks, description, endDate, applyUrl, null, false);
        }
    }

    public static CompanyJobContent from(Input input) {
        CompanyJobContent c = new CompanyJobContent();
        c.title = clean(input.title()); c.jobCategory = clean(input.jobCategory());
        c.experience = clean(input.experience()); c.location = clean(input.location());
        c.hireType = clean(input.hireType()); c.techStacks = clean(input.techStacks());
        c.description = clean(input.description()); c.endDate = input.endDate();
        c.originalPostedDate = input.originalPostedDate();
        c.completeDescriptionConfirmed = Boolean.TRUE.equals(input.completeDescriptionConfirmed());
        c.applyUrl = clean(input.applyUrl());
        return c;
    }

    public void validateSubmission(LocalDate today) {
        if (originalPostedDate != null && (originalPostedDate.isAfter(today) || (endDate != null && originalPostedDate.isAfter(endDate)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JOB_INVALID_POSTED_DATE");
        }
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
