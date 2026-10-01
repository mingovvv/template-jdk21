package com.example.skeleton.common.context;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 요청 ID 정책 설정.
 *
 * @param header 요청 ID를 주고받는 HTTP 헤더 이름.
 *     응답 헤더로 반환하고, {@code trustIncoming}이면 같은 이름의 수신 헤더를 승계함.
 *     조직 표준이 다르면(예: {@code X-Correlation-Id}) 바꿈.
 *     CORS 노출 헤더에도 자동으로 포함됨.
 * @param trustIncoming 상류가 보낸 요청 ID 헤더를 승계할지 여부.
 *     기본 {@code false}(항상 내부 생성)이며 이 서버가 신뢰 경계라는 전제임.
 *     신뢰할 수 있는 게이트웨이/상류 서비스 뒤에 배포할 때만 켬.
 */
@ConfigurationProperties(prefix = "skeleton.request-id")
public record RequestIdProperties(

        @DefaultValue("X-Request-Id")
        String header,

        @DefaultValue("false")
        boolean trustIncoming

) {

    /** RFC 9110 token 규칙. HTTP 헤더 이름으로 쓸 수 있는 문자임. 공백·콜론 등은 헤더를 깨뜨림. */
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    public RequestIdProperties {
        if (header == null || !HEADER_NAME.matcher(header).matches()) {
            throw new IllegalArgumentException("skeleton.request-id.header must be a valid HTTP header name: %s".formatted(header));
        }
    }
}
