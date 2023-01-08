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
import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

class NotContinuation extends AbstractNamedContinuation {

    static final String TYPE_ERROR = "Not expression can only be applied to a boolean.";

    static LiteralVisitor<ExpressionContinuation> previousLiteralVisitor(String name, ExpressionContext context) {
        return new LiteralVisitor<>() {
            @Override
            public ExpressionContinuation visit(Literal literal) {
                throw context.compileError(TYPE_ERROR);
            }

            @Override
            public ExpressionContinuation visit(BooleanLiteral literal) {
                return new BooleanLiteralContinuation(name, !literal.getValue());
            }

            @Override
            public ExpressionContinuation visit(StringLiteral literal) {
                return new BooleanLiteralContinuation(name, !Boolean.parseBoolean(literal.getValue()));
            }
        };
    }

    private final ExpressionContinuation previous;
    private final ExpressionContext context;

    NotContinuation(String name, ExpressionContinuation previous, ExpressionContext context) {
        super(name);

        this.previous = previous;
        this.context = context;
    }

    @Override
    public ClassElement getClassElement() {
        return PrimitiveElement.BOOLEAN;
    }

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        // NB: previous continuation should've already been run. So it's safe to get its class element.
        ClassElement previousClasElement = previous.getClassElement();

        ExpressionContext.RenderCommand coerceCommand =
                !ElementUtils.isAssignable(previousClasElement, PrimitiveElement.BOOLEAN)
                        ? RenderUtils.coerceOrThrow(context, previousClasElement, PrimitiveElement.BOOLEAN)
                        : null;

        return methodVisitor -> {
            if (coerceCommand != null) {
                coerceCommand.render(methodVisitor);
            }
            Label label = methodVisitor.newLabel();
            Label out = methodVisitor.newLabel();
            methodVisitor.ifZCmp(GeneratorAdapter.NE, label);
            methodVisitor.push(true);
            methodVisitor.goTo(out);
            methodVisitor.mark(label);
            methodVisitor.push(false);
            methodVisitor.mark(out);
        };
    }
}
