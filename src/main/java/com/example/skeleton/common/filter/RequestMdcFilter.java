package com.example.skeleton.common.filter;

import com.example.skeleton.common.context.MdcKey;
import com.example.skeleton.common.context.RequestIdProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 단위 MDC의 수립과 정리 담당. MDC 생명주기의 단일 소유자임.
 * 요청 시작 시 requestId와 요청 메타데이터(method, uri, clientIp)를 넣음.
 * 요청이 끝나면 안쪽 필터({@link AuthenticatedUserMdcFilter} 등)가 넣은 키까지 모든 MDC 키를 지움.
 * 그래서 이 요청의 모든 로그 라인(Spring Security 로그, res 접근 로그 포함)에 같은 값이 붙음.
 *
 * 비동기 요청(Callable, DeferredResult, SseEmitter 등)은 결과를 쓰는 ASYNC 디스패치가 다른 스레드에서 실행됨.
 * 첫 디스패치가 끝날 때 MDC를 요청 객체에 보관하고, ASYNC 디스패치에서 그대로 복원함.
 * Spring의 {@code ServerHttpObservationFilter}가 관측 객체를 다루는 방식과 같음.
 *
 * 요청 ID는 클라이언트가 문의할 때 제시할 수 있도록 응답 헤더({@code skeleton.request-id.header}, 기본 {@code X-Request-Id})로 반환함.
 * 기본 동작은 항상 내부 생성임. 이 서버가 신뢰 경계이므로 클라이언트가 보낸 ID는 무시함.
 * 신뢰할 수 있는 게이트웨이/상류 서비스 뒤에 배포할 때는 {@code skeleton.request-id.trust-incoming=true}로 수신 헤더 승계를 켤 수 있음.
 * 이때도 형식 검증(영숫자/하이픈/언더스코어 64자 이내)을 통과한 값만 승계함.
 * 내부 생성 형식은 랜덤 64비트의 base36 고정 13자임.
 * 로그 보관 기간 안에서 충분히 유일하고, 로그 라인이 짧아지며, 순수 영숫자라 로그 뷰어에서 더블클릭으로 전체 선택됨.
 *
 * 순서는 {@code HIGHEST_PRECEDENCE}가 아니라 +1임.
 * Boot의 {@code ForwardedHeaderFilter}({@code server.forward-headers-strategy=framework})가 {@code HIGHEST_PRECEDENCE}인데, 같은 순위면 실행 순서가 보장되지 않음.
 * 이 필터가 먼저 돌면 clientIp에 프록시 IP가 들어감.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@EnableConfigurationProperties(RequestIdProperties.class)
public class RequestMdcFilter extends OncePerRequestFilter {

    private static final String MDC_SNAPSHOT_ATTRIBUTE = RequestMdcFilter.class.getName() + ".MDC_SNAPSHOT";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ID_LENGTH = 13;
    private static final Pattern VALID_INCOMING_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final RequestIdProperties properties;

    public RequestMdcFilter(RequestIdProperties properties) {
        this.properties = properties;
    }

    /** 비동기 요청은 결과를 쓰는 ASYNC 디스패치가 다른 스레드에서 돌기 때문에, 거기서도 MDC를 복원해야 함. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        if (request.getAttribute(MDC_SNAPSHOT_ATTRIBUTE) instanceof Map<?, ?> snapshot) {
            // ASYNC 디스패치. 새로 만들지 않고 첫 디스패치의 값(안쪽 필터가 넣은 userId 포함)을 그대로 이어 씀.
            MdcKey.restore((Map<MdcKey, String>) snapshot);
        } else {
            establish(request, response);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (request.isAsyncStarted()) {
                // 결과는 나중에 다른 스레드에서 쓰이므로 지금의 MDC를 요청 객체에 보관함. 요청 객체만 모든 디스패치에서 같음.
                request.setAttribute(MDC_SNAPSHOT_ATTRIBUTE, MdcKey.snapshot());
            }
            // 가장 바깥 필터가 MDC 정리를 전담함. 스레드는 풀에서 재사용되므로 디스패치마다 지움.
            MdcKey.removeAll();
        }
    }

    private void establish(HttpServletRequest request, HttpServletResponse response) {
        String requestId = resolveRequestId(request);
        MdcKey.REQUEST_ID.put(requestId);
        MdcKey.METHOD.put(request.getMethod());
        MdcKey.URI.put(request.getRequestURI());
        MdcKey.CLIENT_IP.put(request.getRemoteAddr());
        response.setHeader(properties.header(), requestId);
    }

    private String resolveRequestId(HttpServletRequest request) {
        if (properties.trustIncoming()) {
            String incomingKey = request.getHeader(properties.header());
            if (incomingKey != null && VALID_INCOMING_ID.matcher(incomingKey).matches()) {
                return incomingKey;
            }
        }
        return newRequestId();
    }

    private static String newRequestId() {
        String base36 = Long.toUnsignedString(RANDOM.nextLong(), 36);
        return "0".repeat(ID_LENGTH - base36.length()) + base36;
    }

}
