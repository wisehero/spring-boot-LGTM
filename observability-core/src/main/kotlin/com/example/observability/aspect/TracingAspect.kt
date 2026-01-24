package com.example.observability.aspect

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * AOP 기반 자동 추적 Aspect (OpenTelemetry API 사용)
 *
 * Java Agent가 GlobalOpenTelemetry에 SDK를 등록합니다.
 * 이 Aspect는 Agent의 SDK를 사용하여 span을 생성하고,
 * Agent가 Tempo로 전송합니다.
 *
 * com.example 패키지 하위의 모든 public 메서드를 자동으로 추적합니다.
 * (Controller, Service, Repository, Component 등 모든 클래스 포함)
 *
 * 활성화: application.yml에서 observability.tracing.aop.enabled=true 설정
 */
@Aspect
@Component
@ConditionalOnProperty(
    name = ["observability.tracing.aop.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class TracingAspect {

    private val log = LoggerFactory.getLogger(TracingAspect::class.java)

    // Agent가 설정한 GlobalOpenTelemetry에서 Tracer 가져오기
    // lazy 초기화로 Agent 초기화 후에 호출
    private val tracer by lazy {
        log.info("TracingAspect: GlobalOpenTelemetry에서 tracer 초기화 중 (Agent)")
        GlobalOpenTelemetry.getTracer("observability-aop", "1.0.0")
    }

    /**
     * com.example 패키지 하위의 모든 클래스
     */
    @Pointcut("within(com.example..*)")
    fun applicationPackage() {}

    /**
     * public 메서드만 대상
     */
    @Pointcut("execution(public * *(..))")
    fun publicMethod() {}

    /**
     * Spring 프레임워크 내부 클래스 제외 (프록시, 설정 등)
     */
    @Pointcut("!within(com.example.observability..*)")
    fun excludeObservabilityPackage() {}

    /**
     * com.example 패키지의 모든 public 메서드를 span으로 추적
     * (observability 패키지는 제외하여 무한 루프 방지)
     */
    @Around("applicationPackage() && publicMethod() && excludeObservabilityPackage()")
    fun traceMethod(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature
        val className = signature.declaringType.simpleName
        val methodName = signature.name
        val spanName = "$className.$methodName"

        // 현재 컨텍스트의 부모 span에 연결
        val parentContext = Context.current()
        val span = tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setAttribute("code.function", methodName)
            .setAttribute("code.namespace", signature.declaringType.name)
            .startSpan()

        // span을 현재 컨텍스트에 설정
        val scope = span.makeCurrent()

        return try {
            val result = joinPoint.proceed()
            span.setStatus(StatusCode.OK)
            result
        } catch (e: Throwable) {
            span.setStatus(StatusCode.ERROR, e.message ?: "Error")
            span.recordException(e)
            throw e
        } finally {
            scope.close()
            span.end()
        }
    }
}
