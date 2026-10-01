package com.example.skeleton.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

    private enum TestErrorCode implements ErrorCode {
        TEST_NOT_FOUND, TEST_POLICY_VIOLATED
    }

    private static class TestNotFoundException extends ResourceNotFoundException {
        TestNotFoundException(UUID id) {
            super(TestErrorCode.TEST_NOT_FOUND, "Test resource not found: " + id, id);
        }
    }

    private static class TestPolicyException extends UnprocessableException {
        TestPolicyException() {
            super(TestErrorCode.TEST_POLICY_VIOLATED, "Test policy violated");
        }
    }

    private final StaticMessageSource messageSource = new StaticMessageSource();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource);

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void notFoundDetailIsResolvedForTheRequestLocale() {
        UUID id = UUID.randomUUID();
        messageSource.addMessage("error.TEST_NOT_FOUND", Locale.KOREAN, "테스트 리소스를 찾을 수 없습니다: {0}");
        LocaleContextHolder.setLocale(Locale.KOREAN);

        ProblemDetail problem = handler.handleNotFound(new TestNotFoundException(id));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("테스트 리소스를 찾을 수 없습니다: " + id);
        assertThat(problem.getProperties()).containsEntry("code", "TEST_NOT_FOUND");
    }

    @Test
    void missingBundleKeyFallsBackToTheLogMessage() {
        UUID id = UUID.randomUUID();

        ProblemDetail problem = handler.handleNotFound(new TestNotFoundException(id));

        assertThat(problem.getDetail()).as("번들 키가 없으면 영어 로그 메시지로 폴백한다").isEqualTo("Test resource not found: " + id);
    }

    @Test
    void policyViolationMapsTo422() {
        ProblemDetail problem = handler.handleUnprocessable(new TestPolicyException());

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getProperties()).containsEntry("code", "TEST_POLICY_VIOLATED");
    }

    @Test
    void accessDeniedMapsTo403() {
        ProblemDetail problem = handler.handleAccessDenied(new AccessDeniedException("nope"));

        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getProperties()).containsEntry("code", "ACCESS_DENIED");
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingDetails() {
        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("internal secret"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("internal secret");
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }
}
