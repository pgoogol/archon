package com.pgoogol.httpexchangelogger.support;

import jakarta.servlet.http.HttpServletRequest;

public interface ClientIpExtractor {

    String extract(HttpServletRequest request);

}
