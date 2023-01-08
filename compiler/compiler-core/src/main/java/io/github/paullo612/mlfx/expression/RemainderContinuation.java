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

import org.objectweb.asm.commons.GeneratorAdapter;

class RemainderContinuation extends MathContinuation {

    static final String DIVISION_BY_ZERO_ERROR = "Cannot divide by zero.";

    static MathOp op(ExpressionContext context) {
        return new MathOp() {

            @Override
            public double accept(long left, double right) {
                return left % right;
            }

            @Override
            public long accept(long left, long right) {
                if (right == 0L) {
                    throw context.compileError(DIVISION_BY_ZERO_ERROR);
                }

                return left % right;
            }

            @Override
            public double accept(double left, double right) {
                return left % right;
            }

            @Override
            public double accept(double left, long right) {
                return left % right;
            }
        };
    }

    RemainderContinuation(String name,
            ExpressionContinuation left,
            ExpressionContinuation right,
            ExpressionContext context) {
        super(name, left, right, context);
    }

    @Override
    int getOperation() {
        return GeneratorAdapter.REM;
    }
}
