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

class LowerThanOrEqualContinuation extends NumberComparisonContinuation {

    static NumberComparisonOp op() {
        return new NumberComparisonOp() {

            @Override
            public boolean accept(long left, double right) {
                return left <= right;
            }

            @Override
            public boolean accept(long left, long right) {
                return left <= right;
            }

            @Override
            public boolean accept(double left, double right) {
                return left <= right;
            }

            @Override
            public boolean accept(double left, long right) {
                return left <= right;
            }
        };
    }

    LowerThanOrEqualContinuation(
            String name,
            ExpressionContinuation left,
            ExpressionContinuation right,
            ExpressionContext context) {
        super(name, left, right, context);
    }

    @Override
    int getOperation() {
        return GeneratorAdapter.LE;
    }
}
