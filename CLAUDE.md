# skeleton

Spring Boot 4 / Java 21 REST API 템플릿. Package-by-feature, 무상태 JWT 리소스 서버, 영속성 계층 없음(인터페이스 뒤의 인메모리 리포지토리).

## 명령어

`JAVA_HOME`이 JDK 21을 가리켜야 한다 (이 PC: `%USERPROFILE%\.jdks\temurin-21.0.7`).

- `./gradlew check` — 전체 품질 게이트: 테스트(ArchUnit 포함) + 커버리지(클래스당 라인 80%). **모든 작업의 완료 기준.**
- `./gradlew test --tests "com.example.skeleton.order.*"` — 작업 중 대상 테스트만 실행
- `./gradlew bootRun` — :8080에서 실행 (Swagger UI: `/swagger-ui.html`)

## 아키텍처

- **Package-by-feature**: `com/example/skeleton/<feature>/`가 기능의 전부를 담고, 내부는 4개 레이어로 나눈다 — `api/`(컨트롤러, DTO는 `api/dto/`), `application/`(서비스), `domain/`(모델·리포지토리 포트, 실패 어휘는 `domain/error/`에 예외+ErrorCode), `storage/`(JPA 어댑터: `<Feature>Entity`+`<Feature>JpaRepository`+`Jpa<Feature>RepositoryAdapter` — ADR 0009. `@Entity`는 storage의 사유물, 스키마 원본은 `schema.sql`, 테스트 픽스처 `InMemory<Feature>Repository`는 src/test에). 의존 방향은 `api → application → domain ← storage`로 ArchUnit이 강제한다 (ADR 0008). 소형 기능(파일 2~3개)은 평면 유지 가능.
- `common/`은 횡단 관심사(설정, 에러 처리) 전용. `common`은 절대 기능 패키지에 의존하지 않고, 기능 패키지끼리도 서로 의존하지 않는다.
- 이 규칙은 `ArchitectureTest`가 강제한다. 실패하면 코드를 고쳐라 — ADR 추가 없이 규칙을 완화하지 마라.
- `order/` 패키지가 레퍼런스 구현이다. 새 기능은 그 구조를 그대로 따른다 (`new-feature` 스킬 참고).

## 컨벤션

- DTO와 도메인 모델은 record. 도메인 상태 전이는 새 인스턴스를 반환하며, 불법 전이는 서비스가 아니라 도메인 타입 안에서 거부한다.
- 응답에 봉투(`{code, message, data}`) 금지 — 성공은 리소스 DTO 그 자체, 실패는 ProblemDetail. 목록은 `common.response.PageResponse`로 감싼다 (최상위 배열 금지, ADR 0007).
- 에러: `common.error.ResourceNotFoundException`(404), `ConflictException`(409), `UnprocessableException`(422, 정책 위반) 중 하나를 상속한 기능 예외를 던진다. 규칙마다 클래스를 만들지 마라 — 클래스는 서버 행동 단위, 코드는 클라이언트 식별 단위다 (`OrderPolicyException` 참고). 에러 코드는 기능 패키지의 `<Feature>ErrorCode` enum(`ErrorCode` 구현)에 `<기능접두어>_<의미>` 형식으로 추가하고, `messages.properties`와 `messages_ko.properties` 양쪽에 `error.<코드>` 메시지를 등록한다 (누락 시 `ErrorCodeMessageBundleTest` 실패). 응답 `detail`은 Accept-Language를 따르지만 예외의 로그 메시지는 영어 고정이다. 임의의 에러 응답 형태 금지, `ResponseStatusException` 금지 (ADR 0006). ProblemDetail을 만드는 곳은 `GlobalExceptionHandler` 하나 — 필터·Security 등 컨트롤러 밖의 실패도 `handlerExceptionResolver`로 위임한다 (ADR 0012).
- 생성자 주입만 사용 — `@Autowired` 필드 금지 (ArchUnit이 강제).
- `Clock`을 주입받아 사용하고, `Instant.now()`를 직접 호출하지 마라.
- 다른 패키지에서 필요하지 않으면 클래스는 package-private으로 유지한다.
- 보안: 새 엔드포인트는 기본적으로 인증 필요. `SecurityConfig`의 `PUBLIC_PATHS`에 추가하려면 명시적 근거가 필요하다.
- 설정은 생성자 검증이 있는 `@ConfigurationProperties` record로 (`JwtProperties` 참고).

## 로깅

- 요청/응답 접근 로그는 `common/logging`의 필터가 담당한다. 컨트롤러나 서비스에서 요청/응답을 다시 로깅하지 마라.
- 로그 메시지는 `order.created id={} quantity={}` 같은 **key=value 형식**으로 쓴다. 산문 금지 — 수집기 파싱 가능성이 기준이다. 예외는 본문(body): 원문 보존과 가독성을 위해 이스케이프 없이 그대로 기록하되, kv 계약에서 제외되므로 반드시 라인의 **마지막 필드**에 둔다 (ADR 0004).
- requestId는 `RequestMdcFilter`가 MDC로 자동 부착한다(요청 메타데이터·MDC 정리도 이 필터 담당). 로그 메시지에 수동으로 넣지 마라.
- `@Async`는 기본 executor를 사용하라 — `MdcTaskDecorator`가 적용되어 있어 requestId가 승계된다. 커스텀 executor를 만들면 반드시 `setTaskDecorator(new MdcTaskDecorator())`를 적용하라 (`AsyncMdcPropagationTest` 참고).
- 토큰, 비밀번호, 개인정보를 로그에 남기지 마라. 본문 로깅은 DEBUG 전용이며 운영에서 켜지 않는다.
- 서비스 레이어는 상태 변경(생성/전이/삭제)만 INFO로 남기고, 조회는 로깅하지 않는다.
- 설정 배치: 공통 값은 `application.yml`, 프로파일별 값(레벨·JSON 전환 등)은 `application-<profile>.yml`, 어펜더 구조는 `logback-spring.xml`. 기본 프로파일은 `local`이다.

## 검증 루프

테스트는 세 단계로 존재한다 — 새 기능은 `order`의 패턴을 복사하라:

1. 단위: 도메인 + 서비스를 실제 인메모리 리포지토리와 고정 `Clock`으로 테스트 (mock 없음).
2. 웹 슬라이스: `@WebMvcTest`에 실제 `SecurityConfig`를 import하고 `jwt()` post-processor로 인증.
3. 통합: `@SpringBootTest`에서 실제 HS256 토큰을 서명해 디코더 배선까지 검증.

## 코딩 컨벤션

문서화된 판단 규칙은 `docs/CONVENTIONS.md`를 따른다. 코드를 작성·리뷰하기 전에 해당 섹션을 확인하라.

## 의사결정

아키텍처 결정은 `docs/adr/`에 기록한다. 구조, 의존성, 보안 자세를 바꿀 때는 ADR을 추가하라.
