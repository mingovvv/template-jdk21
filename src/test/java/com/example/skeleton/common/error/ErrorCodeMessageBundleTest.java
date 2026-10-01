package com.example.skeleton.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * 코드(ErrorCode enum)와 설정(메시지 번들) 사이의 드리프트 감시자.
 * 새 에러 코드를 추가하고 번들 등록을 잊으면 런타임에 폴백으로 조용히 넘어가는 대신 여기서 빌드가 실패함.
 * 코드 형식 규약(SCREAMING_SNAKE)도 함께 강제함.
 */
class ErrorCodeMessageBundleTest {

    private static final List<String> BUNDLES = List.of("messages.properties", "messages_ko.properties");
    private static final String CODE_FORMAT = "[A-Z]+(_[A-Z]+)*";

    @Test
    void everyErrorCodeHasAMessageInEveryBundle() throws Exception {
        List<ErrorCode> allCodes = findAllErrorCodes();
        assertThat(allCodes).as("스캔이 ErrorCode 구현을 하나도 못 찾으면 테스트 자체가 무의미하다").isNotEmpty();

        for (String bundle : BUNDLES) {
            Properties messages = loadBundle(bundle);
            for (ErrorCode code : allCodes) {
                assertThat(messages.containsKey(code.messageKey()))
                        .as("%s 에 %s 키가 등록되어야 한다", bundle, code.messageKey())
                        .isTrue();
            }
        }
    }

    @Test
    void everyErrorCodeFollowsTheNamingConvention() throws Exception {
        for (ErrorCode code : findAllErrorCodes()) {
            assertThat(code.code()).matches(CODE_FORMAT);
        }
    }

    private static List<ErrorCode> findAllErrorCodes() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
        List<ErrorCode> codes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.example.skeleton")) {
            Class<?> clazz = Class.forName(candidate.getBeanClassName());
            if (clazz.isEnum() && isProductionClass(clazz)) {
                for (Object constant : clazz.getEnumConstants()) {
                    codes.add((ErrorCode) constant);
                }
            }
        }
        return codes;
    }

    /** 테스트 픽스처용 ErrorCode enum(예: 핸들러 테스트의 TestErrorCode)은 번들 등록 의무에서 제외함. */
    private static boolean isProductionClass(Class<?> clazz) {
        // 경로 문자열에 "test"가 있는지로 판별하면 프로젝트가 .../tests/... 아래 있을 때 전부 제외됨.
        // 그래서 테스트 클래스패스 루트와 비교함.
        var codeSource = clazz.getProtectionDomain().getCodeSource();
        var testRoot = ErrorCodeMessageBundleTest.class.getProtectionDomain().getCodeSource().getLocation();
        return codeSource != null && !codeSource.getLocation().equals(testRoot);
    }

    private static Properties loadBundle(String path) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(new ClassPathResource(path).getInputStream(),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
