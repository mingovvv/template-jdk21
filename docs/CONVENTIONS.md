# 코딩 컨벤션

> 사람(팀원)과 AI 에이전트가 함께 읽는 규범 문서다. 코드 작성·리뷰 시 이 문서를 따른다.

## 이 문서를 쓰는 법 (규칙을 추가하기 전에 읽을 것)

1. **기계가 강제할 수 있는 규칙은 여기에 적지 않는다.** 구조는 ArchUnit, 계약은 테스트로
   보낸다 — 문서의 규칙은 어겨지지만 빌드 게이트의 규칙은 못 어긴다. 여기엔 도구가 판정할 수 없는
   **판단의 규칙**만 남긴다. (강제로 옮긴 규칙은 여기서 지우고 링크만 남길 것)
2. **모든 규칙은 3요소로 쓴다**: 규칙(한 문장, 명령형) / 이유(왜 — 없으면 규칙이 아니라 취향이다) /
   예시(좋음·나쁨 코드 한 쌍). 이유를 못 쓰겠으면 등록하지 않는다.
3. 규칙끼리 충돌하면 이 문서보다 ADR(docs/adr/)이 이긴다. 아키텍처 결정은 ADR에, 스타일 판단은 여기에.

<!-- 아래 각 섹션의 "예시" 항목은 작성 형식을 보여주는 견본이다. 지우고 팀의 규칙으로 채울 것. -->

---

## 1. 명명 (Naming)

| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스 | _(예시)_ 역할 접미사를 붙인다: `~Service`, `~Adapter`, `~Filter` | `JpaOrderRepositoryAdapter` |
| 메서드 | | |
| 변수 | | |
| 상수 | | |
| DB 테이블/컬럼 | | |
| 약어 처리 | _(예시)_ 약어도 카멜케이스로: `apiUrl` (O), `APIURL` (X) | |

**금지 명명 목록**: <!-- 예: Util, Manager, Helper 같은 무의미 접미사 / data, info, temp 같은 무정보 변수명 -->

## 2. 메서드/클래스 설계

<!-- 도구가 못 잡는 크기·복잡도 판단 기준. 예시 형식: -->

- **규칙**: _(예시)_ 메서드는 한 화면(~30줄)을 넘기지 않는다.
  - **이유**: 스크롤 없이 읽을 수 없는 메서드는 둘 이상의 책임을 갖고 있을 확률이 높다.
  - **예외**: 단순 매핑/빌더 나열은 허용.
- **규칙**:
  - **이유**:

## 3. 자바 관용구

<!-- 언어 기능의 사용 기준. 예: record vs class, Optional 반환 규칙, 스트림 vs for, var 사용 범위, Lombok 허용 범위 -->

- **규칙**: record 선언의 컴포넌트(필드)는 한 줄에 하나씩 개행한다 — 한 줄에 다 들어가도 나눈다.
  컴포넌트에 **애노테이션이 붙으면 애노테이션도 필드 위 별도 줄**에 둔다.
  - **이유**: 필드 추가/변경이 diff에서 정확히 한 줄로 보이고, 애노테이션이 길어져도 필드명이 항상 같은
    위치에서 읽힌다.
  - **비고**: 이 레이아웃은 도구로 강제되지 않는다 — 포맷터(Eclipse JDT)가 레코드 컴포넌트의 애노테이션을
    같은 줄로 당기므로 포맷터를 두지 않기로 했다 (ADR 0011). IDE의 전체 재포맷(Ctrl+Alt+L)도 같은 이유로
    이 레이아웃을 깰 수 있으니 레코드 선언에는 쓰지 않는다. `@formatter:off` 같은 도구 지시 주석은 쓰지 않는다.
  - **예시** (`OrderCreateRequest` 참고):
    ```java
    public record OrderCreateRequest(

            @NotBlank(message = "productName is required")
            String productName,

            @Positive(message = "quantity must be positive")
            int quantity

    ) {
    }
    ```
- **규칙**: 메서드 선언의 `throws` 절은 시그니처와 같은 줄에 붙인다 — 다음 줄로 넘기지 않는다. 파라미터가 길어
  여러 줄로 나뉘면 닫는 괄호가 있는 마지막 줄에 붙인다. 줄 길이 상한은 160자(`.editorconfig`)다.
  - **이유**: 이름·파라미터·예외로 이루어진 시그니처가 한 덩어리로 읽힌다. 들여쓴 `throws` 줄은 본문 첫 줄처럼
    보여 선언의 끝을 놓치기 쉽고, 시그니처를 grep할 때도 한 줄에 전부 걸려야 결과가 온전하다.
  - **예시**:
    ```java
    // 좋음
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    // 파라미터가 여러 줄이면 마지막 줄에
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) throws Exception {

    // 나쁨
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
    ```
