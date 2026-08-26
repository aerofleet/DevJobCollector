package kr.itsdev.devjobcollector.dto.career;

import kr.itsdev.devjobcollector.career.ApplicationStatus;

public record JobApplicationStatusUpdateRequest(ApplicationStatus status) {
}
