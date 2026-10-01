package com.example.skeleton.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * 필터 체인에서 샌 예외를 {@link HandlerExceptionResolver}로 넘겨 {@code GlobalExceptionHandler}가 ProblemDetail을 만들게 함.
 * 예외를 여기서 잡아야 컨테이너까지 가지 않아 {@code /error} 재디스패치가 생기지 않음.
 *
 * 순서는 HIGHEST+3임.
 * 로깅 필터(HIGHEST+2) 바로 안쪽이라 접근 로그의 status가 실제 응답과 같음.
 * Boot 필터({@code FormContentFilter} 등)와 Security보다 바깥이라 그 예외도 잡음.
 * 이 위치는 Boot {@code RequestContextFilter}(-105)보다 바깥이라 요청 로케일이 설정돼 있지 않음.
 * 그래서 detail이 Accept-Language를 따르도록 해석 동안 로케일을 직접 실음.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class ExceptionResolvingFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver resolver;

    public ExceptionResolvingFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            resolve(request, response, e);
        }
    }

    private void resolve(HttpServletRequest request, HttpServletResponse response, Exception e) {
        if (response.isCommitted()) {
            // 헤더가 이미 나갔으면 상태와 본문을 바꿀 수 없음. 클라이언트가 끊은 경우는 서버 문제가 아니므로 DEBUG로만 남김.
            if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
                log.debug("filter.exception.client-disconnected uri={} type={}", request.getRequestURI(), e.getClass().getName());
            } else {
                log.warn("filter.exception.after-commit uri={} type={}", request.getRequestURI(), e.getClass().getName(), e);
            }
            return;
        }
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        LocaleContextHolder.setLocale(request.getLocale());
        try {
            if (resolver.resolveException(request, response, null, e) == null) {
                log.error("filter.exception.unresolved uri={} type={}", request.getRequestURI(), e.getClass().getName(), e);
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }
}
