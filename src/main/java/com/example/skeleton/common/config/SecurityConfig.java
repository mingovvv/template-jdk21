package com.example.skeleton.common.config;

import com.example.skeleton.common.context.RequestIdProperties;
import com.example.skeleton.common.filter.AuthenticatedUserMdcFilter;
import jakarta.servlet.DispatcherType;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 무상태 리소스 서버 보안 설정.
 * 공개 허용 목록 밖의 모든 요청은 설정된 HS256 시크릿으로 서명된 bearer JWT를 지참해야 함.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    // /api/ping은 무인증 스모크 테스트용 샘플 엔드포인트임 (읽기 전용, 민감 정보 없음).
    private static final String[] PUBLIC_PATHS = {
        "/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/api/ping"
    };

    /**
     * handlerExceptionResolver는 MVC의 예외 해석기임.
     * 같은 타입의 빈이 둘({@code errorAttributes} 포함)이라 이름으로 지정함.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            CorsConfigurationSource corsConfigurationSource,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {

        SecurityProblemDetailHandler problemDetailHandler = new SecurityProblemDetailHandler(handlerExceptionResolver);

        http.csrf(CsrfConfigurer::disable)
            // preflight(OPTIONS)는 Authorization 헤더가 없으므로 인증보다 먼저 CORS가 처리해야 함.
            // 설정은 명시적으로 연결함. withDefaults()는 빈 이름("corsConfigurationSource")으로 찾으므로 이름이 바뀌면 조용히 CORS가 꺼짐.
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Security는 ERROR 디스패치도 인가함. 막으면 익명 요청의 실제 에러(400/500)가 401로 둔갑함.
            // 실패는 출처에서 어드바이스로 위임되어 ERROR 디스패치가 거의 생기지 않음. 그래도 생기면 Boot /error가 응답하도록 열어 둠.
            .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers(PUBLIC_PATHS)
                .permitAll()
                .anyRequest()
                .authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                // 토큰이 있지만 잘못됐거나 만료된 경우(BearerTokenAuthenticationFilter)는 401 + ProblemDetail로 응답함.
                // code는 AUTH_TOKEN_INVALID 또는 AUTH_TOKEN_EXPIRED이고 헤더는 WWW-Authenticate: Bearer error="invalid_token"임.
                .authenticationEntryPoint(problemDetailHandler)
                // 인증됐지만 권한이 부족한 경우는 403 + ProblemDetail code=ACCESS_DENIED, WWW-Authenticate: Bearer error="insufficient_scope"로 응답함.
                // bearer 요청의 기본값으로 등록됨. 아래 exceptionHandling에 명시 설정이 있으면 그쪽이 우선함.
                .accessDeniedHandler(problemDetailHandler))
            .exceptionHandling(exceptions -> exceptions
                // 토큰 없이 보호 경로에 접근한 경우(ExceptionTranslationFilter)는 401 + ProblemDetail code=AUTH_REQUIRED로 응답함.
                .authenticationEntryPoint(problemDetailHandler)
                // 인증됐지만 URL 인가 규칙에 막힌 경우(ExceptionTranslationFilter)는 403 + ProblemDetail code=ACCESS_DENIED로 응답함.
                .accessDeniedHandler(problemDetailHandler))
            .addFilterAfter(new AuthenticatedUserMdcFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 허용 출처가 없으면 등록하지 않음. CorsFilter가 설정을 못 찾으면 CORS 헤더 없이 통과시킴.
     * 요청 ID 헤더는 클라이언트가 문의할 때 제시할 수 있도록 설정된 이름으로 항상 노출함.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties, RequestIdProperties requestId) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.enabled()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(properties.allowedOrigins());
            config.setAllowedMethods(properties.allowedMethods());
            config.setAllowedHeaders(properties.allowedHeaders());
            config.setExposedHeaders(properties.exposedHeaders());
            config.addExposedHeader(requestId.header());
            config.setAllowCredentials(properties.allowCredentials());
            config.setMaxAge(properties.maxAge());
            source.registerCorsConfiguration("/**", config);
        }
        return source;
    }

    /**
     * 방화벽({@code StrictHttpFirewall})이 거부한 요청의 처리기.
     * 기본 처리기는 {@code sendError(400)}으로 ERROR 디스패치를 만듦.
     * 그래서 401/403과 같은 방식으로 어드바이스에 위임해 그 자리에서 ProblemDetail로 응답함.
     */
    @Bean
    RequestRejectedHandler requestRejectedHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        return (request, response, exception)
            -> handlerExceptionResolver.resolveException(request, response, null, exception);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
