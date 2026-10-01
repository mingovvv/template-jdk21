package com.example.skeleton.common.filter;

import com.example.skeleton.common.context.MdcKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 인증된 주체의 식별자를 MDC({@code userId})에 넣어, 이후의 모든 로그 라인(서비스 로그, res 접근 로그)에 requestId와 함께 붙게 함.
 *
 * 인증 정보는 Security 체인 안에서 토큰 검증이 끝난 뒤에야 존재함.
 * 그래서 이 필터는 서블릿 필터가 아니라 {@code SecurityConfig}가 {@code BearerTokenAuthenticationFilter} 직후에 체인 안으로 넣음.
 * 같은 이유로 {@code @Component}로 등록하지 않음. 빈이 되면 Boot가 서블릿 필터로도 자동 등록해 두 번 실행됨.
 *
 * MDC를 스스로 지우지 않음. 정리는 가장 바깥의 {@link RequestMdcFilter}가 전담함.
 * 그래야 바깥에서 나중에 찍히는 res 접근 로그에도 userId가 남음.
 */
public class AuthenticatedUserMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            MdcKey.USER_ID.put(authentication.getName());
        }

        filterChain.doFilter(request, response);
    }

}
