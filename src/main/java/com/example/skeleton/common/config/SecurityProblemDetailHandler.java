package com.example.skeleton.common.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Security 필터 체인의 401/403을 ProblemDetail로 응답함.
 * 필터 체인의 예외는 {@code @RestControllerAdvice}에 닿지 않으므로 MVC의 {@link HandlerExceptionResolver}로 넘겨 {@code GlobalExceptionHandler}가 처리하게 함.
 * 그래서 응답 형태, 에러 코드, 메시지 국제화가 컨트롤러 예외와 같은 곳에서 정해짐.
 *
 * 상태 코드와 {@code WWW-Authenticate} 헤더(RFC 6750)는 Spring의 bearer 기본 구현에 먼저 맡김.
 * 본문만 어드바이스가 씀.
 */
class SecurityProblemDetailHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final HandlerExceptionResolver resolver;

    SecurityProblemDetailHandler(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    // AuthenticationEntryPoint
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, authException);
        resolver.resolveException(request, response, null, authException);
    }

    // AccessDeniedHandler
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        bearerAccessDeniedHandler.handle(request, response, accessDeniedException);
        resolver.resolveException(request, response, null, accessDeniedException);
    }
}
