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

import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.Objects;

class NotEqualToContinuation extends EqualityContinuation {

    static AnyComparisonOp op() {
        return new AnyComparisonOp() {

            @Override
            public boolean accept(long left, double right) {
                return left != right;
            }

            @Override
            public boolean accept(long left, long right) {
                return left != right;
            }

            @Override
            public boolean accept(double left, double right) {
                return left != right;
            }

            @Override
            public boolean accept(double left, long right) {
                return left != right;
            }

            @Override
            public boolean accept(boolean left, boolean right) {
                return left != right;
            }

            @Override
            public boolean accept(String left, String right) {
                return !Objects.equals(left, right);
            }
        };
    }

    NotEqualToContinuation(
            String name,
            ExpressionContinuation left,
            ExpressionContinuation right,
            ExpressionContext context) {
        super(name, left, right, context);
    }

    @Override
    ExpressionContext.RenderCommand createBooleanCommand() {
        // Negate boolean on top of the stack.
        return methodVisitor -> {
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

    @Override
    int getOperation() {
        return GeneratorAdapter.NE;
    }
}
