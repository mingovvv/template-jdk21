package com.example.skeleton.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 브라우저 교차 출처 허용 정책.
 * 기본값은 허용 출처 없음(CORS 비활성).
 * 브라우저 클라이언트가 붙을 때 프로파일 yml에서 출처를 명시적으로 엶.
 *
 * @param allowedOrigins 허용 출처(스킴+호스트+포트 정확히 일치).
 *     비어 있으면 CORS 응답 헤더를 내지 않음.
 * @param allowedMethods 허용 HTTP 메서드
 * @param allowedHeaders 요청에 허용할 헤더
 * @param exposedHeaders 브라우저 JS가 읽을 수 있게 노출할 응답 헤더. 기본은 생성 응답의 Location.
 *     요청 ID 헤더({@code skeleton.request-id.header})는 여기 적지 않아도 항상 추가됨.
 *     그래서 헤더 이름을 바꿔도 CORS가 따라감.
 * @param allowCredentials 쿠키 등 자격 증명 동반 허용 여부. bearer 토큰 API라 기본 {@code false}.
 * @param maxAge preflight 결과 캐시 시간
 */
@ConfigurationProperties(prefix = "skeleton.cors")
public record CorsProperties(

        @DefaultValue
        List<String> allowedOrigins,

        @DefaultValue({"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
        List<String> allowedMethods,

        @DefaultValue({"Authorization", "Content-Type", "Accept-Language"})
        List<String> allowedHeaders,

        @DefaultValue("Location")
        List<String> exposedHeaders,

        @DefaultValue("false")
        boolean allowCredentials,

        @DefaultValue("30m")
        Duration maxAge

) {

    public CorsProperties {
        // 자격 증명 + 와일드카드 출처는 브라우저가 거부하는 조합임. 허용되더라도 모든 사이트에 사용자 세션을 여는 셈임.
        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("skeleton.cors.allowed-origins must list explicit origins when allow-credentials is true");
        }
    }

    boolean enabled() {
        return !allowedOrigins.isEmpty();
    }
}
