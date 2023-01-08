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

import io.github.paullo612.mlfx.compiler.elements.ElementUtils;
import io.github.paullo612.mlfx.compiler.elements.RenderUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;

import java.util.Map;

// NB: We'll be more liberal here. Everything that cannot be evaluated to true is false.
abstract class BooleanContinuation extends BoxableContinuation {

    private static final ShortCircuitContinuation SHORT_CIRCUIT_CONTINUATION = new ShortCircuitContinuation();

    static final String TYPE_ERROR = "Incompatible types in boolean operation.";

    private static final Map<Class<?>, PrimitiveElement> BOXED_BOOLEAN = Map.of(
            Boolean.class, PrimitiveElement.BOOLEAN
    );

    static LiteralVisitor<Boolean> literalVisitor(ExpressionContext context) {
        return new LiteralVisitor<>() {

            @Override
            public Boolean visit(Literal literal) {
                return false;
            }

            @Override
            public Boolean visit(StringLiteral literal) {
                return Boolean.parseBoolean(literal.getValue());
            }

            @Override
            public Boolean visit(BooleanLiteral literal) {
                return literal.getValue();
            }

            @Override
            public Boolean visit(NullLiteral literal) {
                throw context.compileError(TYPE_ERROR);
            }
        };
    }

    private final ExpressionContinuation left;
    private final ExpressionContinuation right;

    BooleanContinuation(
            String name,
            ExpressionContinuation left,
            ExpressionContinuation right,
            ExpressionContext context) {
        super(name, context);
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean canBeRun() {
        return right.canBeRun();
    }

    @Override
    public int requiresMonitoringCount() {
        return right.requiresMonitoringCount();
    }

    @Override
    Map<Class<?>, PrimitiveElement> getBoxedTypesMap() {
        return BOXED_BOOLEAN;
    }

    @Override
    public ClassElement getClassElement() {
        return PrimitiveElement.BOOLEAN;
    }

    abstract ExpressionContext.RenderCommand doRun(
            ExpressionContext.RenderCommand leftCoerceCommand,
            ExpressionContext.RenderCommand shortCircuitCommand,
            ExpressionContext.RenderCommand rightRenderCommand);

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        // NB: We do not bother handling literals here, as all literals must already be removed by compile time
        //  optimizations.
        ClassElement leftClasElement = left.getClassElement();
        ClassElement rightClassElement = right.getClassElement();

        PrimitiveElement leftPrimitive = unbox(leftClasElement);
        PrimitiveElement rightPrimitive = unbox(rightClassElement);

        ExpressionContext.RenderCommand leftCoerceCommand = leftPrimitive == null
                || !ElementUtils.isAssignable(leftClasElement, leftPrimitive)
                ? RenderUtils.coerceOrThrow(context, leftClasElement, PrimitiveElement.BOOLEAN)
                : null;

        ExpressionContext.RenderCommand shortCircuitCommand = adapter.adapt(SHORT_CIRCUIT_CONTINUATION);

        ExpressionContext.RenderCommand rightCoerceCommand = rightPrimitive == null
                || !ElementUtils.isAssignable(rightClassElement, rightPrimitive)
                ? RenderUtils.coerceOrThrow(context, rightClassElement, PrimitiveElement.BOOLEAN)
                : null;

        ExpressionContext.RenderCommand rightCommand = adapter.adapt(right);

        ExpressionContext.RenderCommand rightCoercedCommand = rightCoerceCommand != null
                ? methodVisitor -> {
                    rightCommand.render(methodVisitor);
                    rightCoerceCommand.render(methodVisitor);
                }
                : rightCommand;

        return doRun(leftCoerceCommand, shortCircuitCommand, rightCoercedCommand);
    }
}
