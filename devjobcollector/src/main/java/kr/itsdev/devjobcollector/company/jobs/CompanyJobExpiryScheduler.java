package kr.itsdev.devjobcollector.company.jobs;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class CompanyJobExpiryScheduler {
    private final CompanyJobRepository repository;
    private final CompanyJobService service;
    @Scheduled(fixedDelay = 60000)
    public void expire() {
        repository.expired(LocalDate.now(), PageRequest.of(0, 100)).forEach(service::expire);
    }
}