- **규칙**: 로거는 Lombok `@Slf4j`로 선언한다 — `private static final Logger log = LoggerFactory.getLogger(...)`를 직접 쓰지 않는다.
  - **이유**: 선언 보일러플레이트가 사라지고, 다른 클래스를 복사해 오면서 `getLogger(다른클래스.class)`를 그대로 두는
    실수(로그가 엉뚱한 로거 이름으로 찍힘)가 원천적으로 없어진다.
  - **예외**: 테스트에서 로그 레벨을 바꾸려고 Logback 로거를 가져오는 코드(`(Logger) LoggerFactory.getLogger(...)`)는 선언이 아니므로 허용한다.
- **규칙**: 메시지 문자열은 `+` 연결 대신 `"...%s...".formatted(값)`으로 만든다. 단, **로그 문장은 SLF4J 자리표시자
  `{}`를 쓴다** (`log.info("order.created id={}", id)`).
  - **이유**: 템플릿이 한 덩어리로 보여 메시지의 최종 모양을 바로 읽을 수 있고, `+` 사이의 공백·구두점 누락 실수가 줄어든다.
    로그는 `{}`가 레벨이 꺼져 있으면 문자열을 아예 만들지 않으므로(지연 평가) `.formatted()`보다 낫다.
  - **예외**: 템플릿이 아닌 단순 결합(패딩 `"0".repeat(n) + id` 등)은 `+`가 더 읽기 쉽다.
  - **예시**:
    ```java
    // 좋음
    throw new IllegalArgumentException("secret must be at least %d bytes".formatted(MIN_SECRET_LENGTH));
    log.warn("upstream.failed status={} type={}", status, type);

    // 나쁨
    throw new IllegalArgumentException("secret must be at least " + MIN_SECRET_LENGTH + " bytes");
    log.warn("upstream.failed status=%s".formatted(status));
    ```
- **규칙**: HTTP 메서드는 문자열 대신 Spring의 `HttpMethod` 상수로 비교한다 (`HttpMethod.POST.matches(request.getMethod())`).
  - **이유**: `"POST"`, `"Post"`, `"POTS"`처럼 문자열 오타·대소문자 실수는 컴파일러가 못 잡고 조용히 `false`가 된다.
    상수는 오타가 컴파일 에러로 드러나고, IDE에서 사용처를 추적할 수 있다.
  - **예외**: 애노테이션 속성(`@DefaultValue({"GET", "POST"})` 등)은 컴파일 시점 상수만 받으므로 문자열을 쓴다.
    `HttpMethod`는 Spring 6부터 enum이 아닌 클래스라 `.name()`도 애노테이션에 넣을 수 없다.
  - **예시**:
    ```java
    // 좋음
    HttpMethod.POST.matches(request.getMethod())

    // 나쁨
    "POST".equals(request.getMethod())
    ```
- **규칙**: record 인스턴스 생성 시 컴포넌트가 3개 이상이면 `@Builder`(Lombok)를 붙이고 빌더로 조립한다.
  2개 이하이면 `new`를 그대로 쓴다. 정적 팩터리(`from`, `of` 등)의 내부 구현도 동일 기준을 따른다.
  - **이유**: 같은 타입의 인자가 3개 이상 나열되면 순서 실수를 컴파일러가 못 잡는다(String 두 개가 뒤바뀌어도
    통과). 빌더는 이름으로 바인딩되어 이 오류 부류를 제거하고, 필드 추가 시 호출부 수정도 국소화된다.
  - **예시**: `OrderResponse.from()`, `Order.create()`, `PageResponse.all()` 참고. 상태 전이처럼 일부 필드만
    바꾸는 복사는 `@Builder(toBuilder = true)` + `toBuilder()`를 쓴다 (`Order.withStatus` 참고).
- **규칙**: _(예시)_ `Optional`은 반환 타입에만 쓴다 — 필드·매개변수에 쓰지 않는다.
  - **이유**: 필드/매개변수의 Optional은 "null일 수도 있는 Optional"이라는 이중 부정을 만든다.
- **규칙**:
  - **이유**:

## 4. 주석과 문서화

<!-- 언제 주석을 쓰고 언제 안 쓰는가, Javadoc 의무 범위, 언어(한글/영어) 기준 -->

- **규칙**: _(예시)_ 주석은 "왜"만 쓴다. "무엇"은 코드가 말하게 한다.
  - **이유**: 무엇-주석은 코드 수정 시 함께 갱신되지 않아 거짓말이 된다.
