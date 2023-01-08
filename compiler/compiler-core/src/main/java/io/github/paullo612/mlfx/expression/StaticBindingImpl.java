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

import java.util.Optional;

class StaticBindingImpl<C extends ExpressionContext> implements Expressions.Binding {

    private static class NoOpRenderingAdapter implements RenderingAdapter {

        static final RenderingAdapter INSTANCE = new NoOpRenderingAdapter();

        @Override
        public ExpressionContext.RenderCommand adapt(ExpressionContinuation continuation) {
            return continuation.run(this);
        }
    }

    private final C context;
    private final ExpressionContinuation continuation;
    private final ClassElement targetType;

    StaticBindingImpl(
            C context,
            ExpressionContinuation continuation,
            ClassElement targetType) {
        this.context = context;
        this.continuation = continuation;
        this.targetType = targetType;
    }

    @Override
    public boolean canBeRun() {
        return continuation.canBeRun();
    }

    @Override
    public ClassElement getType() {
        return targetType != null ? targetType : continuation.getClassElement();
    }

    @Override
    public Expressions.BindingType getBindingType() {
        return Expressions.BindingType.STATIC;
    }

    @Override
    public ExpressionContext.RenderCommand run() {
        ExpressionContext.RenderCommand command = continuation.run(NoOpRenderingAdapter.INSTANCE);
        if (targetType == null || ElementUtils.isAssignable(continuation.getClassElement(), targetType)) {
            return command;
        }

        return continuation.asLiteral()
                .map(l -> l.accept(new LiteralVisitor<ExpressionContext.RenderCommand>() {
                    @Override
                    public ExpressionContext.RenderCommand visit(NullLiteral literal) {
                        // A special case. Null literal has Object type, so we must handle it manually.
                        return command;
                    }

                    @Override
                    public ExpressionContext.RenderCommand visit(StringLiteral literal) {
                        return RenderUtils.coerceOrThrow(context, literal.getValue(), targetType);
                    }

                    @Override
                    public ExpressionContext.RenderCommand visit(DecimalLiteral literal) {
                        return RenderUtils.coerceOrThrow(context, String.valueOf(literal.getValue()), targetType);
                    }

                    @Override
                    public ExpressionContext.RenderCommand visit(FloatingPointLiteral literal) {
                        return RenderUtils.coerceOrThrow(context, String.valueOf(literal.getValue()), targetType);
                    }

                    @Override
                    public ExpressionContext.RenderCommand visit(BooleanLiteral literal) {
                        return RenderUtils.coerceOrThrow(context, String.valueOf(literal.getValue()), targetType);
                    }
                }))
                .orElseGet(() -> {
                    ExpressionContext.RenderCommand coerceCommand =
                            RenderUtils.coerceOrThrow(context, continuation.getClassElement(), targetType);

                    return methodVisitor -> {
                        command.render(methodVisitor);
                        coerceCommand.render(methodVisitor);
                    };
                });
    }

    C getContext() {
        return context;
    }

    Optional<ClassElement> getTargetType() {
        return Optional.ofNullable(targetType);
    }

    ExpressionContinuation getContinuation() {
        return continuation;
    }
}
