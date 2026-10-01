# ADR 0005: 로깅 구현체는 Logback, 설정은 값/토폴로지 분리

날짜: 2026-09-01 · 상태: 채택

## 배경

로깅 구현체 후보는 Logback과 Log4j2였다 (log4j 1.x는 2015년 EOL로 제외). Log4j2는 비동기 로거(LMAX Disruptor)와 garbage-free 모드로 극단적 처리량에서 우위지만, 이 API의 로그량(요청당 INFO 수 줄)에서는 병목이 로그가 아니라 I/O와 비즈니스 로직이다.

## 결정

Spring Boot 기본값인 **Logback을 유지**한다. 애플리케이션 코드는 SLF4J 파사드로만 로깅한다(구현체 직접 참조는 테스트의 레벨 토글 한 곳뿐).

설정은 두 층으로 나눈다:

- **`application.yml` (`logging.*`)** — 패턴, 레벨, structured 전환 같은 "값". Boot의 기본 include(`defaults.xml`, `console-appender.xml`)를 경유하므로 `logging.structured.format.console=logstash` JSON 전환도 그대로 동작한다.
- **`logback-spring.xml`** — 어펜더 "토폴로지"만. 로컬/테스트는 콘솔 전용, `prod` 프로파일에서만 롤링 파일(매일 + 100MB, gz 압축, 14일/3GB 상한)을 AsyncAppender(큐 1024, 로그 유실 없음)로 추가한다. 파일명이 `logback.xml`이 아닌 `logback-spring.xml`인 것은 `<springProfile>` 등 Spring 확장을 쓰기 위함이다.

## 결과

- 추가 의존성 0, Boot 로깅 기능(프로퍼티, structured logging, 테스트의 OutputCapture)과 마찰 없음.
- 로그 처리량이 실측으로 병목이 되면 그때 `spring-boot-starter-log4j2`로 교체한다. SLF4J 파사드 덕에 애플리케이션 코드는 변경되지 않으며, 교체 시 새 ADR을 남긴다.
- prod 파일 로그는 `logs/`(또는 `LOG_DIR`)에 쌓이며 `.gitignore`에 등록되어 있다.
