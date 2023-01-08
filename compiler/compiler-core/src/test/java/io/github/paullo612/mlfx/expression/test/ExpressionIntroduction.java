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
package io.github.paullo612.mlfx.expression.test;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@InterceptorBean(Expression.class)
class ExpressionIntroduction implements MethodInterceptor<Object, Object> {

    private static class MethodKey {
        final Class<?> methodDeclaringType;
        final String methodDescription;

        MethodKey(Class<?> methodDeclaringType, String methodDescription) {
            this.methodDeclaringType = Objects.requireNonNull(methodDeclaringType);
            this.methodDescription = Objects.requireNonNull(methodDescription);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MethodKey methodKey = (MethodKey) o;
            return methodDeclaringType.equals(methodKey.methodDeclaringType)
                    && methodDescription.equals(methodKey.methodDescription);
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodDeclaringType, methodDescription);
        }
    }

    private final Map<MethodKey, ExpressionIntroductionDelegate> methodMap;

    @Inject
    ExpressionIntroduction(List<ExpressionIntroductionDelegate> delegates) {
        this.methodMap = delegates.stream()
                .collect(
                        Collectors.toMap(
                                d -> new MethodKey(d.getMethodDeclaringType(), d.getMethodDescription()),
                                Function.identity()
                        )
                );
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        ExpressionIntroductionDelegate delegate =
                methodMap.get(new MethodKey(context.getDeclaringType(), context.getDescription(false)));

        if (delegate == null) {
            return context.proceed();
        }

        return delegate.getExpressionValue(context.getTarget());
    }
}
