# ADR 0010: 보안 경계의 응답·로그 컨텍스트·CORS

날짜: 2026-09-29 · 상태: 채택

## 배경

ADR 0007은 "모든 실패는 ProblemDetail + `code`"를 정했지만, 가장 흔한 실패인 401/403은 Spring Security
필터 체인에서 응답이 작성되어 `@RestControllerAdvice`에 닿지 않았다 — 본문 없는 401이 나갔다. 또 로그에는
requestId만 있고 "누가" 보낸 요청인지가 없었으며, 브라우저 클라이언트를 위한 CORS 정책이 없었다.

## 결정

1. **401/403은 MVC 예외 해석기로 위임한다.** `SecurityProblemDetailHandler`(`AuthenticationEntryPoint` +
   `AccessDeniedHandler`)가 `handlerExceptionResolver`를 호출해 `GlobalExceptionHandler`가 응답을 쓴다.
   JSON을 필터에서 직접 쓰지 않는 이유: 응답 형태·에러 코드·국제화가 컨트롤러 예외와 한 곳에서 결정되어야
   드리프트가 없다. `WWW-Authenticate` 헤더(RFC 6750)는 Spring bearer 기본 구현이 먼저 쓴다.
   - 코드: 토큰 미지참 `AUTH_REQUIRED`, 만료 `AUTH_TOKEN_EXPIRED`, 그 외 검증 실패 `AUTH_TOKEN_INVALID`,
     인가 실패 `ACCESS_DENIED`. 만료만 구분하는 이유는 클라이언트가 재로그인 대신 토큰 갱신으로 복구할 수
     있는 유일한 경우라서다. Spring은 만료를 오류 설명 문자열로만 알리므로, 문구가 바뀌면
     `AUTH_TOKEN_INVALID`로 강등된다(401은 유지).
   - 진입점은 두 곳에 등록한다: `oauth2ResourceServer`(잘못된/만료 토큰)와 `exceptionHandling`(미지참, URL 인가).
2. **userId는 Security 체인 안의 별도 필터가 MDC에 넣는다.** 기존 두 필터(`RequestMdcFilter`,
   `RequestLoggingFilter`)는 401/403 요청도 기록하기 위해 Security보다 앞에 있어야 하며(ADR 0004), 그 위치엔
   인증 정보가 없다. 그래서 `AuthenticatedUserMdcFilter`를 `BearerTokenAuthenticationFilter` 직후에 넣는다.
   빈으로 등록하지 않는다(서블릿 필터 이중 등록 방지). MDC 정리는 가장 바깥의 `RequestMdcFilter`가
   `MdcKey.removeAll()`로 전담한다 — 안쪽 필터가 지우면 바깥의 res 접근 로그에서 userId가 사라진다.
   - 값은 `Authentication#getName()`(JWT `sub`)이다. **`sub`가 불투명 ID라는 전제**이며, IdP가 이메일 등
     개인정보를 `sub`로 쓴다면 이 필터를 빼거나 다른 클레임으로 바꾼다.
3. **CORS는 Security 체인에서 처리하고 기본은 닫는다.** preflight(OPTIONS)에는 `Authorization`이 없어
   MVC 레벨 CORS로는 인증에 먼저 막힌다. `http.cors()` + `CorsProperties`(`skeleton.cors.*`) 기반
   `CorsConfigurationSource`를 쓰며, 허용 출처 기본값은 빈 목록(CORS 헤더 없음)이다.
   `allow-credentials=true`와 `*` 출처의 조합은 기동 시 거부한다. `Location`은 기본 노출하고, 요청 ID 헤더(`skeleton.request-id.header`)는 설정과 무관하게 항상 노출한다.

## 결과

- 클라이언트는 401/403도 다른 실패와 같은 ProblemDetail 파서로 처리하고 `code`로 분기한다.
- 인증된 요청의 모든 로그 라인에 `[requestId] [userId]`가 붙는다. 익명/401 요청은 userId가 비어 있다.
- 필터·컨테이너 수준의 기타 예외(`/error` 경로)는 ADR 0012에서 같은 원칙(resolver 위임)으로 해결했다.
