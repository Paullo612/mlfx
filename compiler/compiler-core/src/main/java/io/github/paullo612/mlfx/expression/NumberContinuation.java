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

import io.github.paullo612.mlfx.compiler.elements.RenderUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;

import java.util.Optional;

abstract class NumberContinuation extends NumberBoxableContinuation implements BinaryBoxableContinuation {

    private final ExpressionContinuation left;
    private final ExpressionContinuation right;

    private PrimitiveElement commonType;

    NumberContinuation(
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
    public Optional<ExpressionContext.RenderCommand> coerceLiteral(
            ExpressionContinuation sourceExpression,
            ClassElement targetType) {
        // NB: Literal must be already converted to number literal at this point.
        return sourceExpression.asLiteral()
                .map(l -> l.accept(new LiteralVisitor<>() {

                    @Override
                    public ExpressionContext.RenderCommand visit(DecimalLiteral literal) {
                        return RenderUtils.coerceOrThrow(context, String.valueOf(literal.getValue()), targetType);
                    }

                    @Override
                    public ExpressionContext.RenderCommand visit(FloatingPointLiteral literal) {
                        return RenderUtils.coerceOrThrow(context, String.valueOf(literal.getValue()), targetType);
                    }
                }));
    }

    abstract String getTypeError();

    private PrimitiveElement getCommonType() {
        if (this.commonType == null) {
            PrimitiveElement leftPrimitive = unbox(left.getClassElement());
            PrimitiveElement rightPrimitive = unbox(right.getClassElement());

            if (leftPrimitive == null && rightPrimitive == null) {
                // Both types are not a numbers. We need at least one number to infer other's type.
                throw context.compileError(getTypeError());
            }

            this.commonType = inferCommonType(leftPrimitive, rightPrimitive);
        }

        return this.commonType;
    }

    @Override
    public ClassElement getClassElement() {
        return getCommonType();
    }

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        PrimitiveElement commonType = getCommonType();

        return runBoxed(adapter, context, left, right, commonType);
    }
}
