# ADR 0003: HS256 공유 시크릿 기반 무상태 JWT 리소스 서버

날짜: 2026-08-28 · 상태: 채택

## 배경

이 API는 표준 Spring Security 리소스 서버 모델을 따르면서도, 외부 인증 서버(IdP) 없이 단독으로 기동할 수 있는 인증이 필요하다.

## 결정

Spring Security OAuth2 Resource Server를 사용하고, HS256 공유 시크릿(`skeleton.security.jwt.secret`, 최소 32바이트, `JwtProperties`가 기동 시 검증)으로 만든 `NimbusJwtDecoder`로 토큰을 검증한다. 모든 엔드포인트는 기본적으로 인증을 요구하며, health/info와 OpenAPI 문서만 공개한다.

## 결과

- 로컬 개발과 테스트에 외부 IdP가 필요 없다. 통합 테스트는 동일한 시크릿으로 실제 토큰을 서명해 디코더 배선까지 검증한다.
- 실제 IdP로 전환할 때는 `JwtDecoder` 빈을 `issuer-uri`/JWKS 설정으로 교체하면 되고, 필터 체인은 그대로다.
- 대칭 시크릿은 이를 아는 누구나 토큰을 발급할 수 있다는 뜻이다. 템플릿 기본값으로는 허용되지만, 운영 배포에서는 반드시 `JWT_SECRET`을 재정의해야 하며 비대칭 키(RS256 + JWKS)를 권장한다. 전환 시 새 ADR로 기록한다.
