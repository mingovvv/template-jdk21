# ADR 0006: 예외 설계 — 계층 구조 + 기능별 ErrorCode enum + 메시지 국제화

날짜: 2026-09-03 · 상태: 채택

## 배경

API 에러 표현의 유력 패턴들을 비교했다.

- **A. 예외 계층 구조**: HTTP 의미론별 추상 베이스(`ResourceNotFoundException`→404, `ConflictException`→409)를 두고 기능 예외가 자기 패키지에서 상속.
- **B. 단일 `ApplicationException` + 중앙 `ErrorCode` enum**: 케이스마다 enum 상수를 등록하고 하나의 예외 클래스로 던지는 방식.
- **C. `AUTH-001` 스타일**: 접두어 + 일련번호 코드에 메시지를 enum에 하드코딩하고, Success 코드까지 두는 엔터프라이즈 전통 스타일.

B는 타입 시스템 반납(다형적 catch 불가, 스택트레이스가 전부 같은 클래스명)과 중앙 enum이 모든 기능 지식을 흡수하는 문제(ADR 0001 위반)로 기각. C는 요소별로 분해해 **접두어 그룹핑만 승계**하고 나머지는 기각했다 — 일련번호는 대조표 없이는 무의미해 의미 있는 이름의 하위 호환이고(Stripe/AWS 등 주요 API도 의미 코드 사용), enum 내 메시지는 국제화 번들과 원본 이원화를 낳으며, Success 코드는 REST에서 HTTP 2xx와 채널 중복이다.

## 결정

**계층 구조 + "계약은 common, 소유는 기능" 분할 + 번들 기반 국제화.**

1. **베이스 예외는 추상 클래스** — 어드바이스가 다형적으로 잡는 분류 체계이며, 추상 선언이 "모든 실패에 이름 있는 기능 예외를 만들라"를 컴파일러 수준에서 강제한다. 모든 예외는 unchecked이고 처리 지점은 경계(어드바이스) 한 곳이다. 베이스는 세 가지: `ResourceNotFoundException`(404), `ConflictException`(409, 리소스의 현재 상태와 충돌), `UnprocessableException`(422, 상태와 무관한 비즈니스 정책 위반).
   - 클래스와 코드의 입도 원칙: **클래스 = 서버 행동의 단위, 코드 = 클라이언트 식별의 단위.** 규칙마다 예외 클래스를 만들지 않는다 — 서버 행동이 동일한 정책 위반들은 기능당 하나의 정책 예외(예: `OrderPolicyException`)가 코드만 달리해 담당하고, 별도 클래스는 서버가 다르게 행동해야 할 때만 만든다(예: 멱등 처리 후보인 `DuplicateOrderException`).
2. **에러 코드는 `ErrorCode` 인터페이스(common) + 기능별 enum(기능 패키지)** — `OrderErrorCode`는 `order`에 산다. `code()`는 enum 상수명을 그대로 쓰므로 문자열 리터럴이 존재하지 않는다. 형식은 `<기능접두어>_<의미>` SCREAMING_SNAKE_CASE(예: `ORDER_NOT_FOUND`), 일련번호 금지. 횡단 코드(`VALIDATION_FAILED` 등)만 common의 `CommonErrorCode`가 소유한다.
3. **메시지는 번들이 단일 소유** — `error.<코드>` 키 규약으로 `messages.properties`(영어 폴백)와 `messages_ko.properties`에 등록한다. 어드바이스가 Accept-Language 로케일(`LocaleContextHolder`)로 detail을 해석하고, 예외 생성 시 `messageArgs`로 치환 인자를 전달한다.
4. **로그는 국제화하지 않는다** — 예외의 `getMessage()`(스택트레이스/로그)는 영어 고정. 같은 장애가 로케일마다 다른 문자열로 남으면 grep이 불가능해진다. 번역되는 채널은 응답 `detail`뿐이며, 기계 채널(`code`)은 로케일 무관.
5. **드리프트는 하네스가 잡는다** — `ErrorCodeMessageBundleTest`가 클래스패스에서 모든 `ErrorCode` 구현을 스캔해 전 번들에 키가 있는지, 코드 형식 규약을 지키는지 검증한다. 번들 누락 = 빌드 실패.

응답 예:

```json
// Accept-Language: ko
{ "status": 404, "code": "ORDER_NOT_FOUND", "detail": "주문을 찾을 수 없습니다: 3f2a..." }
// Accept-Language: en (또는 미지정 시 폴백)
{ "status": 404, "code": "ORDER_NOT_FOUND", "detail": "Order not found: 3f2a..." }
```

## 결과

- 클라이언트는 `code`로 분기하고 `detail`은 표시용이다. 메시지는 번들 수정만으로 개선/추가 번역이 가능하다.
- 기능 추가 시 할 일: 기능 enum에 상수 1줄 + 번들 2곳 등록(잊으면 테스트가 강제) + 예외 클래스. `ResponseStatusException` 직접 던지기는 금지.
- Success 코드는 두지 않는다 — 성공은 HTTP 2xx가 말한다.
- sealed 결과 타입은 다중 실패 분기가 복잡한 유스케이스가 생기면 재검토한다.
