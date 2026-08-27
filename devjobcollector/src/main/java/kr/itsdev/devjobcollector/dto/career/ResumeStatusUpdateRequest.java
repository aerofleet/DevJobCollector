package kr.itsdev.devjobcollector.dto.career;

import jakarta.validation.constraints.NotNull;
import kr.itsdev.devjobcollector.career.ResumeStatus;

public record ResumeStatusUpdateRequest(@NotNull ResumeStatus status) {
}
