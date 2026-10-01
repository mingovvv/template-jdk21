# ADR 0004: 필터 기반 요청 로깅과 MDC requestId

날짜: 2026-09-01 · 상태: 채택

## 배경

요청 단위의 접근 로그와 흐름 추적이 필요하다. 후보는 서블릿 필터와 Spring MVC 인터셉터. 이 API는 기본 전부 인증이므로, 운영에서 가장 자주 조사하게 될 요청이 401/403이라는 점이 결정적 제약이다.

## 결정

서블릿 필터 2개로 구성하고 인터셉터는 사용하지 않는다. 둘 다 Spring Security 필터 체인보다 앞에 배치한다.

1. **`RequestMdcFilter`** (Boot `ForwardedHeaderFilter` 직후, 사실상 최우선) — 요청마다 ID를 **항상 내부 생성**해 MDC(`requestId`)에 넣고 응답 헤더로 반환한다(헤더 이름은 `skeleton.request-id.header`, 기본 `X-Request-Id`). 로그 패턴에 `%X{requestId}`가 포함되어 요청/서비스/응답/Security 로그 전부에 같은 ID가 붙는다. 요청 메타데이터(`method`, `uri`(쿼리 제외), `clientIp`)도 이 필터가 MDC에 넣지만 텍스트 패턴에는 넣지 않는다 — req/res 접근 로그와 중복되며, JSON 로그 전환 시 필드로 자동 포함된다. MDC 정리(`MdcKey.removeAll()`)도 이 필터가 전담한다.
   - **수신 `X-Request-Id` 승계는 설정 스위치(`skeleton.request-id.trust-incoming`)이며 기본값은 `false`다.** 이 서버가 곧 신뢰 경계(엣지)일 때 외부 클라이언트가 ID를 지정할 수 있으면 충돌·재사용으로 "ID 하나 = 요청 하나" 전제가 깨진다 — requestId는 신뢰 경계 안에서 태어나야 하므로 기본은 내부 생성이고, 켜는 것이 의식적 행위여야 한다(secure by default). 신뢰할 수 있는 게이트웨이/상류 서비스 뒤에 배포할 때만 프로파일 yml에서 켜며, 켜더라도 형식 검증(영숫자/하이픈/언더스코어 64자 이내)을 통과한 값만 승계해 로그 인젝션을 막는다. 본격적인 MSA 분산 추적 단계에서는 이 스위치가 아니라 W3C `traceparent` + Micrometer Tracing을 우선 검토하고 새 ADR을 남긴다.
   - **형식은 랜덤 64비트를 base36으로 인코딩한 고정 13자**(예: `k3j9x2mzq81ab`). UUID(36자)는 로그 라인을 불필요하게 길게 만들고, requestId에 필요한 건 영구 유일성이 아니라 로그 보관 기간(14일) 내 사실상 유일성이다 — 64비트면 초당 1,000건 트래픽에서도 충돌 확률이 무시 가능하다. 순수 영숫자라 로그 뷰어에서 더블클릭 한 번으로 전체 선택되는 실용성도 있다(base64url의 `-`/`_`는 단어 경계로 잘림).
2. **`RequestLoggingFilter`** — 요청 시작 시 `req` 마커, 완료 시 `res` 마커로 접근 로그를 남긴다 (`req uri=/api/orders method=POST ip=203.0.113.7` / `res uri=/api/orders method=POST status=201 durationMs=12`). 고정 마커라 grep 한 번으로 방향별 필터링이 된다. 구분자는 쉼표가 아닌 공백(kv 파서가 무설정으로 처리), 단위는 값이 아닌 키 이름에 둔다(`durationMs=12` — 값이 숫자로 남아 수치 쿼리 가능). `ip`는 `getRemoteAddr()` 기준이라 프록시/LB 뒤에서는 프록시 IP가 찍힌다 — 실제 클라이언트 IP가 필요하면 `server.forward-headers-strategy=framework`를 켠다(X-Forwarded-For를 신뢰하는 결정이므로 신뢰할 수 있는 프록시 뒤에서만). 로그 순서는 항상 요청 로그 → 서비스 로그 → 응답 로그다. 본문은 이 클래스의 로거를 DEBUG로 내렸을 때만 기록하며(JSON/텍스트만, 절단 없이 전체), 이때 요청 본문은 `CachedBodyRequest`로 미리 읽어 컨트롤러 실행 전에 로그가 찍히게 한다. `/actuator`, Swagger 경로는 제외한다.

