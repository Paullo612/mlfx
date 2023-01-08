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
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

abstract class EqualityContinuation extends BoxableContinuation implements BinaryBoxableContinuation {

    static final String TYPE_ERROR = "Incompatible types in comparison.";

    static final String TYPE_ERROR_FORMAT = "Incompatible types in comparison: %1$s is not compatible to %2$s.";

    private static boolean isNullLiteral(ExpressionContinuation continuation) {
        return continuation.asLiteral()
                .map(l -> l.accept(new LiteralVisitor<Boolean>() {

                    @Override
                    public Boolean visit(Literal literal) {
                        return false;
                    }

                    @Override
                    public Boolean visit(NullLiteral literal) {
                        return true;
                    }
                }))
                .orElse(false);
    }

    static LiteralVisitor<Dispatcher> leftLiteralVisitor() {
        return new LiteralVisitor<>() {

            @Override
            public Dispatcher visit(StringLiteral literal) {
                return new StringDispatcher(literal.getValue());
            }

            @Override
            public Dispatcher visit(DecimalLiteral literal) {
                return new LongDispatcher(literal.getValue());
            }

            @Override
            public Dispatcher visit(FloatingPointLiteral literal) {
                return new DoubleDispatcher(literal.getValue());
            }

            @Override
            public Dispatcher visit(BooleanLiteral literal) {
                return new BooleanDispatcher(literal.getValue());
            }

            @Override
            public Dispatcher visit(NullLiteral literal) {
                return new StringDispatcher(null);
            }
        };
    }

    static LiteralVisitor<ExpressionContinuation> rightLiteralVisitor(
            Dispatcher dispatcher,
            String name,
            AnyComparisonOp op,
            ExpressionContext context) {
        return new LiteralVisitor<>() {

            @Override
            public ExpressionContinuation visit(StringLiteral literal) {
                return dispatcher.accept(context, name, op, literal.getValue());
            }

            @Override
            public ExpressionContinuation visit(DecimalLiteral literal) {
                return dispatcher.accept(context, name, op, literal.getValue());
            }

            @Override
            public ExpressionContinuation visit(FloatingPointLiteral literal) {
                return dispatcher.accept(context, name, op, literal.getValue());
            }

            @Override
            public ExpressionContinuation visit(BooleanLiteral literal) {
                return dispatcher.accept(context, name, op, literal.getValue());
            }

            @Override
            public ExpressionContinuation visit(NullLiteral literal) {
                return dispatcher.accept(context, name, op, null);
            }
        };
    }

    interface AnyComparisonOp {

        boolean accept(long left, double right);

        boolean accept(long left, long right);

        boolean accept(double left, double right);

        boolean accept(double left, long right);

        boolean accept(boolean left, boolean right);

        boolean accept(String left, String right);
    }

