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
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

class NegateContinuation extends NumberBoxableContinuation {

    static final String TYPE_ERROR = "Negate expression can only be applied to a number or String.";

    static LiteralVisitor<ExpressionContinuation> previousLiteralVisitor(String name, ExpressionContext context) {
        return new LiteralVisitor<>() {
            @Override
            public ExpressionContinuation visit(Literal literal) {
                throw context.compileError(TYPE_ERROR);
            }

            @Override
            public ExpressionContinuation visit(StringLiteral literal) {
                String value = literal.getValue();

                return new StringLiteralContinuation(
                        name, context, value.startsWith("-") ? value.substring(1) : ("-" + value)
                );
            }

            @Override
            public ExpressionContinuation visit(DecimalLiteral literal) {
                return new DecimalLiteralContinuation(name, -literal.getValue());
            }

            @Override
            public ExpressionContinuation visit(FloatingPointLiteral literal) {
                return new FloatingPointLiteralContinuation(name, -literal.getValue());
            }
        };
    }

    private final ExpressionContinuation previous;

    private ClassElement classElement;

    NegateContinuation(String name, ExpressionContinuation previous, ExpressionContext context) {
        super(name, context);

        this.previous = previous;
    }

    @Override
    public ClassElement getClassElement() {
        if (this.classElement == null) {
            ClassElement previousClasElement = previous.getClassElement();

            ClassElement classElement = unbox(previousClasElement);
            if (classElement == null) {
                // Not a number. Fallback to string operations.
                classElement = context.getClassElement(String.class);
            }

            this.classElement = classElement;
        }

        return this.classElement;
    }

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        ClassElement classElement = getClassElement();
        ClassElement previousClasElement = previous.getClassElement();

        if (!ElementUtils.isPrimitive(classElement)) {
            // Not a number. Fallback to string operations.
            ClassElement stringClassElement = context.getClassElement(String.class);

            ExpressionContext.RenderCommand stringCoerceCommand =
                    !ElementUtils.isAssignable(previousClasElement, stringClassElement)
                            ? RenderUtils.coerceOrThrow(context, previousClasElement, stringClassElement)
                            : null;

            // source != null
            //         ? (source.startsWith("-") ? source.substring(1) : "-".concat(source))
            //         : "-0";

            return methodVisitor -> {
                Type stringObject = Type.getType(String.class);

                if (stringCoerceCommand != null) {
                    stringCoerceCommand.render(methodVisitor);
                }
                Label nullLabel = methodVisitor.newLabel();
                Label out = methodVisitor.newLabel();
                methodVisitor.dup();
                methodVisitor.ifNull(nullLabel);

                methodVisitor.dup();
                methodVisitor.push("-");
                methodVisitor.invokeVirtual(
                        stringObject,
                        new Method("startsWith", "(" + RenderUtils.STRING_D + ")Z")
                );
                Label notStartsWith = methodVisitor.newLabel();
                methodVisitor.ifZCmp(GeneratorAdapter.EQ, notStartsWith);
                methodVisitor.push(1);
                methodVisitor.invokeVirtual(
                        stringObject,
                        new Method("substring", "(I)" + RenderUtils.STRING_D)
                );
                methodVisitor.goTo(out);
                methodVisitor.mark(notStartsWith);
                methodVisitor.push("-");
                methodVisitor.swap();
                methodVisitor.invokeVirtual(
                        stringObject,
                        new Method(
                                "concat",
                                "(" + RenderUtils.STRING_D + ")" + RenderUtils.STRING_D
                        )
                );
                methodVisitor.goTo(out);

                methodVisitor.mark(nullLabel);
                methodVisitor.pop();
                methodVisitor.push("-0");
                methodVisitor.mark(out);
            };
        }

        ExpressionContext.RenderCommand coerceCommand = !ElementUtils.isAssignable(previousClasElement, classElement)
                ? RenderUtils.coerceOrThrow(context, previousClasElement, classElement)
                : null;

        return methodVisitor -> {
            if (coerceCommand != null) {
                coerceCommand.render(methodVisitor);
            }

            methodVisitor.math(GeneratorAdapter.NEG, RenderUtils.type(classElement));
        };

    }
}
