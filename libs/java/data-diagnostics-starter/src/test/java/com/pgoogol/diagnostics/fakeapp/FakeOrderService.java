package com.pgoogol.diagnostics.fakeapp;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Serwis „aplikacji” spoza pakietów startera: z jego metod wychodzą operacje, których
 * miejsce wywołania ustala test.
 */
public class FakeOrderService {

    private final FakeOrderRepository repository;

    public FakeOrderService(FakeOrderRepository repository) {

        this.repository = repository;
    }

    public List<String> list() {

        return repository.findAllByStatus("NEW");
    }

    /** Wywołanie z ciała lambdy; miejscem wywołania ma być ta metoda, nie {@code lambda$…}. */
    public List<String> listInLambda() {

        return IntStream.range(0, 1)
            .mapToObj(index -> repository.findAllByStatus("NEW"))
            .flatMap(List::stream)
            .toList();
    }
}
