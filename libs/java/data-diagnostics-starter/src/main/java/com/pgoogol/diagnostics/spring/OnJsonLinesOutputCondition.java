package com.pgoogol.diagnostics.spring;

import com.pgoogol.diagnostics.core.DiagnosticsMode;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Objects;

/**
 * Plik JSONL włącza {@code diagnostics.output.jsonl.enabled}, a bez tego ustawienia tylko
 * tryb dev: w prod plik na dysku serwera to raczej niespodzianka niż pomoc.
 */
class OnJsonLinesOutputCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {

        Binder binder = Binder.get(context.getEnvironment());
        BindResult<Boolean> explicit = binder.bind("diagnostics.output.jsonl.enabled", Boolean.class);
        if (explicit.isBound()) {

            boolean enabled = explicit.get();
            return new ConditionOutcome(enabled, "diagnostics.output.jsonl.enabled=" + enabled);
        }
        DiagnosticsMode mode = binder.bind("diagnostics.mode", DiagnosticsMode.class)
            .orElse(DiagnosticsMode.PROD);
        boolean dev = Objects.equals(mode, DiagnosticsMode.DEV);
        return new ConditionOutcome(dev, "plik JSONL domyślnie tylko w trybie dev, tryb: " + mode);
    }
}
