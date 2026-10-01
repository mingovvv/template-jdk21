package com.example.skeleton.common.filter.wrapper;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 요청 본문을 미리 읽어 두어 요청 처리 전에 로깅할 수 있게 함.
 * 이후 소비자(Jackson 등)는 평소처럼 본문을 다시 읽을 수 있음.
 *
 * Spring의 {@link org.springframework.web.util.ContentCachingRequestWrapper}는 쓰지 않음.
 * 그 클래스는 다운스트림이 읽는 만큼만 복사해 두는 지연 캐싱이라 본문이 {@code doFilter()} 이후에야 확보됨.
 * 요청 로그(본문 포함)는 같은 요청의 서비스 레이어 로그보다 먼저 찍혀야 하므로, 생성 시점에 전부 읽어 두는 즉시 캐싱을 씀.
 *
 * 폼 POST에는 쓰지 않음.
 * 컨테이너는 {@code getParameter()}가 처음 불릴 때 원본 스트림에서 폼을 파싱하는데, 이 래퍼가 먼저 읽으면 파라미터가 사라짐.
 * 이 래퍼는 {@code getParameter*}를 재정의하지 않으므로 폼 판별과 우회는 {@code RequestLoggingFilter}가 담당함.
 *
 * 응답 본문은 컨트롤러가 {@code doFilter()} 동안 써서 만들어지므로 완료 후에만 로깅할 수 있음.
 * 쓰기를 버퍼에 잡아 뒀다가 나중에 꺼내는 흐름은 Spring의 {@link org.springframework.web.util.ContentCachingResponseWrapper}를 그대로 씀.
 * 잡아 둔 본문을 {@code copyBodyToResponse()}로 내보내지 않으면 클라이언트가 빈 응답을 받음.
 */
public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream().readAllBytes();
    }

    public byte[] body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream buffer = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() {
                return buffer.read();
            }

            @Override
            public boolean isFinished() {
                return buffer.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Async reads are not supported for cached bodies");
            }
        };
    }

    /** 요청이 선언한 인코딩(Content-Type의 charset 또는 인코딩 필터가 정한 값)으로 읽고, 없으면 UTF-8로 읽음. */
    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding, StandardCharsets.UTF_8);
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}
