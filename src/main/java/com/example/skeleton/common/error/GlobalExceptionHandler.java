package com.example.skeleton.common.error;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외를 RFC 9457 ProblemDetail 응답으로 바꾸는 유일한 곳임.
 * 모든 응답에 기계가 판별할 {@code code} 필드를 실음.
 * 클라이언트는 사람이 읽는 {@code detail}이 아니라 {@code code}를 보고 분기해야 함.
 *
 * 컨트롤러 밖에서 난 실패도 {@code HandlerExceptionResolver}를 거쳐 여기로 모임.
 * Security 401/403은 {@code SecurityProblemDetailHandler}가 넘겨줌.
 * 방화벽 거부는 {@code SecurityConfig}의 {@code RequestRejectedHandler}가 넘겨줌.
 * 필터에서 빠져나온 예외는 {@code ExceptionResolvingFilter}가 넘겨줌.
 * 응답 형태를 다른 곳에서 만들지 않음.
 *
 * {@code detail}은 요청의 Accept-Language에 맞춰 메시지 번들에서 찾고, 번들에 키가 없으면 영어 폴백 메시지를 씀.
 * 로그는 국제화하지 않음. 운영자가 grep으로 찾을 수 있어야 하기 때문임.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Object[] NO_ARGS = {};

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 기능의 리소스를 찾을 수 없음 (예: 주문 ID가 없음).
     * 응답: 404 + 예외가 들고 있는 기능 코드 (예: ORDER_NOT_FOUND).
     * 기능 예외가 ResourceNotFoundException을 상속해 던짐.
     * detail은 기능 코드의 메시지 번들에서 찾음.
     * */
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.errorCode(), e.messageArgs(), e.getMessage());
    }

    /**
     * 리소스의 현재 상태와 충돌 (예: 이미 취소된 주문을 다시 취소, 중복 주문).
     * 응답: 409 + 예외가 들고 있는 기능 코드 (예: INVALID_ORDER_STATUS, ORDER_DUPLICATE).
     * 기능 예외가 ConflictException을 상속해 던짐.
     * */
    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.errorCode(), e.messageArgs(), e.getMessage());
    }

    /**
     * 리소스 상태와 무관한 비즈니스 정책 위반 (예: 영업시간 외 주문, 최소 주문 금액 미달).
     * 응답: 422 + 예외가 들고 있는 기능 코드 (예: ORDER_STORE_CLOSED).
     * 기능당 정책 예외 하나(예: OrderPolicyException)가 코드만 바꿔 여러 정책을 담당함.
     * */
    @ExceptionHandler(UnprocessableException.class)
    ProblemDetail handleUnprocessable(UnprocessableException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, e.errorCode(), e.messageArgs(), e.getMessage());
    }

    /**
     * 요청 본문(@Valid @RequestBody) 검증 실패.
     * 응답: 400 VALIDATION_FAILED + errors[{field, message}].
     * 입력값(rejected value)은 응답에 담지 않음.
     * 비밀번호 같은 민감한 값이 그대로 되돌아가지 않게 하기 위함임.
     * */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        return validationProblem(e.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                .toList());
    }

    /**
     * 메서드 파라미터 검증 실패 (예: @RequestParam @Min(1) int page 에 0을 보냄).
     * 응답: 400 VALIDATION_FAILED + errors[{field, message}].
     * 본문 검증과 같은 형태임.
     * {@code @Valid} 객체 파라미터는 필드 이름, 단순 파라미터(@RequestParam, @PathVariable)는 파라미터 이름이 field에 들어감.
     * */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleMethodValidation(HandlerMethodValidationException e) {
        return validationProblem(e.getParameterValidationResults().stream()
                .flatMap(result -> result instanceof ParameterErrors errors
                        // @Valid 객체 파라미터: 필드 단위 오류
                        ? errors.getFieldErrors().stream().map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                        // 단순 파라미터(@RequestParam, @PathVariable): 파라미터 이름 단위 오류
                        : result.getResolvableErrors().stream()
                                .map(error -> fieldError(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList());
    }

    /**
     * 필수 @RequestParam 누락.
     * 응답: 400 MISSING_PARAMETER, detail에 빠진 파라미터 이름.
     * */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParameter(MissingServletRequestParameterException e) {
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.MISSING_PARAMETER, new Object[]{e.getParameterName()},
            "Missing required parameter: %s".formatted(e.getParameterName()));
    }

    /**
     * 요청 본문을 해석할 수 없음.
     * 응답: 400 MALFORMED_REQUEST.
     * JSON이 깨졌으면 Spring이 HttpMessageNotReadableException을, 폼의 퍼센트 인코딩이 깨졌으면 Tomcat이 InvalidParameterException을 던짐.
     * 후자는 IllegalStateException 하위라 여기서 매핑하지 않으면 catch-all에서 500으로 뭉개짐.
     * */
    @ExceptionHandler({HttpMessageNotReadableException.class, InvalidParameterException.class})
    ProblemDetail handleUnreadableBody(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.MALFORMED_REQUEST, NO_ARGS, "Malformed request body");
    }

    /**
     * 파라미터 타입 불일치 (예: UUID 자리에 문자열, ?page=abc).
     * 응답: 400 INVALID_PARAMETER, detail에 파라미터 이름.
     * {@code @PathVariable}·{@code @RequestParam}(MethodArgumentTypeMismatchException)과 그 밖의 바인딩을 함께 받음.
     * */
    @ExceptionHandler(TypeMismatchException.class)
    ProblemDetail handleTypeMismatch(TypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_PARAMETER, new Object[]{e.getPropertyName()},
            "Invalid parameter: %s".formatted(e.getPropertyName()));
    }

    /**
     * multipart 요청에서 필수 파트(파일 등) 누락.
     * 응답: 400 MISSING_PARAMETER, detail에 빠진 파트 이름.
     * */
    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail handleMissingPart(MissingServletRequestPartException e) {
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.MISSING_PARAMETER, new Object[]{e.getRequestPartName()},
            "Missing required parameter: %s".formatted(e.getRequestPartName()));
    }

    /**
     * 그 밖의 요청 바인딩 실패 (필수 헤더·쿠키 누락 등).
     * 응답: 예외가 정한 상태 (대부분 400 REQUEST_REJECTED).
     * 400으로 고정하지 않음.
     * 하위 타입 MissingPathVariableException은 경로 변수 이름을 잘못 매핑한 서버 쪽 실수라 500이기 때문임.
     * */
    @ExceptionHandler(ServletRequestBindingException.class)
    ProblemDetail handleRequestBinding(ServletRequestBindingException e) {
        return statusProblem(e.getStatusCode(), e);
    }

    /**
     * 서버가 받지 않는 Content-Type으로 본문을 보냄.
     * 응답: 415 UNSUPPORTED_MEDIA_TYPE, detail에 보낸 Content-Type.
     * 클라이언트가 Content-Type을 고쳐야 함.
     * */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        String contentType = String.valueOf(e.getContentType());
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, new Object[]{contentType},
            "Unsupported content type: %s".formatted(contentType));
    }

    /**
     * 클라이언트가 Accept로 요구한 응답 형식을 만들 수 없음 (예: Accept: application/xml).
     * 응답: 406 NOT_ACCEPTABLE.
     * 클라이언트가 Accept를 고쳐야 함.
     * */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ProblemDetail handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        return problem(HttpStatus.NOT_ACCEPTABLE, CommonErrorCode.NOT_ACCEPTABLE, NO_ARGS,
            "The requested response format (Accept) is not available");
    }

    /**
     * multipart가 아닌 요청을 업로드 API로 보냄 (예: JSON 본문으로 파일 업로드 엔드포인트 호출).
     * 응답: 400 MALFORMED_REQUEST.
     * 업로드 크기 초과(MaxUploadSizeExceededException)도 이 예외의 하위 타입이지만, 더 구체적인 413 핸들러가 먼저 선택됨.
     * */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail handleMultipart(MultipartException e) {
        return problem(HttpStatus.BAD_REQUEST, CommonErrorCode.MALFORMED_REQUEST, NO_ARGS, "Malformed request body");
    }

    /**
     * 업로드 크기 초과.
     * 응답: 413 PAYLOAD_TOO_LARGE.
     * 한도는 spring.servlet.multipart.max-file-size / max-request-size 설정이 정함.
     * */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handlePayloadTooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, CommonErrorCode.PAYLOAD_TOO_LARGE, NO_ARGS,
            "The upload exceeds the maximum allowed size");
    }

    /**
     * 상태 코드를 직접 담아 던지는 범용 예외 (ResponseStatusException 포함).
     * 응답: 예외가 정한 상태 (4xx REQUEST_REJECTED / 5xx INTERNAL_ERROR).
     * 우리 코드는 이 예외를 던지지 않음.
     * 라이브러리·프레임워크가 던진 경우를 위해 상태를 존중함.
     * */
    @ExceptionHandler(ErrorResponseException.class)
    ProblemDetail handleErrorResponse(ErrorResponseException e) {
        return statusProblem(e.getStatusCode(), e);
    }

    /**
     * 클라이언트가 먼저 연결을 끊음 (브라우저 종료, SSE 이탈 등).
     * 응답: 없음 (DEBUG 로그만).
     * 응답을 받을 상대가 없고 서버 결함도 아니므로 500·ERROR 로그로 장애처럼 남기지 않음.
     * 다른 예외에 감싸여 오는 연결 끊김은 catch-all로 감.
     * 그런 경우를 발견하면 여기에 추가함.
     * */
    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    void handleClientDisconnected(Exception e) {
        log.debug("request.client-disconnected type={}", e.getClass().getName());
    }

    /**
     * 경로는 있지만 지원하지 않는 HTTP 메서드 (예: PATCH /api/orders).
     * 응답: 405 METHOD_NOT_ALLOWED.
     * */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                CommonErrorCode.METHOD_NOT_ALLOWED,
                NO_ARGS,
                "HTTP method not allowed for this endpoint");
    }

    /**
     * 존재하지 않는 경로.
     * 응답: 404 ENDPOINT_NOT_FOUND.
     * 정적 리소스 매핑이 켜져 있으면 NoResourceFoundException, 꺼져 있으면(spring.web.resources.add-mappings=false) NoHandlerFoundException이 옴.
     * */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ProblemDetail handleNoResource(Exception e) {
        return problem(
                HttpStatus.NOT_FOUND,
                CommonErrorCode.ENDPOINT_NOT_FOUND,
                NO_ARGS,
                "The requested endpoint does not exist");
    }

    /**
     * 인증 실패 (토큰 없음, 잘못된 토큰, 만료된 토큰).
     * 응답: 401 AUTH_REQUIRED / AUTH_TOKEN_INVALID / AUTH_TOKEN_EXPIRED.
     * Security 필터 체인에서 발생하므로 SecurityProblemDetailHandler가 HandlerExceptionResolver로 위임해야 여기에 도달함.
     * */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleUnauthenticated(AuthenticationException e) {
        return problem(HttpStatus.UNAUTHORIZED, authErrorCode(e), NO_ARGS, "Authentication failed");
    }

    /**
     * 인증은 됐지만 권한 부족.
     * 응답: 403 ACCESS_DENIED.
     * 메서드 보안(@PreAuthorize)은 컨트롤러에서, URL 인가 규칙은 Security 체인에서 위임되어 들어옴.
     * */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, CommonErrorCode.ACCESS_DENIED, NO_ARGS, "Access denied");
    }

    /**
     * Security 방화벽이 거부한 요청 (경로 조작: /../, %2e, ; 등).
     * 응답: 400 REQUEST_REJECTED.
     * 보통은 SecurityConfig의 RequestRejectedHandler가 위임하고, 문제 헤더를 코드가 나중에 읽을 때는 일반 예외로 올라옴.
     * 스캐너·공격 트래픽이 대부분이라 ERROR 로그를 남기지 않음.
     * 500으로 두면 로그가 공격 트래픽으로 뒤덮임.
     * */
    @ExceptionHandler(RequestRejectedException.class)
    ProblemDetail handleRequestRejected(RequestRejectedException e) {
        return statusProblem(HttpStatus.BAD_REQUEST, e);
    }

    /**
     * 비동기 요청(SSE, Callable, DeferredResult) 처리 시간 초과.
     * 응답: 503 SERVICE_UNAVAILABLE.
     * 503은 클라이언트·게이트웨이가 "재시도 가능"으로 판단하는 신호임.
     * 서버 결함이 아니므로 ERROR 로그를 남기지 않음.
     * 타임아웃이 나면 Spring이 작업 스레드를 인터럽트하므로 InterruptedException이 타임아웃 예외보다 먼저 결과로 등록될 수 있음.
     * 같은 상황이므로 둘 다 503으로 응답함 (실행마다 503과 500이 갈리던 경쟁을 막음).
     * */
    @ExceptionHandler({AsyncRequestTimeoutException.class, InterruptedException.class})
    ProblemDetail handleAsyncTimeout(Exception e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE, NO_ARGS,
            "The service is temporarily unavailable. Please try again later");
    }

    /**
     * 외부 API 호출 실패 (RestClient, @HttpExchange 클라이언트).
     * 응답: 시간 초과 504 UPSTREAM_TIMEOUT / 그 외(연결 실패, 상대의 오류 응답, 응답 변환 실패) 502 UPSTREAM_ERROR.
     * 상대 서버의 상태 코드를 그대로 전달하지 않음.
     * 상대가 준 401은 우리가 상대에게 잘못 인증했다는 뜻이지, 클라이언트의 문제가 아님.
     * 상대의 상태와 예외는 로그(upstream.failed)에만 남김.
     * 클라이언트에게 의미 있는 상대 응답(예: 결제 한도 초과)은 기능 코드가 잡아 기능 예외로 바꿔 던짐.
     * */
    @ExceptionHandler(RestClientException.class)
    ProblemDetail handleUpstreamFailure(RestClientException e) {
        // 시간 초과는 가장 안쪽 원인으로 드러남. HTTP 클라이언트 구현마다 타입이 달라 둘을 봄 (JDK HttpClient, 그 외).
        Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(e);
        boolean timeout = rootCause instanceof SocketTimeoutException || rootCause instanceof HttpTimeoutException;
        HttpStatus status = timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        String upstreamStatus = e instanceof RestClientResponseException response ? String.valueOf(response.getStatusCode().value()) : "-";
        log.error("upstream.failed status={} upstreamStatus={} type={}", status.value(), upstreamStatus, e.getClass().getName(), e);
        return timeout
                ? problem(status, CommonErrorCode.UPSTREAM_TIMEOUT, NO_ARGS, "A dependent service did not respond in time")
                : problem(status, CommonErrorCode.UPSTREAM_ERROR, NO_ARGS, "A dependent service failed to respond correctly");
    }

    /**
     * 위에서 다루지 않은 모든 예외 (catch-all).
     * 응답: 500 INTERNAL_ERROR + ERROR 로그(스택트레이스).
     * 정체를 모르는 예외는 서버 결함으로 봄.
     * 클라이언트 잘못(4xx)이 여기로 오면 안 됨.
     * 그런 예외를 발견하면 위에 전용 핸들러를 추가함.
     * */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        return statusProblem(HttpStatus.INTERNAL_SERVER_ERROR, e);
    }

    /**
     * 토큰 없이 들어온 요청(익명)은 OAuth2 예외가 아니므로 AUTH_REQUIRED로 봄.
     * 토큰이 있었다면 검증에 실패한 것이고, 그중 만료만 따로 구분함.
     * 만료는 클라이언트가 다시 로그인하지 않고 토큰 갱신만으로 복구할 수 있는 유일한 경우이기 때문임.
     */
    private static CommonErrorCode authErrorCode(AuthenticationException e) {
        if (!(e instanceof OAuth2AuthenticationException)) {
            return CommonErrorCode.AUTH_REQUIRED;
        }
        return isExpiredToken(e) ? CommonErrorCode.AUTH_TOKEN_EXPIRED : CommonErrorCode.AUTH_TOKEN_INVALID;
    }

    /**
     * Spring Security는 만료를 별도 예외 타입으로 알려주지 않고, {@code JwtTimestampValidator}의 오류 설명("Jwt expired at ...")으로만 알림.
     * 그래서 설명 문구를 보고 판단함.
     * 문구가 바뀌어도 AUTH_TOKEN_INVALID로 떨어질 뿐 401 응답은 그대로임.
     */
    private static boolean isExpiredToken(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof JwtValidationException validation) {
                return validation.getErrors().stream()
                        .anyMatch(error -> error.getDescription() != null
                                && error.getDescription().contains("expired"));
            }
        }
        return false;
    }

    /**
     * 우리 ErrorCode가 없는 예외를 상태 코드만으로 응답함.
     * 5xx는 서버 결함이니 스택트레이스와 함께 ERROR 로그를 남김.
     * 4xx는 클라이언트 잘못이라 로그를 남기지 않음. 접근 로그의 status로 충분함.
     */
    private ProblemDetail statusProblem(HttpStatusCode status, Exception e) {
        if (status.is5xxServerError()) {
            log.error("request.failed status={} type={}", status.value(), e.getClass().getName(), e);
        }
        // 구체적인 코드는 전용 핸들러가 정함. 여기 오는 건 우리 ErrorCode가 없는 예외라 4xx/5xx로만 나눔.
        CommonErrorCode code = status.is4xxClientError() ? CommonErrorCode.REQUEST_REJECTED : CommonErrorCode.INTERNAL_ERROR;
        return problem(status, code, new Object[]{statusText(status)}, "Request failed with status %d".formatted(status.value()));
    }

    private static String statusText(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known == null ? String.valueOf(status.value()) : "%d %s".formatted(status.value(), known.getReasonPhrase());
    }

    private ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_FAILED, NO_ARGS, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", String.valueOf(field), "message", String.valueOf(message));
    }

    private ProblemDetail problem(HttpStatusCode status, ErrorCode errorCode, Object[] args, String fallbackDetail) {
        String detail = messageSource.getMessage(errorCode.messageKey(), args, fallbackDetail,
                LocaleContextHolder.getLocale());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", errorCode.code());
        return problem;
    }
}
