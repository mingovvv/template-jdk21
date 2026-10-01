# ADR 0012: 모든 계층의 실패는 HandlerExceptionResolver를 거쳐 어드바이스 한 곳에서 응답한다

날짜: 2026-09-30 (2026-10-01 단순화·필터 재배치) · 상태: 채택 (ADR 0006·0007·0010을 확장)

## 배경

ADR 0007은 "모든 실패는 ProblemDetail + `code`"를 정했지만, 컨트롤러 밖의 실패는 이 계약을 벗어났다.

- 필터 체인에서 샌 예외 → 컨테이너가 `/error`로 ERROR 재디스패치 → Boot `BasicErrorController`의 기본 JSON
  (`{timestamp, status, error, path}`). 그 시점엔 MDC가 지워져 있고, 접근 로그는 `status=200`으로 오기록된다.
- Security 방화벽(`StrictHttpFirewall`)은 거부 시 기본 처리기가 `sendError(400)`을 호출해 ERROR 디스패치를 만든다.
- Spring Security 7은 ERROR 디스패치도 인가한다 — 익명 요청의 실제 에러(400/500)가 401로 둔갑한다.
- 어드바이스가 명시적으로 다루지 않는 Spring MVC 내장 예외(415, 406, 파라미터 누락 400 등)는 catch-all이
  500으로 뭉갰다.

