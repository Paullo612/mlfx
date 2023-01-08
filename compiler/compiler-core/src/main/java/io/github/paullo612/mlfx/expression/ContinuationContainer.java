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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class ContinuationContainer implements ExpressionContinuation {

    final Deque<ExpressionContinuation> children = new ArrayDeque<>();

    @Override
    public boolean canBeRun() {
        return children.stream()
                .allMatch(ExpressionContinuation::canBeRun);
    }

    @Override
    public int requiresMonitoringCount() {
        return children.stream()
                .mapToInt(ExpressionContinuation::requiresMonitoringCount)
                .sum();
    }

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        // Continuation's run method may throw...
        List<ExpressionContext.RenderCommand> commands = children.stream()
                .map(child -> child.run(adapter))
                .collect(Collectors.toList());

        // But render command itself should not.
        return methodVisitor -> commands.forEach(c -> c.render(methodVisitor));
    }

    @Override
    public Optional<Literal> asLiteral() {
        return children.getLast().asLiteral();
    }

    @Override
    public String getName() {
        return children.getLast().getName();
    }

    @Override
    public ClassElement getClassElement() {
        return children.getLast().getClassElement();
    }

    public <T> T accept(ExpressionContinuationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
