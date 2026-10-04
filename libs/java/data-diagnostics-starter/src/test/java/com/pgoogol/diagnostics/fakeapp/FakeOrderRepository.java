package com.pgoogol.diagnostics.fakeapp;

import java.util.List;

/** Repozytorium „aplikacji” do testów miejsca wywołania; test podstawia za nie proxy JDK. */
public interface FakeOrderRepository {

    List<String> findAllByStatus(String status);
}
