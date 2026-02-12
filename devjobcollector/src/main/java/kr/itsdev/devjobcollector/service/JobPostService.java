package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.domain.JobPost;
import kr.itsdev.devjobcollector.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostService {

    private final JobPostRepository jobPostRepository;

    public Page<JobPost> getJobPosts(Pageable pagealbe) {
        return jobPostRepository.findAllByIsActiveTrueOrderByCreatedAtDesc(pagealbe);        
    }

    public JobPost getJobPostDetail(@NonNull Long id) {
        return jobPostRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공고입니다. ID: " + id));
    }
}