    interface Dispatcher {

        ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, double right);

        ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, long right);

        ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, boolean right);

        ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, String right);
    }

    static class LongDispatcher implements Dispatcher {

        private final long value;

        LongDispatcher(long value) {
            this.value = value;
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, double right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, long right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }

        @Override
        public ExpressionContinuation accept(
                ExpressionContext context,
                String name,
                AnyComparisonOp op,
                boolean right) {
            throw context.compileError(TYPE_ERROR);
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, String right) {
            if (right != null) {
                try {
                    if (right.contains(".")) {
                        return new BooleanLiteralContinuation(name, op.accept(value, Double.parseDouble(right)));
                    } else {
                        return new BooleanLiteralContinuation(name, op.accept(value, Long.parseLong(right)));
                    }
                } catch (NumberFormatException e) {
                    // ignore.
                }
            }

            throw context.compileError(TYPE_ERROR);
        }
    }

    static class DoubleDispatcher implements Dispatcher {

        private final double value;

        DoubleDispatcher(double value) {
            this.value = value;
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, double right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, long right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }

        @Override
        public ExpressionContinuation accept(
                ExpressionContext context,
                String name,
                AnyComparisonOp op,
                boolean right) {
            throw context.compileError(TYPE_ERROR);
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, String right) {
            if (right != null) {
                try {
                    if (right.contains(".")) {
                        return new BooleanLiteralContinuation(name, op.accept(value, Double.parseDouble(right)));
                    } else {
                        return new BooleanLiteralContinuation(name, op.accept(value, Long.parseLong(right)));
                    }
                } catch (NumberFormatException e) {
                    // ignore.
                }
            }

            throw context.compileError(TYPE_ERROR);
        }
    }

    static class BooleanDispatcher implements Dispatcher {

        private final boolean value;

        BooleanDispatcher(boolean value) {
            this.value = value;
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, double right) {
            throw context.compileError(TYPE_ERROR);
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, long right) {
            throw context.compileError(TYPE_ERROR);
        }

        @Override
        public ExpressionContinuation accept(
                ExpressionContext context,
                String name,
                AnyComparisonOp op,
                boolean right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, String right) {
            if (right != null) {
                boolean result = Boolean.parseBoolean(right);

                return new BooleanLiteralContinuation(name, op.accept(value, result));
            }

            throw context.compileError(TYPE_ERROR);
        }
    }

    static class StringDispatcher implements Dispatcher {

        private final String value;

        StringDispatcher(String value) {
            this.value = value;
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, double right) {
            if (value != null) {
                try {
                    if (value.contains(".")) {
                        return new BooleanLiteralContinuation(name, op.accept(Double.parseDouble(value), right));
                    } else {
                        return new BooleanLiteralContinuation(name, op.accept(Long.parseLong(value), right));
                    }
                } catch (NumberFormatException e) {
                    // ignore.
                }
            }

            throw context.compileError(TYPE_ERROR);
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, long right) {
            if (value != null) {
                try {
                    if (value.contains(".")) {
                        return new BooleanLiteralContinuation(name, op.accept(Double.parseDouble(value), right));
                    } else {
                        return new BooleanLiteralContinuation(name, op.accept(Long.parseLong(value), right));
                    }
                } catch (NumberFormatException e) {
                    // ignore.
                }
            }

            throw context.compileError(TYPE_ERROR);
        }

        @Override
        public ExpressionContinuation accept(
                ExpressionContext context,
                String name,
                AnyComparisonOp op,
                boolean right) {
            if (value != null) {
                boolean result = Boolean.parseBoolean(value);

                return new BooleanLiteralContinuation(name, op.accept(result, right));
            }

            throw context.compileError(TYPE_ERROR);
        }

        @Override
        public ExpressionContinuation accept(ExpressionContext context, String name, AnyComparisonOp op, String right) {
            return new BooleanLiteralContinuation(name, op.accept(value, right));
        }
    }

    private final ExpressionContinuation left;
    private final ExpressionContinuation right;

    EqualityContinuation(
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
        return RenderUtils.BOXED_PRIMITIVES;
    }

    @Override
    public ClassElement getClassElement() {
        return PrimitiveElement.BOOLEAN;
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
    public Optional<ExpressionContext.RenderCommand> coerceLiteral(
            ExpressionContinuation sourceExpression,
            ClassElement targetType) {
        return sourceExpression.asLiteral()
                .map(l -> l.accept(new LiteralVisitor<>() {

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
                }));
    }

    private LiteralVisitor<ExpressionContext.RenderCommand> coerceLiteral(ClassElement classElement) {
        // NB: Only null or string literals are applicable here. All other literal types can be represented as
        //  primitives, so, boxed compare will be applied to them.
        return new LiteralVisitor<>() {

            @Override
            public ExpressionContext.RenderCommand visit(NullLiteral literal) {
                return methodVisitor -> methodVisitor.push((Type) null);
            }

            @Override
            public ExpressionContext.RenderCommand visit(StringLiteral literal) {
                return RenderUtils.coerceOrThrow(context, literal.getValue(), classElement);
            }
        };
    }

    abstract ExpressionContext.RenderCommand createBooleanCommand();

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        ClassElement leftClasElement = left.getClassElement();
        ClassElement rightClassElement = right.getClassElement();

        // NB: We still want to check boxed primitive against null literal, so, apply boxed compare logic only if there
        //  are no null literals.
        if (!isNullLiteral(left) && !isNullLiteral(right)) {
            PrimitiveElement leftPrimitive = unbox(leftClasElement);
            PrimitiveElement rightPrimitive = unbox(rightClassElement);

            if (leftPrimitive != null || rightPrimitive != null) {
                // One of values is coercible to primitive. Run boxed compare.
                return runBoxed(adapter, context, left, right, inferCommonType(leftPrimitive, rightPrimitive));
            }
        }

        ExpressionContext.RenderCommand leftRenderCommand;
        if (left.asLiteral().isPresent()) {
            ClassElement finalRightClassElement = rightClassElement;

            leftClasElement = rightClassElement;
            leftRenderCommand = left.asLiteral()
                    .map(l -> l.accept(coerceLiteral(finalRightClassElement)))
                    .orElseThrow(AssertionError::new);
        } else {
            leftRenderCommand = null;
        }

        ExpressionContext.RenderCommand rightRenderCommand;
        if (right.asLiteral().isPresent()) {
            ClassElement finalLeftClassElement = leftClasElement;

            rightClassElement = leftClasElement;
            rightRenderCommand = right.asLiteral()
                    .map(l -> l.accept(coerceLiteral(finalLeftClassElement)))
                    .orElseThrow(AssertionError::new);
        } else {
            rightRenderCommand = adapter.adapt(right);
        }

        if (!ElementUtils.isAssignable(leftClasElement, rightClassElement)
                && !ElementUtils.isAssignable(rightClassElement, leftClasElement)) {
            throw context.compileError(
                    String.format(TYPE_ERROR_FORMAT, leftClasElement.getName(), rightClassElement.getName())
            );
        }

        ClassElement commonType = ElementUtils.isAssignable(leftClasElement, rightClassElement)
                ? rightClassElement
                : leftClasElement;

        if (ElementUtils.isAssignable(leftClasElement, String.class)
                || ElementUtils.isAssignable(rightClassElement, String.class)) {
            // Special case: Compare strings using equals.
            ExpressionContext.RenderCommand booleanCommand = createBooleanCommand();

            return methodVisitor -> {
                if (leftRenderCommand != null) {
                    leftRenderCommand.render(methodVisitor);
                }

                rightRenderCommand.render(methodVisitor);
                methodVisitor.invokeStatic(
                        Type.getType(Objects.class),
                        new Method("equals", "(" + RenderUtils.OBJECT_D + RenderUtils.OBJECT_D + ")Z")
                );

                if (booleanCommand != null) {
                    booleanCommand.render(methodVisitor);
                }
            };

        }

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
