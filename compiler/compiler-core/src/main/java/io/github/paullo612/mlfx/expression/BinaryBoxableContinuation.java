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

import java.util.Optional;

interface BinaryBoxableContinuation {

    private static int score(PrimitiveElement primitive) {
        if (ElementUtils.isAssignable(primitive, double.class)) {
            return 3;
        }

        if (ElementUtils.isAssignable(primitive, float.class)) {
            return 2;
        }

        if (ElementUtils.isAssignable(primitive, long.class)) {
            return 1;
        }

        return 0;
    }

    private static PrimitiveElement findCommonType(PrimitiveElement left, PrimitiveElement right) {
        if (ElementUtils.isAssignable(left, boolean.class) || ElementUtils.isAssignable(right, boolean.class)) {
            // The only common type for booleans is boolean.
            return PrimitiveElement.BOOLEAN;
        }

        int lScore = score(left);
        int rScore = score(right);

        if (lScore == 0 && rScore == 0) {
            // NB: Follow Java rules there. Math operation with any type shorter than int results to int.
            return PrimitiveElement.INT;
        }

        return lScore > rScore ? left : right;
    }

    interface Factory {
        ExpressionContinuation make(
                String name, ExpressionContinuation left, ExpressionContinuation right, ExpressionContext context
        );
    }

    Optional<ExpressionContext.RenderCommand> coerceLiteral(
            ExpressionContinuation sourceExpression,
            ClassElement targetType);

    ExpressionContext.RenderCommand createCommand(ClassElement classElement);

    default PrimitiveElement inferCommonType(PrimitiveElement leftPrimitive, PrimitiveElement rightPrimitive) {
        if (leftPrimitive == null || rightPrimitive == null) {
            return leftPrimitive != null
                    ? leftPrimitive
                    : rightPrimitive;
        }

        return findCommonType(leftPrimitive, rightPrimitive);
    }

    default ExpressionContext.RenderCommand runBoxed(
            RenderingAdapter adapter,
            ExpressionContext context,
            ExpressionContinuation left,
            ExpressionContinuation right,
            PrimitiveElement commonType) {
        ExpressionContext.RenderCommand leftRenderCommand = coerceLiteral(left, commonType)
                // Fallback to runtime coercion if it is not a literal.
                .orElseGet(() -> {
                    ClassElement sourceType = left.getClassElement();
                    return !ElementUtils.isAssignable(sourceType, commonType)
                            ? RenderUtils.coerceOrThrow(context, sourceType, commonType)
                            : null;
                });

        ExpressionContext.RenderCommand rightRenderCommand = coerceLiteral(right, commonType)
                // Fallback to runtime coercion if it is not a literal.
                .orElseGet(() -> {
                    ExpressionContext.RenderCommand command = adapter.adapt(right);

                    ClassElement sourceType = right.getClassElement();
                    if (!ElementUtils.isAssignable(sourceType, commonType)) {
                        ExpressionContext.RenderCommand coerceCommand = RenderUtils.coerceOrThrow(
                                context,
                                sourceType,
                                commonType
                        );

                        return methodVisitor -> {
                            command.render(methodVisitor);
                            coerceCommand.render(methodVisitor);
                        };
                    }

                    return command;
                });

        ExpressionContext.RenderCommand command = createCommand(commonType);

        return methodVisitor -> {
            if (leftRenderCommand != null) {
                leftRenderCommand.render(methodVisitor);
            }

            rightRenderCommand.render(methodVisitor);
            command.render(methodVisitor);
        };
    }
}
