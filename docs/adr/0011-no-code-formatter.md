# ADR 0011: 코드 포맷터(Spotless) 제거

날짜: 2026-09-30 · 상태: 채택

## 배경

Spotless(Eclipse JDT)가 `./gradlew check`에서 포맷을 강제했다. 그러나 이 프로젝트의 핵심 레이아웃 규칙인
"레코드 컴포넌트의 애노테이션은 별도 줄"(CONVENTIONS.md 3장)을 Eclipse 포맷터는 표현하지 못한다 — 레코드
컴포넌트 애노테이션을 항상 같은 줄로 당기며, `alignment_for_annotations_on_field/parameter`,
구버전 `insert_new_line_after_annotation_on_*`, Java 21 compliance 지정, 최신 JDT(3.46)까지 모두 효과가
없었다(2026-09-30 실험). 이를 피하려면 `// @formatter:off/on` 태그 주석을 코드에 넣어야 했고, 코드가
지저분해진다는 판단으로 기각했다. 정규식 후처리(`replaceRegex`)는 문맥을 몰라 취약하므로 기각했다.

## 결정

Spotless 플러그인과 `config/spotless/`, 포맷 훅(`.claude/hooks/format-java.ps1`)을 제거한다. 포맷은 빌드가
강제하지 않는다.

- 최소 레이아웃(인코딩, 스페이스 4칸, 줄 길이 160 — `throws` 한 줄 규칙 때문에 2026-10-01 120에서 상향)은 `.editorconfig`로 IDE에 전달한다.
- 레이아웃 규칙은 CONVENTIONS.md 3장이 정본이며 사람과 에이전트가 작성 시 지킨다.

## 결과

- `./gradlew check` = 테스트(ArchUnit 포함) + 커버리지. 포맷 위반으로 빌드가 실패하지 않는다.
- 포맷 일관성은 리뷰 책임이 된다. 미사용 import 제거도 자동으로 되지 않는다(IDE의 Optimize Imports 사용).
- IDE 전체 재포맷(Ctrl+Alt+L)은 레코드 애노테이션 레이아웃을 깰 수 있다 — 레코드 선언에는 쓰지 않는다.
- 레이아웃을 도구로 강제할 필요가 다시 생기면, 레코드 애노테이션 규칙을 표현할 수 있는 포맷터가 있는지부터
  확인하고 새 ADR로 결정한다.
