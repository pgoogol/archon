package com.pgoogol.diagnostics.core;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Miejsce wywołania z bieżącego stosu: pierwsza ramka z pakietów aplikacji, licząc od
 * najnowszej. Chodzi po stosie przy każdym wywołaniu; regułę z prod (tylko operacje wolne
 * i powtarzane) nakłada {@link SelectiveCallSiteResolver}.
 *
 * <p>Pomija ramki, które nie są kodem aplikacji, nawet gdy leżą w jej pakietach: własne
 * pakiety startera, klasy generowane (CGLIB i proxy Spring z {@code $$}, proxy Hibernate),
 * ciała lambd ({@code lambda$…}; liczy się metoda, w której lambda powstała) i proxy JDK.
 * Proxy JDK z interfejsem z pakietów aplikacji, leżące nad miejscem wywołania, to zwykle
 * repozytorium Spring Data; jego metoda trafia do {@link CallSite#repositoryMethod()}.</p>
 */
public class StackWalkingCallSiteResolver implements CallSiteResolver {

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private static final List<String> OWN_PACKAGES = List.of(
        "com.pgoogol.diagnostics.core.",
        "com.pgoogol.diagnostics.jdbc.",
        "com.pgoogol.diagnostics.spring.");

    private static final Set<String> GENERATED_MARKERS = Set.of("$$", "$HibernateProxy", "$ByteBuddy");

    private final List<String> applicationPackages;

    /** @param applicationPackages pakiety bazowe aplikacji, np. {@code com.pgoogol.music} */
    public StackWalkingCallSiteResolver(List<String> applicationPackages) {

        this.applicationPackages = applicationPackages.stream()
            .map(applicationPackage -> applicationPackage + ".")
            .toList();
    }

    @Override
    public Optional<CallSite> resolve(DataStore store, String shape, Duration duration) {

        if (applicationPackages.isEmpty()) {

            return Optional.empty();
        }
        return WALKER.walk(this::scan);
    }

    private Optional<CallSite> scan(Stream<StackWalker.StackFrame> frames) {

        RepositoryTracker repository = new RepositoryTracker();
        // peek zamiast pętli: metoda repozytorium leży na stosie nad miejscem wywołania, więc
        // trzeba ją zapamiętać w drodze do pierwszej ramki aplikacji, a findFirst kończy marsz
        return frames.peek(repository::observe)
            .filter(this::isApplicationFrame)
            .findFirst()
            .map(frame -> callSite(frame, repository.method()));
    }

    private boolean isApplicationFrame(StackWalker.StackFrame frame) {

        Class<?> type = frame.getDeclaringClass();
        String className = type.getName();
        return inApplication(className)
            && OWN_PACKAGES.stream().noneMatch(className::startsWith)
            && GENERATED_MARKERS.stream().noneMatch(className::contains)
            && !frame.getMethodName().startsWith("lambda$")
            && !Proxy.isProxyClass(type);
    }

    private boolean inApplication(String className) {

        return applicationPackages.stream().anyMatch(className::startsWith);
    }

    private CallSite callSite(StackWalker.StackFrame frame, @Nullable String repositoryMethod) {

        String className = frame.getClassName();
        return new CallSite(className, frame.getMethodName(), frame.getLineNumber(), repositoryMethod);
    }

    /** Zapamiętuje pierwszą metodę proxy JDK, którego interfejs należy do aplikacji. */
    private final class RepositoryTracker {

        private @Nullable String method;

        void observe(StackWalker.StackFrame frame) {

            Class<?> type = frame.getDeclaringClass();
            if (Objects.nonNull(method) || !Proxy.isProxyClass(type)) {

                return;
            }
            method = Arrays.stream(type.getInterfaces())
                .filter(candidate -> inApplication(candidate.getName()))
                .findFirst()
                .map(repository -> repository.getSimpleName() + "." + frame.getMethodName())
                .orElse(null);
        }

        @Nullable String method() {

            return method;
        }
    }
}
