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

class AndContinuation extends BooleanContinuation {

    AndContinuation(
            String name,
            ExpressionContinuation left,
            ExpressionContinuation right,
            ExpressionContext context) {
        super(name, left, right, context);
    }

    @Override
    ExpressionContext.RenderCommand doRun(
            ExpressionContext.RenderCommand leftCoerceCommand,
            ExpressionContext.RenderCommand shortCircuitCommand,
            ExpressionContext.RenderCommand rightRenderCommand) {
        return methodVisitor -> {
            if (leftCoerceCommand != null) {
                leftCoerceCommand.render(methodVisitor);
            }

            Label out = methodVisitor.newLabel();

            Label false1Result = methodVisitor.newLabel();
            Label false2Result = shortCircuitCommand != null ? methodVisitor.newLabel() : false1Result;
            methodVisitor.ifZCmp(GeneratorAdapter.EQ, false1Result);
            rightRenderCommand.render(methodVisitor);
            methodVisitor.ifZCmp(GeneratorAdapter.EQ, false2Result);

            methodVisitor.push(true);
            methodVisitor.goTo(out);

            methodVisitor.mark(false1Result);
            if (shortCircuitCommand != null) {
                shortCircuitCommand.render(methodVisitor);
                methodVisitor.mark(false2Result);
            }
            methodVisitor.push(false);

            methodVisitor.mark(out);
        };
    }
}
