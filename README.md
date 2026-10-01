# skeleton

Spring Boot 4 (Java 21) 기반 REST API 베스트 프랙티스 템플릿. 사람과 AI 에이전트 모두 안전하게 작업할 수 있도록, 중요한 규약은 문서가 아니라 **빌드가 강제**하도록 설계되어 있습니다.

## 기술 스택

- Spring Boot 4.1, Spring Security(무상태 JWT 리소스 서버), Bean Validation
- ArchUnit으로 강제하는 package-by-feature 아키텍처
- 데이터베이스 없음 — 리포지토리는 인터페이스 + 인메모리 구현이며 나중에 교체 가능
- springdoc-openapi(Swagger UI), Actuator 헬스 프로브

## 새 프로젝트 시작하기

이 저장소는 템플릿입니다. 복사한 직후 프로젝트 루트에서 이름을 한 번 치환하세요:

```bash
java scripts/Rename.java --name shop-api --package com.acme.shop
./gradlew clean check
```

| 인자 | 치환 대상 |
|---|---|
| `--name` (kebab-case) | Gradle 프로젝트명, `spring.application.name`, 설정 접두어(`<name>.security.jwt.*`), 로그 파일명, 메인 클래스(`ShopApiApplication`) |
| `--package` | 베이스 패키지와 디렉터리, Gradle `group`(마지막 세그먼트를 뗀 값) |

스크립트는 치환 후 스스로 삭제됩니다. IntelliJ를 열어둔 상태였다면 Gradle 프로젝트를 다시 로드하세요. 치환이 끝나면 이 섹션도 지우면 됩니다.

## 빠른 시작

JDK 21이 필요합니다. `JAVA_HOME`이 JDK 21을 가리키는지 확인하세요.

```bash
./gradlew bootRun          # :8080에서 실행
./gradlew check            # 전체 품질 게이트: 테스트 + 커버리지 + 아키텍처 규칙
```

Swagger UI: <http://localhost:8080/swagger-ui.html> · 헬스 체크: <http://localhost:8080/actuator/health>

### 로컬에서 API 호출하기

모든 `/api/**` 엔드포인트는 `application.yml`의 HS256 시크릿(`skeleton.security.jwt.secret`, `JWT_SECRET` 환경 변수로 재정의)으로 서명된 bearer JWT를 요구합니다. 로컬 실험용 토큰은 [jwt.io](https://jwt.io)에서 HS256 알고리즘 + 개발 시크릿 + 미래 시점의 `exp` 클레임으로 만들거나, `OrderApiIntegrationTest`의 토큰 발급 헬퍼를 복사해 쓰면 됩니다.

```bash
curl -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"productName":"keyboard","quantity":2}' http://localhost:8080/api/orders
```

## 하네스

이 저장소는 검증 루프 자체를 1급 산출물로 취급합니다:

| 계층 | 강제 수단 |
|---|---|
| 포맷 | 강제하지 않음 — `.editorconfig`(인코딩·들여쓰기)와 `docs/CONVENTIONS.md`의 레이아웃 규칙을 따른다 (ADR 0011) |
| 커버리지 | JaCoCo — 클래스당 라인 커버리지 80% |
| 아키텍처 | `ArchitectureTest` (ArchUnit) — 기능 격리, 필드 주입 금지, 계층 규칙 |
| 동작 | 단위 + `@WebMvcTest` 슬라이스 + `@SpringBootTest` 통합 테스트 |
| CI | `.github/workflows/ci.yml`이 로컬과 동일한 `./gradlew check` 실행 |
| 에이전트 가이드 | `CLAUDE.md` + `.claude/skills/new-feature` |

`./gradlew check`가 단일 기준입니다: 로컬에서 통과하면 CI에서도 통과합니다.

## 구조

```
src/main/java/com/example/skeleton/
├── common/          # 횡단 관심사 전용; 기능 패키지에 의존 금지
│   ├── config/      # 보안(401/403 ProblemDetail, CORS), Clock, OpenAPI, 비동기, 타입 안전 설정
│   ├── context/     # MDC 키 관리와 스레드 간 전파
│   ├── error/       # ProblemDetail 매핑 + 공통 예외 베이스
│   ├── filter/      # 서블릿 필터: 요청 MDC, 접근 로그(req/res), 필터 예외→어드바이스 위임, 인증 사용자 MDC
│   └── response/    # PageResponse (목록 응답)
├── ping/            # 공개 스모크 테스트 엔드포인트 (소형 기능은 평면 유지)
└── order/           # 레퍼런스 기능 슬라이스 — 새 기능은 이 구조를 복사
    ├── api/         # 웹 어댑터: 컨트롤러 + dto/
    ├── application/ # 유스케이스: 서비스
    ├── domain/      # 핵심: 모델, 리포지토리 포트, 기능 예외, ErrorCode
    └── storage/     # 영속성 어댑터: 인메모리 구현
```

기능 내부 의존 방향은 `api → application → domain ← storage`이며 ArchUnit이 강제합니다 (ADR 0008).

아키텍처 결정 기록은 [docs/adr](docs/adr)에 있습니다.
