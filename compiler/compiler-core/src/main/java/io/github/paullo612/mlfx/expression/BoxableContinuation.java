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
package io.github.paullo612.mlfx.expression;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;

import java.util.Map;

abstract class BoxableContinuation extends AbstractNamedContinuation {

    final ExpressionContext context;

    BoxableContinuation(String name, ExpressionContext context) {
        super(name);

        this.context = context;
    }

    abstract Map<Class<?>, PrimitiveElement> getBoxedTypesMap();

    PrimitiveElement unbox(ClassElement classElement) {
        if (classElement.isArray()) {
            return null;
        }

        Map<Class<?>, PrimitiveElement> boxedTypesMap = getBoxedTypesMap();

        for (Map.Entry<Class<?>, PrimitiveElement> entry : boxedTypesMap.entrySet()) {

            PrimitiveElement primitive = entry.getValue();
            if (classElement.isAssignable(entry.getKey()) || classElement.isAssignable(primitive)) {
                return primitive;
            }
        }

        return null;
    }
}
