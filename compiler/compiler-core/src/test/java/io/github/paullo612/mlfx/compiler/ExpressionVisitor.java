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
package io.github.paullo612.mlfx.compiler;

import io.github.paullo612.mlfx.compiler.elements.ElementUtils;
import io.github.paullo612.mlfx.expression.Continuation;
import io.github.paullo612.mlfx.expression.ExpressionContext;
import io.github.paullo612.mlfx.expression.Expressions;
import io.github.paullo612.mlfx.expression.test.Expression;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import javafx.beans.value.ObservableValue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class ExpressionVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        AnnotationValue<Expression.Value> expressionAnnotation = element.getAnnotation(Expression.Value.class);
        if (expressionAnnotation == null) {
            return;
        }

        String expression = expressionAnnotation.getValue(String.class)
                .orElse(null);

        if (expression == null || expression.isEmpty()) {
            context.warn("Empty expression", element);
        }

        BindingContextImpl bindingContext = new BindingContextImpl(
                element,
                type -> context.getClassElement(type)
                        .orElseThrow(
                                () -> new CompileErrorException("No class element for type " + type.getSimpleName())
                        )
        );

        ClassElement returnTypeElement = element.getReturnType();

        ExpressionContext.RenderCommand expressionRenderCommand;

        try {
            ClassElement observableValueElement = bindingContext.getClassElement(ObservableValue.class);

            Continuation continuation;
            if (ElementUtils.isAssignable(observableValueElement, returnTypeElement)) {
                returnTypeElement.isGenericPlaceholder();
                List<? extends ClassElement> genericTypes = returnTypeElement.getBoundGenericTypes();
                continuation = Expressions.binding(
                        bindingContext,
                        expression,
                        genericTypes.isEmpty()
                                ? bindingContext.getClassElement(Object.class)
                                : genericTypes.get(0).getType()
                );
            } else {
                continuation = Expressions.expression(bindingContext, expression, returnTypeElement);
            }

            boolean canBeRun = continuation.canBeRun();
            context.info("Expression " + expression + " can " + (canBeRun ? "" : "not ") + "be executed.");

            expressionRenderCommand = continuation.run();
        } catch (CompileErrorException e) {
            context.fail(e.getMessage(), element);
            return;
        }

        String className;

        try {
            BindingExpressionRendererImpl expressionRenderer = bindingContext.getExpressionRenderer();
            if (expressionRenderer != null) {
                OutputStream expressionOutput = context.visitClass(
                        expressionRenderer.getInternalClassName().replace('/', '.'),
                        element
                );

                try (expressionOutput) {
                    expressionOutput.write(expressionRenderer.dispose());
                } catch (IOException e) {
                    context.fail("Failed to close expression output stream: " + e.getMessage(), element);
                    return;
                }
            }

            bindingContext.renderExpression(returnTypeElement, expressionRenderCommand);

            className = bindingContext.getInternalClassName().replace('/', '.');

            OutputStream bindingContextOutput = context.visitClass(
                    className,
                    element
            );

            try (bindingContextOutput) {
                bindingContextOutput.write(bindingContext.dispose());
            } catch (IOException e) {
                context.fail("Failed to close binding output stream: " + e.getMessage(), element);
            }
        } catch (IOException e) {
            context.fail("Failed to visit class: " + e.getMessage(), element);
        }
    }

    @Override
    public String getElementType() {
        // NB: Micronaut cannot get this type from generic correctly, so, override element type getter.
        return Expression.Value.class.getName();
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
