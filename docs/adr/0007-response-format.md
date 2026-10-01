# ADR 0007: 응답 포맷 — 봉투 없음, 목록은 PageResponse

날짜: 2026-09-03 · 상태: 채택

## 배경

성공/실패 응답 바디의 형태를 정해야 한다. 후보는 `{code, message, data}` 봉투(envelope) 방식과, 봉투 없이 성공은 리소스 그 자체·실패는 RFC 9457 ProblemDetail을 내리는 방식.

## 결정

**봉투를 두지 않는다.** 성공/실패의 구분은 HTTP 상태 코드와 Content-Type(`application/problem+json`)이 담당한다.

- 성공: 리소스 DTO가 곧 바디. 생성 201+Location, 삭제 204, 그 외 200.
- 실패: 전부 ProblemDetail + `code` 확장 필드 (ADR 0006).
- 봉투 기각 이유: HTTP가 이미 하는 일(성공/실패 판정)을 바디에 중복시켜 이중 채널 모순("200인데 code=FAIL")을 만들고, 상태 코드 기반 인프라(게이트웨이·모니터링·재시도)와 어긋나며, 모든 호출부에 언랩 비용을 강제한다. 클라이언트의 실패 타입은 API 전체에 ProblemDetail 하나면 되고, 서버 간 호출에서 실패는 반환값이 아니라 예외 채널(에러 디코더에서 `code` 기반 번역)로 소비한다.

**단, 목록 응답은 `common.response.PageResponse<T>`로 감싼다 — 최상위 배열 금지.**

- 최상위 `[]`는 필드를 추가할 수 없어 페이지네이션 메타 도입이 파괴적 변경이 된다. 클라이언트가 없는 지금이 이 결정의 무비용 시점이다.
- 이것은 봉투 회귀가 아니다. `page`/`totalElements`/`hasNext`는 프로토콜 중복이 아니라 "컬렉션"이라는 리소스 자체의 도메인 데이터다.
- 필드는 6개로 절제한다: `items`, `page`(1부터), `size`, `totalElements`, `totalPages`, `hasNext`. `isFirst`/`hasPrevious`/`isLast`/`numberOfElements`는 파생 가능한 중복이라 두지 않는다.
- 페이징 도입 전에는 `PageResponse.all(items)`(단일 페이지), 도입 후에는 `of()`를 쓴다. Spring Data를 도입하면 `Page` 어댑터 팩토리를 추가한다.

## 결과

- Success 코드/래퍼가 없으므로 클라이언트 호출부는 성공 타입만 다루고, 실패 처리는 인터셉터/에러 디코더 한 곳에 모인다.
- 목록 엔드포인트는 처음부터 확장 가능한 형태다. 페이지네이션 추가가 기존 클라이언트를 깨지 않는다.