- **규칙**:
  - **이유**:

## 5. 예외·에러 처리

> 구조 규칙은 [ADR 0006](adr/0006-exception-design.md)이 정본: 베이스 3종(404/409/422) 상속,
> ErrorCode enum, 메시지 번들. 위반은 테스트가 잡는다. 아래에는 그 위의 판단 규칙만 적는다.

- **규칙**:
  - **이유**:

## 6. 로깅

> 구조 규칙은 [ADR 0004](adr/0004-logging-filter.md)가 정본: key=value, req/res 마커, MDC requestId.
> 아래에는 "무엇을 어느 레벨로 남기는가"류의 판단 규칙만 적는다.

- **규칙**:
  - **이유**:

## 7. 테스트

<!-- 테스트 명명 규칙, given-when-then 여부, 픽스처 작성법, 무엇을 테스트하지 않는가 -->

- **테스트 메서드 명명**: _(예시)_ `동작조건과결과를문장으로` — `cancelCancelledOrderThrowsConflict`
- **규칙**:
  - **이유**:

## 8. API 설계

> 응답 형태는 [ADR 0007](adr/0007-response-format.md)이 정본(봉투 금지, 목록은 PageResponse).
> 아래에는 URL 설계·필드 명명 등 판단 규칙을 적는다.

| 항목 | 규칙 | 예 |
|---|---|---|
| URL 경로 | _(예시)_ 복수형 명사, 동사 금지 | `/api/orders` (O), `/api/getOrder` (X) |
| 쿼리 파라미터 명명 | | |
| 날짜/시간 형식 | | |
| 페이징 파라미터 | | |

- **규칙**: 비동기 엔드포인트(`Callable`, `DeferredResult`, `SseEmitter` 등을 반환)는 타임아웃을 명시함.
  `Callable`은 `WebAsyncTask`로 감싸 타임아웃을 주고, `DeferredResult`·`SseEmitter`는 생성자에 넘김.
  타임아웃 시 돌려줄 결과가 따로 있으면 `onTimeout` 콜백으로 정함.
  - **이유**: 지정하지 않으면 전역 기본값(`spring.mvc.async.request-timeout`, 30초)에 묶여 엔드포인트 성격과 맞지 않음.
    `onTimeout`으로 결과를 정해 두면 Spring이 작업 스레드를 인터럽트할 때 생기는 예외와 경쟁하지 않고 응답이 하나로 정해짐.
    어드바이스는 타임아웃과 `InterruptedException`을 모두 503으로 처리하지만, 엔드포인트별 의도는 엔드포인트가 정하는 것이 맞음.
  - **예시**:
    ```java
    // 좋음
    @GetMapping("/report")
    WebAsyncTask<Report> report() {
        return new WebAsyncTask<>(10_000L, reportService::generate);
    }

    // 나쁨 (타임아웃이 전역 기본값에 묶임)
    @GetMapping("/report")
    Callable<Report> report() {
        return reportService::generate;
    }
    ```

## 9. Git / PR

| 항목 | 규칙 |
|---|---|
| 브랜치 명명 | |
| 커밋 메시지 형식 | |
| PR 크기 기준 | |
| 리뷰 승인 기준 | |

## 10. 리뷰 체크리스트

<!-- 리뷰어(사람·AI 공용)가 기계적으로 훑는 목록. 하네스가 이미 잡는 항목은 넣지 않는다. -->

- [ ] _(예시)_ 새 공개 메서드에 이유 없는 `public`이 없는가 (package-private로 충분하지 않은가)
- [ ]
- [ ]

---

## 규칙의 승격 이력

<!-- 문서 규칙이 하네스(도구 강제)로 승격되면 여기에 기록하고 본문에서 삭제한다.
     예: 2026-09-07 "필드 주입 금지" → ArchUnit no_field_injection 규칙으로 승격 -->

| 날짜 | 규칙 | 승격된 곳 |
|---|---|---|
| 2026-09-07 | record 필드 개행을 포맷터가 되돌리지 않게 | Spotless 포맷터를 Palantir → Eclipse JDT(`join_wrapped_lines=false`)로 교체 (`config/spotless/eclipse-format.xml`) — **2026-09-30 철회** (ADR 0011) |
| 2026-09-07 | 애노테이션 별도 줄 레이아웃 보호 | `use_on_off_tags` 활성화 — `@formatter:off/on` 구간은 포맷터가 건드리지 않음. `formatAnnotations()` 스텝은 충돌로 제거 — **2026-09-30 철회** (ADR 0011) |
| | | |
