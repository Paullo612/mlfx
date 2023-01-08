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
import org.objectweb.asm.Label;

abstract class NumberComparisonContinuation extends NumberContinuation {

    static final String TYPE_ERROR = "Comparison operations can only be done between numbers.";

    static LiteralVisitor<Dispatcher> literalVisitor(ExpressionContext context) {
        return new LiteralVisitor<>() {

            @Override
            public Dispatcher visit(Literal literal) {
                throw context.compileError(TYPE_ERROR);
            }

            @Override
            public Dispatcher visit(StringLiteral literal) {
                try {
                    return literal.getValue().contains(".")
                            ? new DoubleDispatcher(Double.parseDouble(literal.getValue()))
                            : new LongDispatcher(Long.parseLong(literal.getValue()));
                } catch (NumberFormatException e) {
                    // ignore.
                }

                throw context.compileError(TYPE_ERROR);
            }

            @Override
            public Dispatcher visit(DecimalLiteral literal) {
                return new LongDispatcher(literal.getValue());
            }

            @Override
            public Dispatcher visit(FloatingPointLiteral literal) {
                return new DoubleDispatcher(literal.getValue());
            }
        };
    }

    static LiteralVisitor<ExpressionContinuation> rightLiteralVisitor(
            Dispatcher dispatcher,
            String name,
            NumberComparisonOp op) {
        // NB: We're visiting right literal here, which must already be converted to appropriate type by
        //  |literalVisitor|, so, no error checking here.
        return new LiteralVisitor<>() {

            @Override
            public ExpressionContinuation visit(DecimalLiteral literal) {
                return dispatcher.accept(name, op, literal.getValue());
            }

            @Override
            public ExpressionContinuation visit(FloatingPointLiteral literal) {
                return dispatcher.accept(name, op, literal.getValue());
            }
        };
    }

    interface NumberComparisonOp {

        boolean accept(long left, double right);

        boolean accept(long left, long right);

        boolean accept(double left, double right);

        boolean accept(double left, long right);
    }

    interface Dispatcher {

        ExpressionContinuation accept(String name, NumberComparisonOp op, double right);

        ExpressionContinuation accept(String name, NumberComparisonOp op, long right);

        ExpressionContinuation asContinuation();
    }

    static class LongDispatcher implements Dispatcher {

        private final long value;

        LongDispatcher(long value) {
            this.value = value;
        }

        @Override
        public ExpressionContinuation accept(String name, NumberComparisonOp op, double right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }

        @Override
        public ExpressionContinuation accept(String name, NumberComparisonOp op, long right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }

        @Override
        public ExpressionContinuation asContinuation() {
            return new DecimalLiteralContinuation(String.valueOf(value), value);
        }
    }

    static class DoubleDispatcher implements Dispatcher {

        private final double value;

        DoubleDispatcher(double value) {
            this.value = value;
        }

        @Override
        public ExpressionContinuation accept(String name, NumberComparisonOp op, double right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }

        @Override
        public ExpressionContinuation accept(String name, NumberComparisonOp op, long right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }

        @Override
        public ExpressionContinuation asContinuation() {
            return new FloatingPointLiteralContinuation(String.valueOf(value), value);
        }
    }

    NumberComparisonContinuation(
            String name,
            ExpressionContinuation left,
            ExpressionContinuation right,
            ExpressionContext context) {
        super(name, left, right, context);
    }

    abstract int getOperation();

    @Override
    public ExpressionContext.RenderCommand createCommand(ClassElement classElement) {
        int operation = getOperation();

        return methodVisitor -> {
            Label trueResult = methodVisitor.newLabel();
            Label out = methodVisitor.newLabel();
            methodVisitor.ifCmp(RenderUtils.type(classElement), operation, trueResult);
            methodVisitor.push(false);
            methodVisitor.goTo(out);
            methodVisitor.mark(trueResult);
            methodVisitor.push(true);
            methodVisitor.mark(out);
        };
    }

    @Override
    String getTypeError() {
        return TYPE_ERROR;
    }

    @Override
    public ClassElement getClassElement() {
        return PrimitiveElement.BOOLEAN;
    }
}