프레임워크 방향: Spring Framework 메인테이너는 "에러는 error 디스패치에 의존하지 말고 가능한 한 애플리케이션이
처리해야 한다"는 입장이고(spring-framework#34272), `/error`의 ProblemDetail 렌더링은 Boot 4.x에서 미해결이다
(spring-boot#43850).

## 결정

**응답 본문(ProblemDetail)을 만드는 곳은 `GlobalExceptionHandler` 하나다. 각 실패는 그것이 생긴 곳의 공식 확장
지점에서 `handlerExceptionResolver`로 위임한다** — ERROR 디스패치가 생기기 전에, 출처에서 끝낸다.

| 실패 지점 | 위임 경로 |
|---|---|
| 컨트롤러/서비스 | DispatcherServlet → 어드바이스 (기본) |
| Security 401/403 | `AuthenticationEntryPoint`/`AccessDeniedHandler` → resolver (`SecurityProblemDetailHandler`, ADR 0010) |
| Security 방화벽 거부 | `RequestRejectedHandler` 빈 → resolver. 늦게 던져지는 `RequestRejectedException`도 어드바이스가 400으로 매핑 |
| 필터에서 샌 예외 (Boot `FormContentFilter` 포함) | `ExceptionResolvingFilter`의 try/catch → resolver |

- **`ExceptionResolvingFilter` 순서 HIGHEST+3**: `RequestLoggingFilter`(HIGHEST+2) 바로 안쪽(접근 로그 status 정확),
  Boot `FormContentFilter`(-9900)·Security(-100)보다 바깥(그 예외도 REQUEST 디스패치 안에서 포착).
  이 위치는 Boot `RequestContextFilter`(-105)보다 바깥이라 로케일이 비어 있으므로, 해석 동안 `LocaleContextHolder`에
  요청 로케일을 직접 싣는다(없으면 Accept-Language가 무시됨을 실측 확인).
  - 처음엔 로케일 때문에 -101에 뒀으나, 잘못 인코딩된 PUT 폼이 `FormContentFilter`에서 예외를 던져 필터 밖으로 샜다
    (2026-10-01 실측): 500 응답, 접근 로그 `status=200`, requestId 없는 Tomcat ERROR 로그. 바깥으로 옮기고 로케일을
    직접 싣는 쪽이 더 단순하고 정확하다.
- **예외마다 전용 핸들러를 둔다** (2026-10-01): Spring MVC 내장 예외(415 `UNSUPPORTED_MEDIA_TYPE`, 406 `NOT_ACCEPTABLE`,
  413 `PAYLOAD_TOO_LARGE`, 파라미터·파트 누락, 바인딩 실패, `ErrorResponseException` 등)도 `@ExceptionHandler`로 하나씩 받는다 —
  어떤 예외가 어떤 응답이 되는지 핸들러 목록만 보고 알 수 있어야 한다. 처음엔 catch-all이 `instanceof ErrorResponse`로 상태를
  꺼내 한꺼번에 처리했으나, 분기가 catch-all 안에 숨어 읽기 어렵다는 이유로 풀어냈다.
- **catch-all(`Exception`)은 500 + ERROR 로그만 한다.** 정체를 모르는 예외 = 서버 결함이다. 클라이언트 잘못(4xx)이 catch-all에
  도달하면 그건 전용 핸들러가 빠진 것이므로 핸들러를 추가한다. 서버 쪽 실수인 Spring 예외(`MissingPathVariableException`,
  `HttpMessageNotWritableException` 등)는 500이 맞으므로 따로 두지 않는다.
- 클라이언트 연결 끊김(`AsyncRequestNotUsableException`, Tomcat `ClientAbortException`)은 응답 없이 DEBUG만 남긴다.
  5xx만 ERROR 로그를 남긴다(4xx는 접근 로그로 충분).
- **ERROR 디스패치 전용 처리는 두지 않는다.** 필터보다 바깥에 남은 건 예외를 던지지 않도록 방어된 우리 MDC·로깅
  필터와 Boot의 인코딩·Forwarded 필터뿐이라, ERROR 디스패치는 사실상 생기지 않는다. 생기더라도
  `SecurityConfig`의 `dispatcherTypeMatchers(ERROR).permitAll()` 덕에 401로 둔갑하지 않고 Boot `/error`가 응답한다.

### 외부 API 호출 실패 (2026-10-01 추가)

`RestClient`/`@HttpExchange` 클라이언트의 `RestClientException`을 어드바이스가 직접 매핑한다 — 기능 코드는 외부 호출
예외를 감쌀 필요 없이 그대로 던진다.

| 실패 | 응답 |
|---|---|
| 응답 시간 초과 (원인 사슬에 `SocketTimeoutException`/`HttpTimeoutException`) | 504 `UPSTREAM_TIMEOUT` |
| 연결 실패, 상대의 오류 응답(4xx 포함), 응답 변환 실패 | 502 `UPSTREAM_ERROR` |

- **상대 서버의 상태 코드를 그대로 전달하지 않는다.** 상대가 준 401은 "우리가 상대에게 잘못 인증했다"는 뜻이지 클라이언트의
  문제가 아니다. 상대의 상태·예외는 `upstream.failed status= upstreamStatus= type=` 로그에만 남기고 응답 detail에는 노출하지 않는다.
- 클라이언트에게 의미 있는 상대 응답(예: 결제 한도 초과)은 기능의 외부 호출 코드가 잡아 기능 예외(422 등)로 바꿔 던진다.
- 502·504를 500과 구분하는 이유: 클라이언트·게이트웨이가 503·504를 "재시도 가능"으로 판단하고, 모니터링에서 "우리 버그(500)"와
  "연동 장애(502/504)"가 상태 코드만으로 갈린다. 시간 초과 판별은 `NestedExceptionUtils.getMostSpecificCause()`로 가장 안쪽 원인을 본다.
- **외부 호출 클라이언트에는 반드시 연결·읽기 타임아웃을 설정한다.** 없으면 504가 나는 대신 요청 스레드가 상대를 무한정 기다린다.
- WebClient(리액티브)의 `WebClientException`은 다루지 않는다 — 도입 시 같은 기준으로 매핑을 추가한다.
- 검증(2026-10-01, 사본 프로젝트에 임시 컨트롤러 + 가짜 상류 서버): 느린 상류 → 504, 꺼진 포트 → 502, 상류 401 → 502(401 미전달).

### 기각한 대안

- **ERROR 디스패치를 가로채기**(최초 구현, 이후 안전망으로 축소했다가 삭제): 요청 속성 해석·URI 래퍼·MDC 복원이
  필요해 복잡했고, 실패를 출처가 아닌 사후에 처리한다. 원인 예외를 상태 코드로 뭉개면 400이 500이 되는 함정도
  실측으로 확인했다.
- **응답 래퍼로 `sendError` 가로채기**(Boot `ErrorPageFilter` 방식): 모든 요청의 응답을 감싸 영향 범위가 가장
  넓고, `sendError`/`getWriter` 커밋 타이밍 문제가 알려져 있다(spring-boot#17568). 주류 패턴이 아니다.

## 결과

- 클라이언트가 받는 모든 실패는 `application/problem+json` + `code`다. 필터·방화벽 수준 실패 포함.
- 방화벽 거부가 REQUEST 디스패치 안에서 끝나므로, 접근 로그의 `res` 줄에 최종 응답 본문까지 기록된다.
- 응답 형태를 필터나 핸들러에서 직접 쓰는 코드는 금지다 — 새 실패 출처가 생기면 그 확장 지점에서 resolver로 위임한다.
- 응답이 이미 커밋된 뒤의 예외(클라이언트 연결 끊김, 스트리밍 중 실패)는 본문을 바꿀 수 없어 WARN 로그만 남긴다.
- 검증(2026-10-01, 앱 기동 후 curl): 잘못된 PUT/POST 폼 400(en/ko, 접근 로그 status=400, ERROR 로그 0건),
  방화벽 400(instance=원래 경로), 401×2, 415, 정상 요청.
- 비동기 요청(2026-10-01 처리). Callable·DeferredResult 타임아웃과 그때 작업 스레드 인터럽트로 생기는 `InterruptedException`을
  모두 503 `SERVICE_UNAVAILABLE`로 응답함. 둘이 경쟁해 실행마다 503과 500이 갈리던 문제를 실측으로 확인하고 막음.
  전역 타임아웃은 `spring.mvc.async.request-timeout`(30초)로 명시하고, 엔드포인트별 타임아웃은 CONVENTIONS.md 8장 규칙을 따름.
  접근 로그와 MDC의 비동기 처리는 ADR 0004 참고.
- `MultipartException`(multipart가 아닌 요청을 업로드 API로 보냄)은 400 `MALFORMED_REQUEST`. 처음엔 catch-all에서 500이었음.
- 검증(2026-10-01, 사본 프로젝트에 실험 컨트롤러·필터·가짜 상류 서버를 두고 50개 시나리오 실행): DEBUG·INFO 모두 50/50 통과.
  범주는 요청/라우팅, 본문, 파라미터, 업로드, 인증/인가, 도메인, 서버, 비동기(타임아웃 5회 반복 포함), 외부 호출, CORS, 컨테이너.

### 알려진 한계

- **CORS 거부(403)는 평문 "Invalid CORS request"로 응답함.** Spring의 CORS 필터가 직접 응답을 쓰기 때문임.
  브라우저는 CORS가 실패한 응답의 본문을 JS에 보여 주지 않으므로 ProblemDetail로 바꿔도 읽을 클라이언트가 거의 없어 그대로 둠.
- **헤더·URL이 너무 큰 요청은 Tomcat의 HTML 400으로 응답함.** 요청을 해석하는 단계에서 거부되어 Spring까지 오지 않음.
  게이트웨이·로드밸런서에서 다룰 영역임. 응답에 서버 버전 정보는 노출되지 않음을 확인함.
