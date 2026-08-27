package kr.itsdev.devjobcollector.dto.career;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResumeUpsertRequest(
        @NotBlank @Size(max = 150) String title,
        @NotNull JsonNode content
) {
}
