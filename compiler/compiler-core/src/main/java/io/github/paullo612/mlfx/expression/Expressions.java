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
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Expressions {

    private static class FXMLExpressionVisitorImpl implements FXMLExpressionVisitor<ExpressionContinuation> {

        static final ParseTreeVisitor<ExpressionContinuation> DEFAULT = new FXMLExpressionVisitorImpl();

        private ExpressionContext context;
        private ContinuationContainer container;

        @Override
        public synchronized ExpressionContinuation visitRoot(FXMLExpressionParser.RootContext ctx) {
            context = ctx.context;
            container = new ContinuationContainer();

            visit(ctx.expression());

            try {
                return container;
            } finally {
                context = null;
                container = null;
            }
        }

        @Override
        public ExpressionContinuation visitPropertyReadExpression(
                FXMLExpressionParser.PropertyReadExpressionContext ctx) {
            String identifier = ctx.Identifier().getText();
            ExpressionContext context = this.context;

            if (ctx.e == null) {
                assert container.children.isEmpty() : "Parser grammar error";

                // Load value from scope.
                container.children.addLast(new ScopeReadContinuation(identifier, context));

                return container;
            }

            ExpressionContinuation result = visit(ctx.e);
            ExpressionContinuation previous = container.children.getLast();

            // Load identifier as property.
            container.children.addLast(new PropertyReadContinuation(identifier, previous, context));

            return result;
        }

        @Override
        public ExpressionContinuation visitCollectionExpression(FXMLExpressionParser.CollectionExpressionContext ctx) {
            ExpressionContinuation result = visit(ctx.l);

            assert !container.children.isEmpty() : "Parser grammar error";
            ExpressionContinuation previous = container.children.getLast();

            // Do a sanity check. Load by index can only be applied to string literals.
            String previousStringLiteral = previous.asLiteral()
                    .map(l -> l.accept(CollectionContinuation.previousLiteralVisitor(context)))
                    .orElse(null);

            ContinuationContainer previousContainer = container;
            ContinuationContainer argument = new ContinuationContainer();
            container = argument;

            try {
                visit(ctx.r);
            } finally {
                container = previousContainer;
            }

            if (previousStringLiteral != null) {
                Integer value = argument.asLiteral()
                        .map(l -> l.accept(CollectionContinuation.argumentLiteralVisitor(context)))
                        .orElse(null);

                if (value != null) {
                    // Do a compile time optimization. Load character at compile time.
                    String character = CollectionContinuation.loadCharacter(context, previousStringLiteral, value);

                    // Replace top of the stack with string literal.
                    container.children.removeLast();
                    container.children.addLast(new StringLiteralContinuation(ctx.getText(), context, character));
                    return result;
                }
            }

            container.children.addLast(new CollectionContinuation(ctx.getText(), context, previous, argument));

            return result;
        }

        @Override
        public ExpressionContinuation visitMethodCallExpression(FXMLExpressionParser.MethodCallExpressionContext ctx) {
            ExpressionContinuation result = visit(ctx.l);

            assert !container.children.isEmpty() : "Parser grammar error";
            ExpressionContinuation previous = container.children.getLast();

            List<ExpressionContinuation> arguments = ctx.args.stream()
                    .<ExpressionContinuation>map(arg -> {
                        ContinuationContainer currentContainer = container;
                        ContinuationContainer argContinuation = new ContinuationContainer();
                        container = argContinuation;

                        try {
                            visit(arg);
                        } finally {
                            container = currentContainer;
                        }

                        return argContinuation;
                    })
                    .collect(Collectors.toList());

            // Call the method.
            container.children.addLast(new MethodCallContinuation(ctx, context, previous, arguments));
            return result;
        }

        @Override
        public ExpressionContinuation visitNullLiteral(FXMLExpressionParser.NullLiteralContext ctx) {
            String value = ctx.getText();

            container.children.addLast(new NullLiteralContinuation(value, context));

            return container;
        }

        @Override
        public ExpressionContinuation visitStringLiteral(FXMLExpressionParser.StringLiteralContext ctx) {
            String value = ctx.getText().substring(1, ctx.getText().length() - 1);

            container.children.addLast(new StringLiteralContinuation(ctx.getText(), context, value));

            return container;
        }

        @Override
        public ExpressionContinuation visitDecimalLiteral(FXMLExpressionParser.DecimalLiteralContext ctx) {
            long value = Long.parseLong(ctx.getText());

            container.children.addLast(new DecimalLiteralContinuation(ctx.getText(), value));

            return container;
        }

        @Override
        public ExpressionContinuation visitFloatingPointLiteral(FXMLExpressionParser.FloatingPointLiteralContext ctx) {
            double value = Double.parseDouble(ctx.getText());

            container.children.addLast(new FloatingPointLiteralContinuation(ctx.getText(), value));

            return container;
        }

        @Override
        public ExpressionContinuation visitBooleanLiteral(FXMLExpressionParser.BooleanLiteralContext ctx) {
            boolean value = Boolean.parseBoolean(ctx.getText());

            container.children.addLast(new BooleanLiteralContinuation(ctx.getText(), value));

            return container;
        }

        @Override
        public ExpressionContinuation visitSubstractOrNegateExpression(
                FXMLExpressionParser.SubstractOrNegateExpressionContext ctx) {
            ExpressionContinuation result = visit(ctx.e);

            assert !container.children.isEmpty() : "Parser grammar error";
            ExpressionContinuation previous = container.children.getLast();

            switch (ctx.t.getType()) {
                case FXMLExpressionLexer.Not: {
                    ExpressionContinuation notOp = previous.asLiteral()
                            .map(l -> l.accept(NotContinuation.previousLiteralVisitor(ctx.getText(), context)))
                            .orElse(null);

                    if (notOp != null) {
                        // Replace top of the stack with negated boolean literal.
                        container.children.removeLast();
                        container.children.addLast(notOp);

                        return result;
                    }

                    container.children.addLast(new NotContinuation(ctx.getText(), previous, context));
                    break;
                }
                case FXMLExpressionLexer.Subtract: {
                    ExpressionContinuation negateOp = previous.asLiteral()
                            .map(l -> l.accept(NegateContinuation.previousLiteralVisitor(ctx.getText(), context)))
                            .orElse(null);

                    if (negateOp != null) {
                        // Replace top of the stack with negated literal.
                        container.children.removeLast();
                        container.children.addLast(negateOp);

                        return result;
                    }

                    container.children.addLast(new NegateContinuation(ctx.getText(), previous, context));
                    break;
                }
                default:
                    throw new AssertionError();
            }

            return result;
        }

        private ExpressionContinuation visitMathExpression(
                FXMLExpressionParser.ExpressionContext l,
                FXMLExpressionParser.ExpressionContext r,
                String text,
                Function<ExpressionContext, MathContinuation.MathOp> mathOpFactory,
                BinaryBoxableContinuation.Factory continuationFactory
        ) {
            ExpressionContinuation result = visit(l);

            assert !container.children.isEmpty() : "Parser grammar error";
            ExpressionContinuation left = container.children.getLast();

            MathContinuation.Dispatcher leftDispatcher = left.asLiteral()
                    .map(literal -> literal.accept(MathContinuation.literalVisitor(context)))
                    .orElse(null);

            if (leftDispatcher != null) {
                left = leftDispatcher.asContinuation();
            }

            ContinuationContainer previousContainer = container;
            ContinuationContainer right = new ContinuationContainer();
            container = right;

            try {
                visit(r);
            } finally {
                container = previousContainer;
            }

            ExpressionContinuation rightExpression = right.asLiteral()
                    .map(literal -> literal.accept(MathContinuation.literalVisitor(context)))
                    .map(MathContinuation.Dispatcher::asContinuation)
                    .orElse(right);

            if (leftDispatcher != null) {
                ExpressionContinuation compileTimeOp = rightExpression.asLiteral()
                        .map(literal -> literal.accept(
                                        MathContinuation.rightLiteralVisitor(
                                                leftDispatcher, text, mathOpFactory.apply(context)
                                        )
                                )
                        )
                        .orElse(null);

                // Left is a literal. Remove it from the stack.
                container.children.removeLast();

                if (compileTimeOp != null) {
                    // Put literal computed at compile time on the stack.
                    container.children.addLast(compileTimeOp);

                    return result;
                }
            }

            container.children.addLast(continuationFactory.make(text, left, rightExpression, context));

            return result;
        }

        @Override
        public ExpressionContinuation visitMultiplyOrDivideOrRemainderExpression(
                FXMLExpressionParser.MultiplyOrDivideOrRemainderExpressionContext ctx) {
            Function<ExpressionContext, MathContinuation.MathOp> mathOpFactory;
            BinaryBoxableContinuation.Factory continuationFactory;

            switch (ctx.t.getType()) {
                case FXMLExpressionLexer.Multiply:
                    mathOpFactory = MultiplyContinuation::op;
                    continuationFactory = MultiplyContinuation::new;
                    break;
                case FXMLExpressionLexer.Divide:
                    mathOpFactory = DivideContinuation::op;
                    continuationFactory = DivideContinuation::new;
                    break;
                case FXMLExpressionLexer.Remainder:
                    mathOpFactory = RemainderContinuation::op;
                    continuationFactory = RemainderContinuation::new;
                    break;
                default:
                    throw new AssertionError();
            }

            return visitMathExpression(ctx.l, ctx.r, ctx.getText(), mathOpFactory, continuationFactory);
        }

        @Override
        public ExpressionContinuation visitAddOrSubtractExpression(
                FXMLExpressionParser.AddOrSubtractExpressionContext ctx) {
            Function<ExpressionContext, MathContinuation.MathOp> mathOpFactory;
            BinaryBoxableContinuation.Factory continuationFactory;

            switch (ctx.t.getType()) {
                case FXMLExpressionLexer.Add:
                    mathOpFactory = AddContinuation::op;
                    continuationFactory = AddContinuation::new;
                    break;
                case FXMLExpressionLexer.Subtract:
                    mathOpFactory = SubtractContinuation::op;
                    continuationFactory = SubtractContinuation::new;
                    break;
                default:
                    throw new AssertionError();
            }

            return visitMathExpression(ctx.l, ctx.r, ctx.getText(), mathOpFactory, continuationFactory);
        }

        private ExpressionContinuation visitComparisonExpression(
                FXMLExpressionParser.ExpressionContext l,
                FXMLExpressionParser.ExpressionContext r,
                String text,
                Supplier<NumberComparisonContinuation.NumberComparisonOp> comparisonOpFactory,
                BinaryBoxableContinuation.Factory continuationFactory
        ) {
            ExpressionContinuation result = visit(l);

            assert !container.children.isEmpty() : "Parser grammar error";
            ExpressionContinuation left = container.children.getLast();

            NumberComparisonContinuation.Dispatcher leftDispatcher = left.asLiteral()
                    .map(literal -> literal.accept(NumberComparisonContinuation.literalVisitor(context)))
                    .orElse(null);

            if (leftDispatcher != null) {
                left = leftDispatcher.asContinuation();
            }

            ContinuationContainer previousContainer = container;
            ContinuationContainer right = new ContinuationContainer();
            container = right;

            try {
                visit(r);
            } finally {
                container = previousContainer;
            }

            ExpressionContinuation rightExpression = right.asLiteral()
                    .map(literal -> literal.accept(NumberComparisonContinuation.literalVisitor(context)))
                    .map(NumberComparisonContinuation.Dispatcher::asContinuation)
                    .orElse(right);

            if (leftDispatcher != null) {
                ExpressionContinuation compileTimeOp = rightExpression.asLiteral()
                        .map(literal -> literal.accept(
                                NumberComparisonContinuation.rightLiteralVisitor(
                                                leftDispatcher, text, comparisonOpFactory.get()
                                        )
                                )
                        )
                        .orElse(null);

                // Left is a literal. Remove it from the stack.
                container.children.removeLast();

                if (compileTimeOp != null) {
                    // Put literal computed at compile time on the stack.
                    container.children.addLast(compileTimeOp);

                    return result;
                }
            }

            container.children.addLast(continuationFactory.make(text, left, rightExpression, context));

            return result;
        }

        @Override
        public ExpressionContinuation visitComparisonExpression(FXMLExpressionParser.ComparisonExpressionContext ctx) {
            Supplier<NumberComparisonContinuation.NumberComparisonOp> comparisonOpFactory;
            BinaryBoxableContinuation.Factory continuationFactory;

            switch (ctx.t.getType()) {
                case FXMLExpressionLexer.GT:
                    comparisonOpFactory = GreaterThanContinuation::op;
                    continuationFactory = GreaterThanContinuation::new;
                    break;
                case FXMLExpressionLexer.GE:
                    comparisonOpFactory = GreaterThanOrEqualContinuation::op;
                    continuationFactory = GreaterThanOrEqualContinuation::new;
                    break;
                case FXMLExpressionLexer.LT:
                    comparisonOpFactory = LowerThanContinuation::op;
                    continuationFactory = LowerThanContinuation::new;
                    break;
                case FXMLExpressionLexer.LE:
                    comparisonOpFactory = LowerThanOrEqualContinuation::op;
                    continuationFactory = LowerThanOrEqualContinuation::new;
                    break;
                default:
                    throw new AssertionError();
            }

            return visitComparisonExpression(
                    ctx.l, ctx.r, ctx.getText(), comparisonOpFactory, continuationFactory
            );
        }

        private ExpressionContinuation visitEqualityExpression(
                FXMLExpressionParser.ExpressionContext l,
                FXMLExpressionParser.ExpressionContext r,
                String text,
                Supplier<EqualityContinuation.AnyComparisonOp> equalityOpFactory,
                BinaryBoxableContinuation.Factory continuationFactory
        ) {
            ExpressionContinuation result = visit(l);

            assert !container.children.isEmpty() : "Parser grammar error";
            ExpressionContinuation left = container.children.getLast();

            EqualityContinuation.Dispatcher dispatcher = left.asLiteral()
                    .map(literal -> literal.accept(EqualityContinuation.leftLiteralVisitor()))
                    .orElse(null);

            ContinuationContainer previousContainer = container;
            ContinuationContainer right = new ContinuationContainer();
            container = right;

            try {
                visit(r);
            } finally {
                container = previousContainer;
            }

            if (dispatcher != null) {
                ExpressionContinuation compileTimeOp = right.asLiteral()
                        .map(literal -> literal.accept(
                                EqualityContinuation.rightLiteralVisitor(
                                                dispatcher, text, equalityOpFactory.get(), context
                                        )
                                )
                        )
                        .orElse(null);

                // Left is a literal. Remove it from the stack.
                container.children.removeLast();

                if (compileTimeOp != null) {
                    // Put literal computed at compile time on the stack.
                    container.children.addLast(compileTimeOp);

                    return result;
                }
            }

            container.children.addLast(continuationFactory.make(text, left, right, context));

            return result;
        }

        @Override
        public ExpressionContinuation visitEqualityExpression(FXMLExpressionParser.EqualityExpressionContext ctx) {
            Supplier<EqualityContinuation.AnyComparisonOp> equalityOpFactory;
            BinaryBoxableContinuation.Factory continuationFactory;

            switch (ctx.t.getType()) {
                case FXMLExpressionLexer.EqualTo:
                    equalityOpFactory = EqualToContinuation::op;
                    continuationFactory = EqualToContinuation::new;
                    break;
                case FXMLExpressionLexer.NotEqualTo:
                    equalityOpFactory = NotEqualToContinuation::op;
                    continuationFactory = NotEqualToContinuation::new;
                    break;
                default:
                    throw new AssertionError();
            }

            return visitEqualityExpression(ctx.l, ctx.r, ctx.getText(), equalityOpFactory, continuationFactory);
        }

        @Override
        public ExpressionContinuation visitOrExpression(FXMLExpressionParser.OrExpressionContext ctx) {
            ExpressionContinuation result = visit(ctx.l);

            assert !container.children.isEmpty() : "Parser grammar error";
            ExpressionContinuation left = container.children.getLast();

            Boolean leftResult = left.asLiteral()
                    .map(literal -> literal.accept(BooleanContinuation.literalVisitor(context)))
                    .orElse(null);

            if (Boolean.TRUE.equals(leftResult)) {
                // Left is a true literal. Let's render it.
                container.children.removeLast();
                container.children.addLast(new BooleanLiteralContinuation(ctx.getText(), true));
                return result;
            }

            ContinuationContainer previousContainer = container;
            ContinuationContainer right = new ContinuationContainer();
            container = right;

            try {
                visit(ctx.r);
            } finally {
                container = previousContainer;
            }

            Boolean rightResult = right.asLiteral()
                    .map(literal -> literal.accept(BooleanContinuation.literalVisitor(context)))
                    .orElse(null);

            if (leftResult != null) {
                // Left is a false literal. Remove it from the stack.
                container.children.removeLast();

                if (rightResult != null) {
                    // Put literal computed at compile time on the stack.
                    container.children.addLast(new BooleanLiteralContinuation(ctx.getText(), rightResult));
                } else {
                    // Left is a false literal, so, we can just replace container with right one.
                    container = right;
                }

                return result;
            }

            if (Boolean.TRUE.equals(rightResult) ) {
                // Right part is true literal. Or expression is always evaluates to true.
                container.children.removeLast();
                container.children.addLast(new BooleanLiteralContinuation(ctx.getText(), true));
                return result;
            }

            if (Boolean.FALSE.equals(rightResult)) {
                // Right part is false literal. There is no sense in rendering a special continuation.
                return result;
            }

            container.children.addLast(new OrContinuation(ctx.getText(), left, right, context));

            return result;
        }

        @Override
        public ExpressionContinuation visitAndExpression(FXMLExpressionParser.AndExpressionContext ctx) {
            ExpressionContinuation result = visit(ctx.l);

            assert !container.children.isEmpty() : "Parser grammar error";
            ExpressionContinuation left = container.children.getLast();

            Boolean leftResult = left.asLiteral()
                    .map(literal -> literal.accept(BooleanContinuation.literalVisitor(context)))
                    .orElse(null);

            if (Boolean.FALSE.equals(leftResult)) {
                // Left is a false literal. Let's render it.
                container.children.removeLast();
                container.children.addLast(new BooleanLiteralContinuation(ctx.getText(), false));
                return result;
            }

            ContinuationContainer previousContainer = container;
            ContinuationContainer right = new ContinuationContainer();
            container = right;

            try {
                visit(ctx.r);
            } finally {
                container = previousContainer;
            }

            Boolean rightResult = right.asLiteral()
                    .map(literal -> literal.accept(BooleanContinuation.literalVisitor(context)))
                    .orElse(null);

            if (leftResult != null) {
                // Left is a true literal. Remove it from the stack.
                container.children.removeLast();

                if (rightResult != null) {
                    // Put literal computed at compile time on the stack.
                    container.children.addLast(new BooleanLiteralContinuation(ctx.getText(), rightResult));
                } else {
                    // Left is a true literal, so, we can just replace container with right one.
                    container = right;
                }

                return result;
            }

            if (Boolean.FALSE.equals(rightResult)) {
                // Right part is false literal. And expression is always evaluates to false.
                container.children.removeLast();
                container.children.addLast(new BooleanLiteralContinuation(ctx.getText(), false));
                return result;
            }

            if (Boolean.TRUE.equals(rightResult)) {
                // Right part is true literal. There is no sense in rendering a special continuation.
                return result;
            }

            container.children.addLast(new AndContinuation(ctx.getText(), left, right, context));

            return result;
        }

        @Override
        public ExpressionContinuation visitParenthesisedExpression(
                FXMLExpressionParser.ParenthesisedExpressionContext ctx) {
            return ctx.e.accept(this);
        }

        @Override
        public ExpressionContinuation visit(ParseTree tree) {
            return tree.accept(this);
        }

        @Override
        public ExpressionContinuation visitChildren(RuleNode node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExpressionContinuation visitTerminal(TerminalNode node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExpressionContinuation visitErrorNode(ErrorNode node) {
            throw new UnsupportedOperationException();
        }
    }

    public enum BindingType {
        STATIC,
        DYNAMIC
    }

    public interface Binding extends TypedContinuation {

        BindingType getBindingType();
    }

    private static ExpressionContinuation parse(ExpressionContext context, String expression) {
        Lexer lexer = new FXMLExpressionLexer(CharStreams.fromString(expression));
        TokenStream tokenStream = new CommonTokenStream(lexer);
        FXMLExpressionParser parser = new FXMLExpressionParser(tokenStream);

        ParseTree tree = parser.root(context);

        return FXMLExpressionVisitorImpl.DEFAULT.visit(tree);
    }

    public static TypedContinuation expression(ExpressionContext context, String expression) {
        ExpressionContinuation continuation = parse(context, expression);

        return new StaticBindingImpl<>(context, continuation, null);

    }

    public static TypedContinuation expression(ExpressionContext context, String expression, ClassElement targetType) {
        ExpressionContinuation continuation = parse(context, expression);

        return new StaticBindingImpl<>(context, continuation, targetType);

    }

    public static Binding binding(BindingContext context, String expression) {
        ExpressionContinuation continuation = parse(context, expression);

        return new BindingImpl(context, continuation, null, expression);
    }

    public static Binding binding(BindingContext context, String expression, ClassElement targetType) {
        ExpressionContinuation continuation = parse(context, expression);

        return new BindingImpl(context, continuation, targetType, expression);
    }

    private Expressions() {
        super();
    }
}
