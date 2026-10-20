package kr.itsdev.devjobcollector.controller;

import kr.itsdev.devjobcollector.ai.ResumePreprocessingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumePreprocessingDisabledTest {
    @Test
    void doesNotRegisterEndpointWithoutExplicitEnablement() {
        new WebApplicationContextRunner()
                .withBean(ResumePreprocessingService.class, () -> mock(ResumePreprocessingService.class))
                .withUserConfiguration(ResumePreprocessingController.class)
                .run(context -> assertThat(context).doesNotHaveBean(ResumePreprocessingController.class));
    }
}
