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
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MethodCallContinuation extends AbstractNamedContinuation {

    static final String NO_SUCH_METHOD_ERROR_FORMAT = "Unable to find method \"%1$s\" of class \"%2$s\".";
    static final String AMBIGUOUS_METHOD_CALL_ERROR_FORMAT = "Ambiguous method call \"%1$s\".";

    interface RenderCommandSupplier {

        ExpressionContext.RenderCommand get(RenderingAdapter adapter);
    }

    private static class MethodCallCandidate {

        final int score;
        final MethodElement methodElement;
        final List<RenderCommandSupplier> renderCommands;

        MethodCallCandidate(
                int score,
                MethodElement methodElement,
                List<RenderCommandSupplier> renderCommands) {
            this.score = score;
            this.methodElement = methodElement;
            this.renderCommands = renderCommands;
        }
    }

    private final String methodName;
    private final ExpressionContext context;
    private final ExpressionContinuation previous;
    private final List<ExpressionContinuation> arguments;

    private MethodCallCandidate callCandidate;

    MethodCallContinuation(
            FXMLExpressionParser.MethodCallExpressionContext ctx,
            ExpressionContext context,
            ExpressionContinuation previous,
            List<ExpressionContinuation> arguments) {
        super(ctx.getText());

        this.methodName = ctx.Identifier().getText();
        this.context = context;
        this.previous = previous;
        this.arguments = arguments;
    }

    @Override
    public boolean canBeRun() {
        return arguments.stream()
                .allMatch(ExpressionContinuation::canBeRun);
    }

    private Optional<RenderCommandSupplier> tryCoerce(
            ExpressionContinuation argument,
            ClassElement targetType) {
        return argument.asLiteral()
                .<Optional<RenderCommandSupplier>>map(l -> l.accept(new LiteralVisitor<>() {
                    @Override
                    public Optional<RenderCommandSupplier> visit(StringLiteral literal) {
                        // Try to coerce string value. This may result in optimized enum constant load or compile time
                        //  String to int conversion.
                        return RenderUtils.coerce(literal.getValue(), targetType)
                                .map(r -> __ -> r);
                    }

                    @Override
                    public Optional<RenderCommandSupplier> visit(NullLiteral literal) {
                        // A special case. Null literal has Object type, so we must handle it manually.
                        if (ElementUtils.isPrimitive(targetType)) {
                            // Null cannot be cast to primitive type.
                            return Optional.empty();
                        }

                        // Load null as is.
                        return Optional.of(adapter -> adapter.adapt(argument));
                    }

                    @Override
                    public Optional<RenderCommandSupplier> visit(DecimalLiteral literal) {
                        return RenderUtils.coerce(String.valueOf(literal.getValue()), targetType)
                                .map(r -> __ -> r);
                    }

                    @Override
                    public Optional<RenderCommandSupplier> visit(FloatingPointLiteral literal) {
                        return RenderUtils.coerce(String.valueOf(literal.getValue()), targetType)
                                .map(r -> __ -> r);
                    }

                    @Override
                    public Optional<RenderCommandSupplier> visit(BooleanLiteral literal) {
                        return RenderUtils.coerce(String.valueOf(literal.getValue()), targetType)
                                .map(r -> __ -> r);
                    }
                }))
                .orElseGet(() -> RenderUtils.coerce(argument.getClassElement(), targetType)
                        .map(coerceCommand -> adapter -> {
                            ExpressionContext.RenderCommand command = adapter.adapt(argument);

                            return methodVisitor -> {
                                command.render(methodVisitor);
                                coerceCommand.render(methodVisitor);
                            };
                        })
                );
    }

    private Optional<MethodCallCandidate> reduceCallCandidates(List<MethodElement> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Filter and score each candidate. If argument can be passed without coercion, method gets
        //  |argumentCommands.size() - argumentIndex| for each argumentIndex, 0 otherwise.
        List<MethodCallCandidate> scoredCandidates = candidates.stream()
                .flatMap(method -> {
                    int score = 0;
                    List<RenderCommandSupplier> renderCommands = new ArrayList<>(arguments.size());

                    for (int i = 0; i < arguments.size(); ++i) {
                        ExpressionContinuation argument = arguments.get(i);
                        ClassElement parameterClassElement = method.getParameters()[i].getType();

                        if (ElementUtils.isAssignable(argument.getClassElement(), parameterClassElement)) {
                            score += arguments.size() - i;
                            renderCommands.add(adapter -> adapter.adapt(argument));
                            continue;
                        }

                        Optional<RenderCommandSupplier> coercedCommand = tryCoerce(argument, parameterClassElement);
                        if (coercedCommand.isEmpty()) {
                            // Argument is not coercible. Skip candidate.
                            return Stream.empty();
                        }

                        renderCommands.add(coercedCommand.get());
                    }

                    return Stream.of(new MethodCallCandidate(score, method, renderCommands));
                })
                .sorted(Comparator.<MethodCallCandidate>comparingInt(c -> c.score).reversed())
                .collect(Collectors.toList());

        Iterator<MethodCallCandidate> it = scoredCandidates.iterator();
        if (!it.hasNext()) {
            return Optional.empty();
        }

        MethodCallCandidate result = it.next();

        while (it.hasNext()) {
            MethodCallCandidate next = it.next();

            if (next.score == result.score) {
                throw context.compileError(String.format(AMBIGUOUS_METHOD_CALL_ERROR_FORMAT, getName()));
            }
        }

        return Optional.of(result);
    }

    MethodCallCandidate getCallCandidate() {
        if (this.callCandidate == null) {
            ClassElement previousClasElement = previous.getClassElement();

            // Get potential call candidates.
            List<MethodElement> candidates = previousClasElement.getEnclosedElements(
                    ElementQuery.ALL_METHODS
                            .onlyAccessible()
                            .named(Predicate.isEqual(methodName))
                            .filter(m -> m.getParameters().length == arguments.size())
            );

            this.callCandidate = reduceCallCandidates(candidates)
                    .orElseThrow(() -> context.compileError(
                            String.format(NO_SUCH_METHOD_ERROR_FORMAT, methodName, previousClasElement.getName())
                    ));
        }

        return this.callCandidate;
    }

    @Override
    public ClassElement getClassElement() {
        return getCallCandidate().methodElement.getReturnType();
    }

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        MethodCallCandidate candidate = getCallCandidate();

        List<ExpressionContext.RenderCommand> renderCommands = candidate.renderCommands.stream()
                .map(s -> s.get(adapter))
                .collect(Collectors.toList());

        return methodVisitor -> {
            renderCommands.forEach(c -> c.render(methodVisitor));

            RenderUtils.renderMethodCall(methodVisitor, candidate.methodElement);
        };
    }
}
