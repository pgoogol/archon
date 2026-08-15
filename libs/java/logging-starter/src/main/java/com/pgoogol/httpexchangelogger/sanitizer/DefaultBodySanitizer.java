package com.pgoogol.httpexchangelogger.sanitizer;

import com.pgoogol.httpexchangelogger.support.ContentTypeMatcher;

public class DefaultBodySanitizer implements BodySanitizer {

    private final BodySanitizer jsonSanitizer;
    private final BodySanitizer xmlSanitizer;
    private final BodySanitizer formSanitizer;

    public DefaultBodySanitizer(BodySanitizer jsonSanitizer, BodySanitizer xmlSanitizer, BodySanitizer formSanitizer) {

        this.jsonSanitizer = jsonSanitizer;
        this.xmlSanitizer = xmlSanitizer;
        this.formSanitizer = formSanitizer;
    }

    @Override
    public String sanitize(String body, String contentType) {

        if (ContentTypeMatcher.isJson(contentType)) {

            return jsonSanitizer.sanitize(body, contentType);
        }
        if (ContentTypeMatcher.isXml(contentType)) {

            return xmlSanitizer.sanitize(body, contentType);
        }
        if (ContentTypeMatcher.isFormUrlEncoded(contentType)) {

            return formSanitizer.sanitize(body, contentType);
        }
        return body;
    }

}
