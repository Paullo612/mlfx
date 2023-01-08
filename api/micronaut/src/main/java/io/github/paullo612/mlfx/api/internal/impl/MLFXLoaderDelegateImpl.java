/*
 * Copyright 2023 Paullo612
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.paullo612.mlfx.api.internal.impl;

import io.github.paullo612.mlfx.api.CompiledFXMLLoader;
import io.github.paullo612.mlfx.api.CompiledLoadException;
import io.github.paullo612.mlfx.api.ControllerAccessor;
import io.github.paullo612.mlfx.api.internal.MLFXLoaderDelegate;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.reflect.ClassUtils;
import org.slf4j.Logger;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Micronaut based loader delegate implementation.
 *
 * @author Paullo612
 */
public class MLFXLoaderDelegateImpl implements MLFXLoaderDelegate {

    private static final Logger LOG = ClassUtils.getLogger(MLFXLoaderDelegateImpl.class);

    private final Map<URI, CompiledFXMLLoader<?, ?>> loadersMap = new HashMap<>();

    /**
     * Constructs Micronaut based delegate.
     */
    public MLFXLoaderDelegateImpl() {
        SoftServiceLoader<CompiledFXMLLoader> softLoaders =
                SoftServiceLoader.load(CompiledFXMLLoader.class, MLFXLoaderDelegateImpl.class.getClassLoader());

        for (ServiceDefinition<CompiledFXMLLoader> softLoader : softLoaders) {
            if (softLoader.isPresent()) {
                CompiledFXMLLoader<?, ?> loader = softLoader.load();

                URI uri;

                try {
                    uri = loader.getURI();
                } catch (CompiledLoadException e) {
                    LOG.error("Unable to retrieve URI of compiled FXML loader class \"{}\"", softLoader.getName());
                    continue;
                }

                loadersMap.put(uri, loader);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unable to link compiled FXML loader class \"{}\".", softLoader.getName());
                }
            }
        }
    }

    @Override
    public Optional<CompiledFXMLLoader<?, ?>> getCompiledLoader(URI location) {
        return Optional.ofNullable(loadersMap.get(location));
    }

    @Override
    public <C> ControllerAccessor<C> createControllerAccessor(Class<C> controllerClass) {
        BeanIntrospection<C> introspection;

        try {
            introspection = BeanIntrospection.getIntrospection(controllerClass);
        } catch (IntrospectionException e) {
            introspection = null;
        }

        return new ControllerAccessorImpl<>(controllerClass, introspection);
    }
}
