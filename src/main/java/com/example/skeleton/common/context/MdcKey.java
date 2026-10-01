package com.example.skeleton.common.context;

import java.util.EnumMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * MDC에 들어가는 모든 키의 단일 관리 지점.
 * 키 문자열과 MDC 접근을 여기에 캡슐화하므로 호출부는 문자열 리터럴이나 {@link MDC} API를 직접 만지지 않음.
 * 인프라 성격인 {@code MdcTaskDecorator}만 예외임.
 *
 * 텍스트 패턴 키({@code inTextPattern=true})는 로그 라인을 서로 잇는 상관 키라 모든 텍스트 로그 라인에 찍힘.
 * 추가/변경 시 로그 패턴(application.yml의 console 패턴, logback-spring.xml의 파일 패턴)도 함께 갱신해야 함.
 * 패턴과 어긋나면 {@code MdcKeyLogPatternTest}가 잡음.
 *
 * 구조화 전용 키({@code inTextPattern=false})는 요청 메타데이터임.
 * 텍스트 로그에서는 req/res 접근 로그에 이미 있으므로 라인마다 반복하지 않음.
 * JSON 로그로 전환하면 필드로 자동 포함됨.
 *
 * 값은 한 줄짜리 짧은 식별 정보만 넣음. 토큰, 쿼리스트링, 헤더 원문, 개인정보는 넣지 않음.
 */
public enum MdcKey {
    REQUEST_ID("requestId", true),
    USER_ID("userId", true),
    METHOD("method", false),
    URI("uri", false),
    CLIENT_IP("clientIp", false);

    private final String key;
    private final boolean inTextPattern;

    MdcKey(String key, boolean inTextPattern) {
        this.key = key;
        this.inTextPattern = inTextPattern;
    }

    public String key() {
        return key;
    }

    public boolean inTextPattern() {
        return inTextPattern;
    }

    public void put(String value) {
        MDC.put(key, value);
    }

    public String get() {
        return MDC.get(key);
    }

    public void remove() {
        MDC.remove(key);
    }

    /** 요청 종료 시 모든 키를 지움. 요청 스레드는 풀에서 재사용되므로 남기면 다음 요청 로그에 섞임. */
    public static void removeAll() {
        for (MdcKey mdcKey : values()) {
            mdcKey.remove();
        }
    }

    /**
     * 현재 스레드의 MDC 중 이 enum이 관리하는 키만 복사함.
     * 비동기 요청은 처리 도중 스레드가 바뀌므로, 다른 스레드에서 같은 값을 이어 쓰려고 보관할 때 사용.
     */
    public static Map<MdcKey, String> snapshot() {
        Map<MdcKey, String> snapshot = new EnumMap<>(MdcKey.class);
        for (MdcKey mdcKey : values()) {
            String value = mdcKey.get();
            if (value != null) {
                snapshot.put(mdcKey, value);
            }
        }
        return snapshot;
    }

    public static void restore(Map<MdcKey, String> snapshot) {
        snapshot.forEach(MdcKey::put);
    }

}
