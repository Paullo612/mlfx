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

import io.github.paullo612.mlfx.api.CompiledLoadException;
import io.github.paullo612.mlfx.api.ControllerAccessor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;

import java.util.Objects;

class ControllerAccessorImpl<C> implements ControllerAccessor<C> {

    private final Class<C> controllerClass;
    private final BeanIntrospection<C> introspection;

    ControllerAccessorImpl(Class<C> controllerClass, BeanIntrospection<C> introspection) {
        this.controllerClass = Objects.requireNonNull(controllerClass);
        this.introspection = introspection;
    }

    @Override
    public Class<C> getControllerClass() {
        return controllerClass;
    }

    private void checkIntrospection() throws CompiledLoadException {
        if (introspection == null) {
            throw new CompiledLoadException(
                    "No introspection found for controller class \"" + controllerClass.getName() + "\"."
            );
        }
    }

    @Override
    public C newControllerInstance() throws CompiledLoadException {
        checkIntrospection();

        try {
            return introspection.instantiate();
        } catch (InstantiationException e) {
            throw new CompiledLoadException("Unable to instantiate class \"" + controllerClass.getName() + "\".", e);
        }
    }

    @Override
    public void setField(C controller, String fieldName, Object value) throws CompiledLoadException {
        checkIntrospection();

        BeanProperty<C, Object> property = introspection.getProperty(fieldName)
                .orElseThrow(() ->
                        new CompiledLoadException(
                                "Unable to find property \"" + fieldName + "\" in class \"" + controllerClass.getName()
                                        + "\"."
                        )
                );

        try {
            property.set(controller, value);
        } catch (IllegalArgumentException e) {
            throw new CompiledLoadException(
                    "Unable to set property \"" + fieldName + "\" of class \"" + controllerClass.getName() + "\".", e
            );
        }
    }

    @Override
    public void executeMethod(C controller, String methodName, Object... arguments) throws CompiledLoadException {
        checkIntrospection();

        BeanMethod<C, Object> method = introspection.getBeanMethods().stream()
                .filter(m -> m.getName().equals(methodName))
                .filter(m -> !m.getReturnType().isReactive() && m.getReturnType().isVoid())
                .filter(m -> m.getArguments().length == arguments.length)
                .filter(m -> {
                    Argument<?>[] methodArguments = m.getArguments();

                    for (int i = 0; i < methodArguments.length; ++i) {
                        if (methodArguments[i].isPrimitive() && !methodArguments[i].isInstance(arguments[i])) {
                            return false;
                        } else if (arguments[i] != null && !methodArguments[i].isInstance(arguments[i])) {
                            return false;
                        }
                    }

                    return true;
                })
                .findFirst()
                .orElseThrow(
                        () -> new CompiledLoadException(
                                "Unable to find method \"" + methodName + "\" with compatible signature in class \""
                                        + controllerClass.getName() + "\"."
                        )
                );

        method.invoke(controller, arguments);
    }

    @Override
    public ExecutableMethod findMethod(C controller, String methodName, Class<?>... argumentTypes)
            throws CompiledLoadException {
        checkIntrospection();

        BeanMethod<C, Object> method = introspection.getBeanMethods().stream()
                .filter(m -> m.getName().equals(methodName))
                .filter(m -> !m.getReturnType().isReactive() && m.getReturnType().isVoid())
                .filter(m -> m.getArguments().length == argumentTypes.length)
                .filter(m -> {
                    Argument<?>[] methodArguments = m.getArguments();

                    for (int i = 0; i < methodArguments.length; ++i) {
                        if (!methodArguments[i].isAssignableFrom(argumentTypes[i])) {
                            return false;
                        }
                    }

                    return true;
                })
                .findFirst()
                .orElseThrow(
                        () -> new CompiledLoadException(
                                "Unable to find method \"" + methodName + "\" with compatible signature in class \""
                                        + controllerClass.getName() + "\"."
                        )
                );

        return arguments -> method.invoke(controller, arguments);
    }
}
