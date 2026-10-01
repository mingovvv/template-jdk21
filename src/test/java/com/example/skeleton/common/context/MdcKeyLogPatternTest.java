package com.example.skeleton.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 코드(MdcKey)와 설정(로그 패턴) 사이의 암묵적 계약을 명시화함.
 * MdcKey의 키가 로그 패턴의 {@code %X{...}}에 빠져 있으면 그 값이 로그에서 조용히 사라지므로, 드리프트를 빌드 실패로 바꿈.
 */
class MdcKeyLogPatternTest {

    @Test
    void consolePatternContainsEveryMdcKey() throws Exception {
        String applicationYml = readClasspathFile("application.yml");

        for (MdcKey key : textPatternKeys()) {
            assertThat(applicationYml)
                    .as("application.yml console 패턴에 %%X{%s}가 있어야 한다", key.key())
                    .contains("%X{" + key.key());
        }
    }

    @Test
    void fileAppenderPatternContainsEveryMdcKey() throws Exception {
        String logbackXml = readClasspathFile("logback-spring.xml");

        for (MdcKey key : textPatternKeys()) {
            assertThat(logbackXml)
                    .as("logback-spring.xml 파일 어펜더 패턴에 %%X{%s}가 있어야 한다", key.key())
                    .contains("%X{" + key.key());
        }
    }

    /** 구조화 전용 키(요청 메타데이터)는 텍스트 패턴에 없어도 됨. JSON 로그에는 MDC 전체가 자동 포함됨. */
    private static List<MdcKey> textPatternKeys() {
        return Arrays.stream(MdcKey.values()).filter(MdcKey::inTextPattern).toList();
    }

    private static String readClasspathFile(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
