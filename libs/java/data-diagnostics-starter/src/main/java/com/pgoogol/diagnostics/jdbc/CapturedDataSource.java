package com.pgoogol.diagnostics.jdbc;

/**
 * Znacznik {@code DataSource} owiniętego przez {@link DataSourceCapturePostProcessor}.
 * Pozwala pominąć bean, który już przechwytuje zapytania, i sprawdzić w teście, że
 * owinięcie zaszło.
 */
public interface CapturedDataSource {

}
