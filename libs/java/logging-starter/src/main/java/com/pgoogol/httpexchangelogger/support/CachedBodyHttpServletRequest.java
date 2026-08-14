package com.pgoogol.httpexchangelogger.support;

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
import java.util.Objects;

/**
 * Request wrapper that eagerly reads the body into a re-readable buffer, so the exchange can be
 * logged even when the handler never reads the body. Use only for bounded, textual requests;
 * the application reads the same body back from the buffer.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {

        super(request);
        ServletInputStream inputStream = request.getInputStream();
        this.cachedBody = inputStream.readAllBytes();
    }

    private static Charset resolveCharset(String encoding) {

        if (Objects.isNull(encoding) || encoding.isBlank()) {

            return StandardCharsets.UTF_8;
        }
        try {

            return Charset.forName(encoding);
        } catch (Exception ex) {

            return StandardCharsets.UTF_8;
        }
    }

    public byte[] getCachedBody() {

        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {

        return new CachedServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {

        Charset charset = resolveCharset(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody), charset));
    }

    private static final class CachedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        private CachedServletInputStream(byte[] body) {

            this.buffer = new ByteArrayInputStream(body);
        }

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
        public void setReadListener(ReadListener readListener) {

            throw new UnsupportedOperationException("Async reads are not supported on cached request bodies");
        }

    }

}
