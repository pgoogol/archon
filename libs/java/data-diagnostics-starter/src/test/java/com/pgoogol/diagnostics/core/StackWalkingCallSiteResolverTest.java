package com.pgoogol.diagnostics.core;

import com.pgoogol.diagnostics.fakeapp.FakeOrderRepository;
import com.pgoogol.diagnostics.fakeapp.FakeOrderService;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class StackWalkingCallSiteResolverTest {

    private static final String APP = "com.pgoogol.diagnostics.fakeapp";

    private final AtomicReference<Optional<CallSite>> seen = new AtomicReference<>(Optional.empty());

    @Test
    @DisplayName("miejscem wywołania jest metoda serwisu aplikacji, a metoda repozytorium trafia obok")
    void resolve_whenCalledThroughRepositoryProxy_returnsServiceMethodAndRepository() {

        // given
        StackWalkingCallSiteResolver resolver = new StackWalkingCallSiteResolver(List.of(APP));
        FakeOrderService service = new FakeOrderService(repositoryResolvingWith(resolver));

        // when
        service.list();

        // then
        CallSite callSite = seen.get().orElseThrow();
        assertAll(
            () -> assertThat(callSite.className()).isEqualTo(FakeOrderService.class.getName()),
            () -> assertThat(callSite.method()).isEqualTo("list"),
            () -> assertThat(callSite.line()).isPositive(),
            () -> assertThat(callSite.repositoryMethod()).isEqualTo("FakeOrderRepository.findAllByStatus"));
    }

    @Test
    @DisplayName("ciało lambdy nie jest miejscem wywołania; liczy się metoda, w której lambda powstała")
    void resolve_whenCalledFromLambda_returnsEnclosingMethod() {

        // given
        StackWalkingCallSiteResolver resolver = new StackWalkingCallSiteResolver(List.of(APP));
        FakeOrderService service = new FakeOrderService(repositoryResolvingWith(resolver));

        // when
        service.listInLambda();

        // then
        assertThat(seen.get()).map(CallSite::method).contains("listInLambda");
    }

    @Test
    @DisplayName("klasa generowana przez CGLIB nie jest miejscem wywołania, nawet w pakiecie aplikacji")
    void resolve_whenOnlyGeneratedClassOnStack_returnsEmpty() {

        // given: rada proxy CGLIB woła resolver, więc z kodu aplikacji na stosie jest tylko podklasa $$
        StackWalkingCallSiteResolver resolver = new StackWalkingCallSiteResolver(List.of(APP));
        MethodInterceptor resolving = invocation -> {

            seen.set(resolver.resolve(DataAccessEventFixtures.POSTGRESQL, "select 1", Duration.ZERO));
            return List.of();
        };
        ProxyFactory factory = new ProxyFactory(new FakeOrderService(status -> List.of()));
        factory.setProxyTargetClass(true);
        factory.addAdvice(resolving);
        FakeOrderService proxied = (FakeOrderService) factory.getProxy();
        seen.set(Optional.of(new CallSite("marker", "marker", 0, null)));

        // when
        proxied.list();

        // then
        assertThat(seen.get()).isEmpty();
    }

    @Test
    @DisplayName("bez pakietów aplikacji resolver nie chodzi po stosie i nic nie zwraca")
    void resolve_whenNoApplicationPackages_returnsEmpty() {

        // given
        StackWalkingCallSiteResolver resolver = new StackWalkingCallSiteResolver(List.of());
        FakeOrderService service = new FakeOrderService(repositoryResolvingWith(resolver));

        // when
        service.list();

        // then
        assertThat(seen.get()).isEmpty();
    }

    /** Proxy JDK repozytorium, które przy wywołaniu ustala miejsce, jak zrobiłby to sterownik JDBC. */
    private FakeOrderRepository repositoryResolvingWith(CallSiteResolver resolver) {

        ClassLoader loader = FakeOrderRepository.class.getClassLoader();
        Class<?>[] interfaces = {FakeOrderRepository.class};
        return (FakeOrderRepository) Proxy.newProxyInstance(loader, interfaces, (proxy, method, args) -> {

            seen.set(resolver.resolve(DataAccessEventFixtures.POSTGRESQL, "select 1", Duration.ZERO));
            return List.of();
        });
    }
}
