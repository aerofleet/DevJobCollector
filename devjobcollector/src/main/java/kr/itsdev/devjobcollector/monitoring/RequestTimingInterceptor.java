package kr.itsdev.devjobcollector.monitoring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.itsdev.devjobcollector.config.PerfLogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class RequestTimingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "perfRequestStartNano";
    private final long requestThresholdMs;

    public RequestTimingInterceptor(PerfLogProperties perfLogProperties) {
        this.requestThresholdMs = perfLogProperties.requestThresholdMs();
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        request.setAttribute(START_TIME_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, @Nullable Exception ex) {
        Object startValue = request.getAttribute(START_TIME_ATTR);
        if (!(startValue instanceof Long startNano)) {
            return;
        }

        double elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;
        String message = "API timing [{} {}] status={} elapsedMs={}";
        Object[] args = new Object[] {
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                String.format("%.2f", elapsedMs)
        };

        if (elapsedMs >= requestThresholdMs) {
            log.warn(message, args);
            return;
        }
        log.info(message, args);
    }
}
