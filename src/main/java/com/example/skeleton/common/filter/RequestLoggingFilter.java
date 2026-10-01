package com.example.skeleton.common.filter;

import com.example.skeleton.common.context.MdcKey;
import com.example.skeleton.common.filter.wrapper.CachedBodyRequestWrapper;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 서버에 도달한 모든 요청(Spring Security가 거부하는 요청 포함)의 접근 로그.
 * 메시지는 key=value 쌍이라 로그 수집기가 별도 파싱 패턴 없이 처리할 수 있음.
 *
 * INFO에서는 메타데이터만 기록함.
 * 이 클래스의 로거를 DEBUG로 내리면 요청/응답 본문까지 기록함(JSON/텍스트만, 절단 없이 전체).
 * 요청 본문은 미리 읽어 두므로 요청 로그는 항상 같은 요청의 서비스 레이어 로그보다 먼저 찍힘.
 *
 * 비동기 요청(Callable, DeferredResult, SseEmitter 등)은 응답이 나중에 ASYNC 디스패치에서 쓰임.
 * 그래서 res 로그는 첫 디스패치에서 {@link AsyncListener}를 등록하고 {@code onComplete}에서 남김.
 * 정상 응답, 타임아웃, 에러, 클라이언트 연결 끊김 모두 {@code onComplete}가 호출되므로 접근 로그가 빠지지 않음.
 * Spring의 {@code ServerHttpObservationFilter}와 같은 방식이며, spring-framework#28294에서 메인테이너가 안내한 방법임.
 * DEBUG의 응답 래퍼는 첫 디스패치에서 만든 것을 ASYNC 디스패치에서 찾아 이어 쓰고, 본문은 마지막 디스패치에서만 내보냄.
 * Spring의 {@code ShallowEtagHeaderFilter}와 같은 패턴임.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PREFIXES = List.of("/actuator", "/swagger-ui", "/v3/api-docs");
    private static final String START_NANOS_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".START_NANOS";
    private static final String RESPONSE_BODY_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".RESPONSE_BODY";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /** 비동기 요청의 마지막 디스패치에서 DEBUG 응답 본문을 내보내야 하므로 ASYNC 디스패치에서도 실행. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        boolean firstDispatch = !isAsyncDispatch(request);
        HttpServletRequest requestToUse = request;
        HttpServletResponse responseToUse = response;

        if (firstDispatch) {
            request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
            if (log.isDebugEnabled()) {
                // 폼 POST는 감싸지 않음. 본문을 먼저 읽으면 컨테이너의 폼 파라미터가 사라짐.
                // 여기서 직접 파싱하면 잘못된 폼이 req 로그 전에 예외를 던짐. 이 필터는 예외 처리 필터보다 바깥이라 접근 로그가 통째로 빠짐.
                boolean formPost = isFormPost(request);
                requestToUse = formPost ? request : new CachedBodyRequestWrapper(request);
                responseToUse = new ContentCachingResponseWrapper(response);
                log.debug("req uri={} method={} ip={} body={}",
                        uriWithQuery(request),
                        request.getMethod(),
                        request.getRemoteAddr(),
                        formPost ? "(form)" : readableBody(((CachedBodyRequestWrapper) requestToUse).body(), request.getContentType()));
            } else {
                log.info("req uri={} method={} ip={}", uriWithQuery(request), request.getMethod(), request.getRemoteAddr());
            }
        }

        try {
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            // ASYNC 디스패치의 response는 첫 디스패치의 래퍼를 다른 래퍼들이 감싼 것이므로 풀어서 찾음.
            ContentCachingResponseWrapper cachedResponse = WebUtils.getNativeResponse(responseToUse, ContentCachingResponseWrapper.class);
            if (isAsyncStarted(request)) {
                // 아직 응답 전임. 본문 방출과 res 로그를 미룸. 리스너는 첫 디스패치에서 한 번만 등록함.
                if (firstDispatch) {
                    request.getAsyncContext().addListener(new CompletionLogListener(request, responseToUse, MdcKey.snapshot()));
                }
            } else {
                String body = cachedResponse == null ? null : readableBody(cachedResponse.getContentAsByteArray(), cachedResponse.getContentType());
                if (cachedResponse != null) {
                    cachedResponse.copyBodyToResponse();
                }
                if (firstDispatch) {
                    logResponse(request, responseToUse.getStatus(), body);
                } else {
                    // 비동기 요청의 마지막 디스패치. 기록은 완료 리스너가 하므로 본문만 넘겨줌.
                    request.setAttribute(RESPONSE_BODY_ATTRIBUTE, body);
                }
            }
        }
    }

    /** body가 null이면 본문을 기록하지 않는 요약 모드(INFO)임. */
    private void logResponse(HttpServletRequest request, int status, String body) {
        long durationMs = elapsedMs((Long) request.getAttribute(START_NANOS_ATTRIBUTE));
        if (body != null) {
            log.debug("res uri={} method={} status={} durationMs={} body={}", uriWithQuery(request), request.getMethod(), status, durationMs, body);
        } else {
            log.info("res uri={} method={} status={} durationMs={}", uriWithQuery(request), request.getMethod(), status, durationMs);
        }
    }

    /**
     * 비동기 요청이 완전히 끝났을 때 res 로그를 남김.
     * onComplete는 요청이 끝난 뒤 컨테이너 스레드에서 호출되어 MDC가 비어 있으므로, 등록 시점의 MDC를 복원해서 기록함.
     */
    private final class CompletionLogListener implements AsyncListener {

        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final Map<MdcKey, String> mdc;

        private CompletionLogListener(HttpServletRequest request, HttpServletResponse response, Map<MdcKey, String> mdc) {
            this.request = request;
            this.response = response;
            this.mdc = mdc;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            MdcKey.restore(mdc);
            try {
                logResponse(request, response.getStatus(), (String) request.getAttribute(RESPONSE_BODY_ATTRIBUTE));
            } finally {
                MdcKey.removeAll();
            }
        }

        /** 같은 요청에서 비동기가 다시 시작되면 리스너가 해제되므로 다시 등록함 (서블릿 스펙). */
        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // 타임아웃 응답은 Spring이 ASYNC 디스패치로 쓰고, 그 뒤 onComplete가 호출됨.
        }

        @Override
        public void onError(AsyncEvent event) {
            // 에러 후에도 onComplete가 호출되므로 여기서는 기록하지 않음.
        }
    }

    /**
     * body는 이스케이프 없이 원문 그대로 기록함.
     * 사람 가독성과 "사용자가 보낸 그대로"의 원문 보존이 이 필드의 존재 이유임.
     * 값에 공백이 있을 수 있어 kv 파싱 대상에서 빠지므로 항상 라인의 마지막 필드에 둠.
     * 개행만 공백으로 접어 한 로그가 한 라인이 되게 함.
     */
    private String readableBody(byte[] body, String contentType) {
        if (body.length == 0) {
            return "";
        }
        MediaType mediaType = parseOrNull(contentType);
        if (mediaType == null || !isTextual(mediaType)) {
            return "(non-text)";
        }
        return new String(body, charsetOf(mediaType)).replaceAll("\\s*\\R\\s*", " ");
    }

    private static boolean isTextual(MediaType mediaType) {
        return "text".equals(mediaType.getType()) || mediaType.getSubtype().contains("json");
    }

    /** 컨테이너가 폼을 파싱하는 경우는 POST뿐임(Tomcat 기본). PUT/PATCH 폼은 Spring이 스트림에서 직접 읽으므로 감싸도 됨. */
    private static boolean isFormPost(HttpServletRequest request) {
        MediaType mediaType = parseOrNull(request.getContentType());
        return HttpMethod.POST.matches(request.getMethod()) && mediaType != null && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(mediaType);
    }

    /**
     * Content-Type에 명시된 charset을 따르고, 없으면 UTF-8로 봄.
     * {@code response.getCharacterEncoding()}은 charset이 없는 응답에서 서블릿 기본값 ISO-8859-1을 돌려줘 UTF-8 JSON의 한글이 깨지므로 쓰지 않음.
     */
    private static Charset charsetOf(MediaType mediaType) {
        return mediaType.getCharset() != null ? mediaType.getCharset() : StandardCharsets.UTF_8;
    }

    /** 이 필터는 예외 처리 필터보다 바깥이라, 깨진 Content-Type이 요청 실패로 번지지 않게 해석 실패를 삼킴. */
    private static MediaType parseOrNull(String contentType) {
        if (contentType == null) {
            return null;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return null;
        }
    }

    private static String uriWithQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : "%s?%s".formatted(request.getRequestURI(), query);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

}