인터셉터를 쓰지 않는 이유: 인터셉터는 Security 통과 후·핸들러 매핑 후에만 실행되므로 401/403/404 요청이 로그에 남지 않고, 본문 스트림도 읽을 수 없다. "서버에 도달한 모든 요청은 흔적을 남긴다"를 만족하는 위치는 필터뿐이다.

로그 메시지는 `method=POST uri=/api/orders status=201` 형식의 key=value로 통일한다. 평소에는 텍스트 한 줄이지만, `logging.structured.format.console=logstash` 한 줄로 전체를 JSON(MDC 포함)으로 전환할 수 있다 — 데이터를 산문이 아닌 키에 담는 것이 전환 가능성의 조건이다.

### 비동기 요청 (2026-10-01 추가)

Callable·DeferredResult·SseEmitter처럼 응답이 나중에 ASYNC 디스패치(다른 스레드)에서 쓰이는 요청을 위해 다음을 둠.

- `RequestMdcFilter`는 ASYNC 디스패치에서도 실행됨. 첫 디스패치가 끝날 때 MDC를 요청 속성에 보관하고 ASYNC 디스패치에서 복원함.
  Spring `ServerHttpObservationFilter`가 관측 객체를 다루는 방식과 같음. 작업 스레드로의 전파는 기존 `MdcTaskDecorator`가 담당함.
- `RequestLoggingFilter`는 비동기가 시작되면 `AsyncListener`를 등록하고 `onComplete`에서 res 로그를 남김.
  정상 응답, 타임아웃, 에러, 연결 끊김 모두 `onComplete`가 호출되므로 접근 로그가 빠지지 않음 (spring-framework#28294에서 메인테이너가 안내한 방식).
  `onComplete`는 MDC가 빈 스레드에서 호출되므로 등록 시점의 MDC를 복원해서 기록함.
- DEBUG의 응답 래퍼는 ASYNC 디스패치에서 찾아 이어 쓰고, 본문은 마지막 디스패치에서만 내보냄 (Spring `ShallowEtagHeaderFilter`와 같은 패턴).
  처음 디스패치에서 내보내면 나중에 쓰인 본문이 래퍼에 갇혀 클라이언트가 빈 응답을 받음(실측 확인).
- 장기적으로 Micrometer Tracing을 도입하면 traceId가 비동기 경계를 프레임워크 수준에서 이어 주므로, 그때 이 방식과 `MdcTaskDecorator`를 재검토함.

## 결과

- 인증 실패를 포함한 모든 요청이 requestId와 함께 기록되어 스레드/요청별 흐름 추적이 가능하다.
- MDC는 ThreadLocal 기반이라 다른 스레드로 자동 전파되지 않는다. `@Async`용 기본 executor에는 `MdcTaskDecorator`가 적용되어 requestId가 승계되며(Boot가 `TaskDecorator` 빈을 자동 반영), 커스텀 executor를 만들 때도 같은 데코레이터를 적용해야 한다. WebFlux/리액티브로 전환한다면 MDC 모델 자체가 맞지 않으므로 context-propagation 재설계와 새 ADR이 필요하다.
- `Authorization` 헤더 등 민감 값은 기록하지 않는다. 본문 로깅(DEBUG)은 로컬 디버깅 전용이며 운영에서 켜지 않는다.
- **`body`는 이스케이프 없이 원문 그대로 기록하며, kv 파싱 계약에서 제외된다.** logfmt 인용(따옴표 + 이스케이프)을 검토했으나 기각했다 — 이스케이프된 본문은 가독성이 나쁘고, 무엇보다 "사용자가 실제로 보낸 그대로"가 아니게 되어 디버깅 시 오해의 원천이 된다. 원문 보존이 body 필드의 존재 이유다. 대신 body는 항상 **마지막 필드**에 두어 앞의 정형 필드(uri, method, ip, status, durationMs — 전부 공백 불포함 보장) 파싱은 항상 성립하게 하고, 수집기에서 body가 필요하면 `body=%{GREEDYDATA}`(마지막 필드라 가능)로 회수한다. 본문까지 온전한 머신 파싱이 필요해지면 structured logging(JSON) 전환으로 해결한다.
- DEBUG 시 요청 본문을 메모리에 통째로 읽으므로 대용량 업로드 엔드포인트가 생기면 크기 상한 또는 경로 제외를 추가해야 한다.
- 핸들러(컨트롤러 메서드) 단위 계측이 필요해지면 인터셉터가 아니라 Micrometer 메트릭으로 해결하고 새 ADR을 남긴다.
