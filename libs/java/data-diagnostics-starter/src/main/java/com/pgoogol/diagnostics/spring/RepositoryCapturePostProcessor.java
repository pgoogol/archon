package com.pgoogol.diagnostics.spring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;

/**
 * Dopina {@link RepositoryCaptureStrategy} do każdej fabryki repozytoriów Spring Data,
 * zanim ta zbuduje repozytorium. To publiczna droga rejestracji słuchacza wywołań:
 * fabryka przyjmuje dostosowania, a dostosowanie dodaje słuchacza.
 */
class RepositoryCapturePostProcessor implements BeanPostProcessor {

    private final ObjectProvider<RepositoryCaptureStrategy> strategy;

    RepositoryCapturePostProcessor(ObjectProvider<RepositoryCaptureStrategy> strategy) {

        this.strategy = strategy;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {

        if (bean instanceof RepositoryFactoryBeanSupport<?, ?, ?> factoryBean) {

            factoryBean.addRepositoryFactoryCustomizer(
                factory -> strategy.ifAvailable(factory::addInvocationListener));
        }
        return bean;
    }
}
