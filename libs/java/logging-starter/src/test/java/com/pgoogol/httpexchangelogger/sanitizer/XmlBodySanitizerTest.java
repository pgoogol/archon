package com.pgoogol.httpexchangelogger.sanitizer;

import com.pgoogol.httpexchangelogger.autoconfigure.HttpExchangeLoggerProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XmlBodySanitizerTest {

    private XmlBodySanitizer sanitizerWithFields(List<String> fields) {

        HttpExchangeLoggerProperties.Mask mask = new HttpExchangeLoggerProperties.Mask();
        mask.setEnabled(true);
        mask.setFields(fields);
        SensitiveValueMasker masker = new SensitiveValueMasker(fields);
        return new XmlBodySanitizer(masker, mask);
    }

    private XmlBodySanitizer disabledSanitizer() {

        HttpExchangeLoggerProperties.Mask mask = new HttpExchangeLoggerProperties.Mask();
        mask.setEnabled(false);
        mask.setFields(List.of("password"));
        SensitiveValueMasker masker = new SensitiveValueMasker(mask.getFields());
        return new XmlBodySanitizer(masker, mask);
    }

    @Test
    void sanitize_whenSensitiveElement_replacesTextWithMask() {

        // given
        XmlBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));
        String input = "<order><password>secret</password><productId>p_1</productId></order>";

        // when
        String result = sanitizer.sanitize(input, "application/xml");

        // then
        assertThat(result).contains("<password>***</password>");
        assertThat(result).contains("<productId>p_1</productId>");
        assertThat(result).doesNotContain("secret");
    }

    @Test
    void sanitize_whenSensitiveAttribute_replacesValueWithMask() {

        // given
        XmlBodySanitizer sanitizer = sanitizerWithFields(List.of("token"));
        String input = "<user token=\"abc-123\" name=\"alice\"/>";

        // when
        String result = sanitizer.sanitize(input, "application/xml");

        // then
        assertThat(result).contains("token=\"***\"");
        assertThat(result).contains("name=\"alice\"");
        assertThat(result).doesNotContain("abc-123");
    }

    @Test
    void sanitize_whenFieldNameInDifferentCase_masksCaseInsensitively() {

        // given
        XmlBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));
        String input = "<root><Password>x</Password><PASSWORD>y</PASSWORD></root>";

        // when
        String result = sanitizer.sanitize(input, "application/xml");

        // then
        assertThat(result).doesNotContain(">x<");
        assertThat(result).doesNotContain(">y<");
        assertThat(result).contains("<Password>***</Password>");
        assertThat(result).contains("<PASSWORD>***</PASSWORD>");
    }

    @Test
    void sanitize_whenNestedSensitiveElement_masksRecursively() {

        // given
        XmlBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));
        String input = "<user><credentials><password>secret</password></credentials></user>";

        // when
        String result = sanitizer.sanitize(input, "application/xml");

        // then
        assertThat(result).contains("<password>***</password>");
        assertThat(result).doesNotContain("secret");
    }

    @Test
    void sanitize_whenMaskingDisabled_returnsOriginalBody() {

        // given
        XmlBodySanitizer sanitizer = disabledSanitizer();
        String input = "<x><password>secret</password></x>";

        // when
        String result = sanitizer.sanitize(input, "application/xml");

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void sanitize_whenContentTypeIsNotXml_returnsOriginalBody() {

        // given
        XmlBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));
        String input = "{\"password\":\"secret\"}";

        // when
        String result = sanitizer.sanitize(input, "application/json");

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void sanitize_whenXmlInvalidAndSensitiveFieldsConfigured_returnsPlaceholder() {

        // given
        XmlBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));
        String invalid = "<not-xml<<<>>>password=secret";

        // when
        String result = sanitizer.sanitize(invalid, "application/xml");

        // then
        assertThat(result).isEqualTo(XmlBodySanitizer.UNPARSEABLE_PLACEHOLDER);
        assertThat(result).doesNotContain("secret");
    }

    @Test
    void sanitize_whenDoctypeDeclared_rejectsAndFailsClosed() {

        // given
        // Hardened parser must reject DOCTYPE declarations (XXE defence). Result should be the
        // unparseable placeholder, never the raw body (which would let an attacker disclose data).
        XmlBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));
        String withDoctype = "<!DOCTYPE foo [<!ELEMENT foo ANY>]><foo><password>secret</password></foo>";

        // when
        String result = sanitizer.sanitize(withDoctype, "application/xml");

        // then
        assertThat(result).isEqualTo(XmlBodySanitizer.UNPARSEABLE_PLACEHOLDER);
        assertThat(result).doesNotContain("secret");
    }

    @Test
    void sanitize_whenVendorXmlContentType_returnsMaskedXml() {

        // given
        XmlBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));
        String input = "<root><password>secret</password></root>";

        // when
        String result = sanitizer.sanitize(input, "application/atom+xml;charset=utf-8");

        // then
        assertThat(result).contains("<password>***</password>");
    }

    @Test
    void sanitize_whenBodyIsNull_returnsNull() {

        // given
        XmlBodySanitizer sanitizer = sanitizerWithFields(List.of("password"));

        // when / then
        assertThat(sanitizer.sanitize(null, "application/xml")).isNull();
    }

}
