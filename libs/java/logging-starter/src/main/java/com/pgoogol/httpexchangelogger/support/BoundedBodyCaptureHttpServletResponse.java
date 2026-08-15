package com.pgoogol.httpexchangelogger.support;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Response wrapper that streams the body straight through to the container while capturing only
 * the first {@code captureLimit} bytes for logging. Memory usage is bounded by the capture limit
 * regardless of the response size, and no copy-back step is needed because the container receives
 * every byte as the handler writes it.
 */
public class BoundedBodyCaptureHttpServletResponse extends HttpServletResponseWrapper {

    private static final int INITIAL_BUFFER_SIZE = 1_024;

    private final int captureLimit;
    private final ByteArrayOutputStream capture;

    private long totalWritten;
    private ServletOutputStream outputStream;
    private PrintWriter writer;

    public BoundedBodyCaptureHttpServletResponse(HttpServletResponse response, int captureLimit) {

        super(response);
        this.captureLimit = Math.max(captureLimit, 0);
        this.capture = new ByteArrayOutputStream(Math.min(this.captureLimit, INITIAL_BUFFER_SIZE));
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

    @Override
    public ServletOutputStream getOutputStream() throws IOException {

        if (Objects.isNull(outputStream)) {

            outputStream = new CapturingServletOutputStream(super.getOutputStream());
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {

        if (Objects.isNull(writer)) {

            Charset charset = resolveCharset(getCharacterEncoding());
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), charset));
        }
        return writer;
    }

    public byte[] getCapturedBody() {

        return capture.toByteArray();
    }

    /**
     * True when the handler wrote more bytes than the capture limit; the captured body is then an
     * incomplete prefix and must never be treated as the full payload.
     */
    public boolean isCaptureOverflowed() {

        return totalWritten > captureLimit;
    }

    /**
     * Pushes characters buffered inside the wrapper's writer down to the container's output
     * stream. Must be called once after the filter chain returns, otherwise writer output that
     * the handler never flushed would be lost.
     */
    public void flushCapturedWriter() {

        if (Objects.nonNull(writer)) {

            writer.flush();
        }
    }

    @Override
    public void resetBuffer() {

        super.resetBuffer();
        clearCapture();
    }

    @Override
    public void reset() {

        super.reset();
        clearCapture();
    }

    private void clearCapture() {

        capture.reset();
        totalWritten = 0;
    }

    private final class CapturingServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        private CapturingServletOutputStream(ServletOutputStream delegate) {

            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {

            delegate.write(b);
            if (totalWritten < captureLimit) {

                capture.write(b);
            }
            totalWritten++;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {

            delegate.write(buffer, offset, length);
            if (totalWritten < captureLimit) {

                int room = (int) Math.min(captureLimit - totalWritten, length);
                capture.write(buffer, offset, room);
            }
            totalWritten += length;
        }

        @Override
        public void flush() throws IOException {

            delegate.flush();
        }

        @Override
        public void close() throws IOException {

            delegate.close();
        }

        @Override
        public boolean isReady() {

            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {

            delegate.setWriteListener(writeListener);
        }

    }

}
