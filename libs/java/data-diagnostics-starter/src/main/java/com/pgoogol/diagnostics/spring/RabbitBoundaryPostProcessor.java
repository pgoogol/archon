package com.pgoogol.diagnostics.spring;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Dokłada {@link RabbitMessageBoundary} na początek łańcucha rad każdej fabryki kontenerów
 * słuchaczy RabbitMQ. Fabryka pozwala łańcuch tylko zastąpić, a odczyt ma chroniony, więc
 * dotychczasowe rady (np. ponowienia ustawione przez Boota) czytamy refleksją; bez tego
 * dodanie granicy po cichu by je usunęło.
 */
class RabbitBoundaryPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<RabbitMessageBoundary> boundary;

    RabbitBoundaryPostProcessor(ObjectProvider<RabbitMessageBoundary> boundary) {

        this.boundary = boundary;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {

        if (bean instanceof AbstractRabbitListenerContainerFactory<?> factory) {

            boundary.ifAvailable(advice -> prepend(factory, advice));
        }
        return bean;
    }

    private void prepend(AbstractRabbitListenerContainerFactory<?> factory, RabbitMessageBoundary advice) {

        Advice[] existing = currentChain(factory);
        if (Arrays.asList(existing).contains(advice)) {

            return;
        }
        Advice[] chain = Stream.concat(Stream.of(advice), Arrays.stream(existing))
            .toArray(Advice[]::new);
        factory.setAdviceChain(chain);
    }

    private Advice[] currentChain(AbstractRabbitListenerContainerFactory<?> factory) {

        Method getter = ReflectionUtils.findMethod(AbstractRabbitListenerContainerFactory.class, "getAdviceChain");
        if (Objects.isNull(getter)) {

            return new Advice[0];
        }
        ReflectionUtils.makeAccessible(getter);
        Object chain = ReflectionUtils.invokeMethod(getter, factory);
        if (chain instanceof Advice[] advices) {

            return advices;
        }
        return new Advice[0];
    }
}
