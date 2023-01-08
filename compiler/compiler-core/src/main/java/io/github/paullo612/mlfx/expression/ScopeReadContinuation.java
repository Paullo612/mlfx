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

class ScopeReadContinuation implements ExpressionContinuation {

    static final String UNKNOWN_IDENTIFIER_ERROR_FORMAT = "Unknown identifier reference in expression: \"%1$s\".";

    private final String identifier;
    private final ExpressionContext context;

    private ExpressionContext.Loadable loadable;

    public ScopeReadContinuation(String identifier, ExpressionContext context) {
        this.identifier = identifier;
        this.context = context;
    }

    @Override
    public String getName() {
        return identifier;
    }

    @Override
    public boolean canBeRun() {
        return context.getScope().containsKey(identifier);
    }

    ExpressionContext.Loadable getLoadable() {
        if (this.loadable == null) {
            ExpressionContext.Loadable loadable = context.getScope().get(identifier);
            if (loadable == null) {
                throw context.compileError(String.format(UNKNOWN_IDENTIFIER_ERROR_FORMAT, identifier));
            }

            this.loadable = loadable;
        }

        return loadable;
    }

    @Override
    public ClassElement getClassElement() {
        return getLoadable().getClassElement();
    }

    @Override
    public ExpressionContext.RenderCommand run(RenderingAdapter adapter) {
        return getLoadable().load();
    }

    @Override
    public <T> T accept(ExpressionContinuationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
