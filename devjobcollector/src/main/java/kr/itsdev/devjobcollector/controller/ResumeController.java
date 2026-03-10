package kr.itsdev.devjobcollector.controller;

import kr.itsdev.devjobcollector.dto.ResumeDTO;
import kr.itsdev.devjobcollector.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/resume")
public class ResumeController {

    private final ResumeService resumeService;

    @GetMapping("/{userId}")
    public ResponseEntity<ResumeDTO> getResume(@PathVariable Long userId) {
        return ResponseEntity.ok(resumeService.getResume(userId));
    }

    @PostMapping
    public ResponseEntity<ResumeDTO> saveResume(@RequestBody ResumeDTO resumeDTO) {
        return ResponseEntity.ok(resumeService.saveResume(resumeDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResumeDTO> updateResume(@PathVariable Long id, @RequestBody ResumeDTO resumeDTO) {
        return ResponseEntity.ok(resumeService.updateResume(id, resumeDTO));
    }
}
