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

import java.util.Optional;

interface ExpressionContinuation {

    default boolean canBeRun() {
        return true;
    }

    default Optional<Literal> asLiteral() {
        return Optional.empty();
    }

    String getName();

    default int requiresMonitoringCount() {
        return 0;
    }

    ClassElement getClassElement();

    ExpressionContext.RenderCommand run(RenderingAdapter adapter);

    default <T> T accept(ExpressionContinuationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
