---
name: new-feature
description: order 패키지 레퍼런스 구현을 따라 새 기능 슬라이스(도메인, 리포지토리, 서비스, 컨트롤러, 테스트)를 추가한다
---

# 새 기능 슬라이스 추가

`order` 패키지를 거울삼아 `src/main/java/com/example/skeleton/<feature>/` 패키지를 새로 만든다. 내부는 4개 레이어 서브패키지로 나눈다 — `api/`(컨트롤러+DTO), `application/`(서비스), `domain/`(모델·포트·예외·ErrorCode), `storage/`(저장소 구현). 의존 방향 `api → application → domain ← storage`는 ArchUnit이 강제한다 (ADR 0008; 파일 2~3개짜리 소형 기능은 평면 유지 가능). 각 단계가 검증 가능하도록 안쪽(도메인)에서 바깥쪽(컨트롤러) 순서로 작업한다.

## 단계

1. **도메인 모델** (`domain/`) — 정적 팩토리와 상태 전이 메서드를 가진 불변 record. 불법 전이는 도메인 안에서 거부한다 (`Order.java` 참고). 라이프사이클이 있으면 상태 enum을 추가한다.
2. **기능 예외** (`domain/error/`) — `<Feature>ErrorCode` enum(`common.error.ErrorCode` 구현, 패키지 내 소유)을 만들고, `common.error.ResourceNotFoundException`(404), `ConflictException`(409), `UnprocessableException`(422) 중 하나를 상속한 예외가 그 코드를 사용한다 (`OrderErrorCode` 참고). 규칙마다 클래스를 만들지 말 것 — 서버 행동이 같으면 정책 예외 하나가 코드만 달리해 담당한다 (`OrderPolicyException` 참고). 코드 형식은 `<기능접두어>_<의미>` (예: `PRODUCT_NOT_FOUND`). `messages.properties`와 `messages_ko.properties`에 `error.<코드>` 메시지를 등록한다 — 잊으면 `ErrorCodeMessageBundleTest`가 실패한다. 새로운 에러 응답 형태를 발명하지 마라.
3. **리포지토리** — 포트 인터페이스는 `domain/`에(도메인이 소유), `storage/`에는 JPA 3형제(`<Feature>Entity`, `<Feature>JpaRepository`, `Jpa<Feature>RepositoryAdapter` — `order/storage` 참고)를 만들고 `schema.sql`에 테이블을 추가한다. `src/test`의 storage 패키지에 테스트 픽스처 `InMemory<Feature>Repository`도 만들고, 포트 계약 테스트(`OrderRepositoryContractTest` 패턴)를 두 구현에 적용한다.
4. **서비스** (`application/`) — 리포지토리 포트와 `Clock`을 생성자 주입. 비즈니스 오케스트레이션만 담당하고, 불변식은 도메인 모델에 둔다. storage를 import하지 마라.
5. **DTO** (`api/dto/`) — Bean Validation 애노테이션이 붙은 `<Feature>CreateRequest`, 정적 `from(domain)` 팩토리를 가진 `<Feature>Response`. req/res로 다시 나누지 않는다.
6. **컨트롤러** (`api/`) — `/api/<features>` 하위의 package-private `@RestController`. POST는 Location 헤더와 함께 201, DELETE는 204를 반환한다. 목록 조회는 최상위 배열이 아니라 `common.response.PageResponse`로 감싼다.
7. **테스트 3단계** (order의 테스트 클래스를 템플릿으로 복사):
   - `<Feature>Test` + `<Feature>ServiceTest`: 실제 인메모리 리포지토리 + 고정 `Clock`, mock 없음.
   - `<Feature>ControllerTest`: `@WebMvcTest` + `@Import(SecurityConfig.class)` + `jwt()` post-processor. 401, 201, 400 검증 실패, 404, (해당되면) 409를 커버한다.
   - 엔드투엔드 흐름이 필요하면 `OrderApiIntegrationTest`를 확장하거나 형제 클래스를 추가한다.

## 완료 기준

- `./gradlew check` 통과: 테스트 그린, 클래스당 라인 커버리지 80% 이상, ArchUnit 규칙 만족. 레이아웃은 `docs/CONVENTIONS.md` 3장을 따른다(포맷터 없음).
- 사용자가 명시적으로 공개 엔드포인트를 요청하지 않았다면 `SecurityConfig.PUBLIC_PATHS` 변경 금지.
- order 패턴에서 벗어났다면 그 이유를 `docs/adr/`에 기록한다.
