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

import java.util.Optional;

class FloatingPointLiteralContinuation extends AbstractNamedContinuation implements FloatingPointLiteral {

    private final double value;

    FloatingPointLiteralContinuation(String name, double value) {
        super(name);

        this.value = value;
    }

    @Override
    public double getValue() {
        return value;
    }

    @Override
    public Optional<Literal> asLiteral() {
        return Optional.of(this);
    }

    @Override
    public ClassElement getClassElement() {
        return PrimitiveElement.DOUBLE;
    }

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        return methodVisitor -> methodVisitor.push(getValue());
    }
}
