package com.pgoogol.httpexchangelogger.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedBodyCaptureHttpServletResponseTest {

    @Test
    void getCapturedBody_whenBodyWithinLimit_capturesFullBodyWithoutOverflow() throws Exception {

        // given
        MockHttpServletResponse delegate = new MockHttpServletResponse();
        BoundedBodyCaptureHttpServletResponse response = new BoundedBodyCaptureHttpServletResponse(delegate, 100);

        // when
        response.getOutputStream().write("hello".getBytes(StandardCharsets.UTF_8));

        // then
        assertThat(response.getCapturedBody()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(response.isCaptureOverflowed()).isFalse();
        assertThat(delegate.getContentAsByteArray()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getCapturedBody_whenBodyExceedsLimit_capturesOnlyPrefixAndFlagsOverflow() throws Exception {

        // given
        MockHttpServletResponse delegate = new MockHttpServletResponse();
        BoundedBodyCaptureHttpServletResponse response = new BoundedBodyCaptureHttpServletResponse(delegate, 4);

        // when
        response.getOutputStream().write("abcdefgh".getBytes(StandardCharsets.UTF_8));

        // then
        assertThat(response.getCapturedBody()).isEqualTo("abcd".getBytes(StandardCharsets.UTF_8));
        assertThat(response.isCaptureOverflowed()).isTrue();
    }

    @Test
    void getOutputStream_whenBodyExceedsLimit_stillStreamsFullBodyToContainer() throws Exception {

        // given
        MockHttpServletResponse delegate = new MockHttpServletResponse();
        BoundedBodyCaptureHttpServletResponse response = new BoundedBodyCaptureHttpServletResponse(delegate, 4);

        // when
        response.getOutputStream().write("abcdefgh".getBytes(StandardCharsets.UTF_8));

        // then
        assertThat(delegate.getContentAsByteArray()).isEqualTo("abcdefgh".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getOutputStream_whenSingleByteWritesExceedLimit_capturesPrefixAndFlagsOverflow() throws Exception {

        // given
        MockHttpServletResponse delegate = new MockHttpServletResponse();
        BoundedBodyCaptureHttpServletResponse response = new BoundedBodyCaptureHttpServletResponse(delegate, 2);

        // when
        response.getOutputStream().write('a');
        response.getOutputStream().write('b');
        response.getOutputStream().write('c');

        // then
        assertThat(response.getCapturedBody()).isEqualTo("ab".getBytes(StandardCharsets.UTF_8));
        assertThat(response.isCaptureOverflowed()).isTrue();
        assertThat(delegate.getContentAsByteArray()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getWriter_whenFlushedByFilter_deliversAndCapturesWriterOutput() throws Exception {

        // given
        MockHttpServletResponse delegate = new MockHttpServletResponse();
        delegate.setCharacterEncoding(StandardCharsets.UTF_8.name());
        BoundedBodyCaptureHttpServletResponse response = new BoundedBodyCaptureHttpServletResponse(delegate, 100);
        PrintWriter writer = response.getWriter();

        // when
        writer.write("written via writer");
        response.flushCapturedWriter();

        // then
        assertThat(response.getCapturedBody()).isEqualTo("written via writer".getBytes(StandardCharsets.UTF_8));
        assertThat(delegate.getContentAsString()).isEqualTo("written via writer");
    }

    @Test
    void resetBuffer_whenBodyWasCaptured_clearsCaptureAndOverflowState() throws Exception {

        // given
        MockHttpServletResponse delegate = new MockHttpServletResponse();
        BoundedBodyCaptureHttpServletResponse response = new BoundedBodyCaptureHttpServletResponse(delegate, 4);
        response.getOutputStream().write("abcdefgh".getBytes(StandardCharsets.UTF_8));

        // when
        response.resetBuffer();

        // then
        assertThat(response.getCapturedBody()).isEmpty();
        assertThat(response.isCaptureOverflowed()).isFalse();
    }

}
