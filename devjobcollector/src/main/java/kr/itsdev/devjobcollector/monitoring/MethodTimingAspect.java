package kr.itsdev.devjobcollector.monitoring;

import kr.itsdev.devjobcollector.config.PerfLogProperties;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class MethodTimingAspect {

    private final long serviceThresholdMs;
    private final long repositoryThresholdMs;

    public MethodTimingAspect(PerfLogProperties perfLogProperties) {
        this.serviceThresholdMs = perfLogProperties.serviceThresholdMs();
        this.repositoryThresholdMs = perfLogProperties.repositoryThresholdMs();
    }

    @Around("execution(* kr.itsdev.devjobcollector.service..*(..))")
    public Object profileService(ProceedingJoinPoint joinPoint) throws Throwable {
        return profile(joinPoint, serviceThresholdMs, "service");
    }

    @Around("execution(* kr.itsdev.devjobcollector.repository..*(..))")
    public Object profileRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        return profile(joinPoint, repositoryThresholdMs, "repository");
    }

    private Object profile(ProceedingJoinPoint joinPoint, long thresholdMs, String layer) throws Throwable {
        long startNano = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            double elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

            if (elapsedMs >= thresholdMs) {
                log.warn("Slow {} method {} elapsedMs={}", layer, methodName, String.format("%.2f", elapsedMs));
            } else {
                log.debug("{} method {} elapsedMs={}", layer, methodName, String.format("%.2f", elapsedMs));
            }
        }
    }
}
