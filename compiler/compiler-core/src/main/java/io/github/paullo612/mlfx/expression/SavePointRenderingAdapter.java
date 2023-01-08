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
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PrimitiveElement;
import javafx.beans.value.ObservableValue;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

class SavePointRenderingAdapter implements RenderingAdapter {

    private static final int CLEAR_VARIABLE_INDEX = 2;

    private static class SavePointInfo {
        final int reservedLabelsCount;
        final ExpressionContext.RenderCommand command;

        SavePointInfo(int reservedLabelsCount, ExpressionContext.RenderCommand command) {
            this.reservedLabelsCount = reservedLabelsCount;
            this.command = command;
        }
    }

    private static class SavePoint {

        private static void renderDefaultValue(GeneratorAdapter methodVisitor, ClassElement classElement) {
            if (classElement.isAssignable(PrimitiveElement.BYTE)
                    || classElement.isAssignable(PrimitiveElement.CHAR)
                    || classElement.isAssignable(PrimitiveElement.SHORT)
                    || classElement.isAssignable(PrimitiveElement.INT)
            ) {
                methodVisitor.push(0);
            } else if (classElement.isAssignable(PrimitiveElement.BOOLEAN)) {
                methodVisitor.push(false);
            } else if (classElement.isAssignable(PrimitiveElement.FLOAT)) {
                methodVisitor.push(0.f);
            } else if (classElement.isAssignable(PrimitiveElement.DOUBLE)) {
                methodVisitor.push(0.d);
            } else if (classElement.isAssignable(PrimitiveElement.LONG)) {
                methodVisitor.push(0L);
            } else {
                methodVisitor.push((Type) null);
            }
        }

        private static void renderListenerAdd(
                GeneratorAdapter methodVisitor,
                boolean renderClear,
                ExpressionContext.RenderCommand loadableCommand,
                ExpressionContext.RenderCommand loadListenerHelperCommand,
                ClassElement loadListenerHelperClassElement,
                MethodElement propertyModelGetter,
                int step) {
            Label out;

            if (renderClear) {
                out = methodVisitor.newLabel();

                methodVisitor.loadLocal(CLEAR_VARIABLE_INDEX);
                methodVisitor.push(0);
                methodVisitor.ifICmp(GeneratorAdapter.NE, out);
            } else {
                out = null;
            }

            loadListenerHelperCommand.render(methodVisitor);
            loadableCommand.render(methodVisitor);
            loadableCommand.render(methodVisitor);

            RenderUtils.renderMethodCall(methodVisitor, propertyModelGetter);

            methodVisitor.push(step);
            methodVisitor.invokeInterface(
                    RenderUtils.type(loadListenerHelperClassElement),
                    new Method(
                            "addListener",
                            "(" + RenderUtils.OBJECT_D + Type.getType(ObservableValue.class).getDescriptor() + "I)V"
                    )
            );
            if (renderClear) {
                methodVisitor.mark(out);
            }
        }

        private static void renderListenerRemove(
                GeneratorAdapter methodVisitor,
                ExpressionContext.RenderCommand loadableCommand,
                ExpressionContext.RenderCommand loadListenerHelperCommand,
                ClassElement loadListenerHelperClassElement,
                int step) {
            loadListenerHelperCommand.render(methodVisitor);
            loadableCommand.render(methodVisitor);
            methodVisitor.push(step);
            methodVisitor.invokeInterface(
                    RenderUtils.type(loadListenerHelperClassElement),
                    new Method("removeListener", "(" + RenderUtils.OBJECT_D + "I)V")
            );
        }

        static class Result {
            final ExpressionContext.Loadable loadable;
            final ExpressionContext.RenderCommand renderCommand;

            Result(ExpressionContext.Loadable loadable, ExpressionContext.RenderCommand renderCommand) {
                this.loadable = loadable;
                this.renderCommand = renderCommand;
            }
        }

        private final BindingContext.BindingExpressionRendererContext rendererContext;
        private final boolean renderClear;
        private final ExpressionContext.Loadable loadable;
        private final int reservedLabelsCount;
        private final List<ExpressionContext.RenderCommand> commands = new ArrayList<>();
        private ClassElement currentType;

        SavePoint(BindingContext.BindingExpressionRendererContext rendererContext, boolean renderClear) {
            this(rendererContext, renderClear, null, 1);
        }

        SavePoint(
                BindingContext.BindingExpressionRendererContext rendererContext,
                boolean renderClear,
                ExpressionContext.Loadable loadable,
                int reservedLabelsCount) {
            this.rendererContext = rendererContext;
            this.renderClear = renderClear;
            this.loadable = loadable;
            this.reservedLabelsCount = reservedLabelsCount;
        }

        void addCommand(ExpressionContext.RenderCommand command, ClassElement resultType) {
            commands.add(command);
            currentType = resultType;
        }

        int getReservedLabelsCount() {
            return reservedLabelsCount;
        }

        private ExpressionContext.RenderCommand storeIfNotClear(ExpressionContext.RenderCommand renderCommand) {
            if (!renderClear) {
                return renderCommand;
            }

            return methodVisitor -> {
                Label defaultValue = methodVisitor.newLabel();
                Label out = methodVisitor.newLabel();

                methodVisitor.loadLocal(CLEAR_VARIABLE_INDEX);
                methodVisitor.push(0);
                methodVisitor.ifICmp(GeneratorAdapter.NE, defaultValue);
                renderCommand.render(methodVisitor);
                methodVisitor.goTo(out);
                methodVisitor.mark(defaultValue);
                renderDefaultValue(methodVisitor, currentType);
                methodVisitor.mark(out);
            };
        }

        Result run(boolean store) {
            if (commands.isEmpty()) {
                return new Result(loadable, null);
            }

            ExpressionContext.RenderCommand loadableCommand = loadable != null ? loadable.load() : null;
            ExpressionContext.RenderCommand renderCommand = methodVisitor -> {
                if (loadableCommand != null) {
                    loadableCommand.render(methodVisitor);
                }
                commands.forEach(c -> c.render(methodVisitor));
            };
            if (!store) {
                return new Result(null, renderCommand);
            }

            assert currentType != null;
            BindingContext.Storable storable = rendererContext.store(currentType);
            return new Result(storable, storable.store(storeIfNotClear(renderCommand)));
        }

        Result run(MethodElement propertyModelGetter, int step) {
            assert loadable != null;
            ExpressionContext.RenderCommand loadableCommand = loadable.load();
            ExpressionContext.RenderCommand loadListenerHelperCommand = rendererContext.getListenerHelper().load();

            assert currentType != null;
            BindingContext.Storable storable = rendererContext.store(currentType);
            ExpressionContext.RenderCommand storeCommand = storable.store(storeIfNotClear(methodVisitor -> {
                loadableCommand.render(methodVisitor);
                commands.forEach(c -> c.render(methodVisitor));
            }));

            ClassElement listenerHelperClassElement = rendererContext.getListenerHelper().getClassElement();
            return new Result(
                    storable,
                    methodVisitor -> {
                        renderListenerRemove(
                                methodVisitor,
                                loadableCommand,
                                loadListenerHelperCommand,
                                listenerHelperClassElement,
                                step
                        );
                        storeCommand.render(methodVisitor);
                        renderListenerAdd(
                                methodVisitor,
                                renderClear,
                                loadableCommand,
                                loadListenerHelperCommand,
                                listenerHelperClassElement,
                                propertyModelGetter,
                                step
                        );
                    }
            );
        }
    }

    private static class ShortCircuitLabels {
        final Label in = new Label();
        final Label out = new Label();
    }

    private static class SavePointContainer {

        private final SavePointContainer parent;
        private final ShortCircuitLabels shortCircuitLabels;
        private final List<SavePointInfo> infos = new ArrayList<>();
        private SavePoint current;
        private int reserved;
        private int reservedByUs;
        private int reservedByChildren;

        SavePointContainer() {
            this(null, null);
        }

        private SavePointContainer(SavePointContainer parent, ShortCircuitLabels shortCircuitLabels) {
            this.parent = parent;
            this.shortCircuitLabels = shortCircuitLabels;
        }

        private int reserve(int count) {
            try {
                return parent == null
                        ? infos.stream().mapToInt(info -> info.reservedLabelsCount).sum()
                        + current.getReservedLabelsCount() + reserved
                        : parent.reserve(count) + infos.size() + 1;
            } finally {
                reservedByChildren += count;
                reserved += count;
            }
        }

        void savePoint(
                ExpressionContext.Loadable loadable,
                BindingContext.BindingExpressionRendererContext rendererContext) {
            current = new SavePoint(rendererContext, parent != null, loadable, 1);
        }

        void savePoint(
                MethodElement propertyModelGetter,
                BindingContext.BindingExpressionRendererContext rendererContext) {
            int step;

            if (parent == null) {
                step = infos.size() + reservedByChildren;
            } else {
                step = parent.reserve(infos.size() - reservedByUs) + infos.size() + reservedByChildren;
                reservedByUs += (infos.size() - reservedByUs);
            }

            SavePoint.Result result = current.run(propertyModelGetter, step);
            infos.add(new SavePointInfo(current.getReservedLabelsCount(), result.renderCommand));
            current = new SavePoint(rendererContext, parent != null, result.loadable, 1);
        }

        private void savePoint(
                int reservedLabelsCount,
                BindingContext.BindingExpressionRendererContext rendererContext) {
            SavePoint.Result result = current.run(true);
            infos.add(new SavePointInfo(current.getReservedLabelsCount(), result.renderCommand));
            current = new SavePoint(rendererContext, parent != null, result.loadable, reservedLabelsCount);
        }

        void addCommand(
                ExpressionContext.RenderCommand renderCommand,
                ClassElement resultType,
                BindingContext.BindingExpressionRendererContext rendererContext) {
            if (reserved != 0) {
                savePoint(reserved, rendererContext);
                reserved = 0;
            }

            if (current == null) {
                current = new SavePoint(rendererContext, parent != null);
            }

            current.addCommand(renderCommand, resultType);
        }

        SavePointContainer derive(ShortCircuitLabels shortCircuitLabels) {
            return new SavePointContainer(this, shortCircuitLabels);
        }

        ExpressionContext.RenderCommand run(BindingContext.BindingExpressionRendererContext rendererContext) {
            SavePoint.Result result = current.run(parent != null);
            infos.add(new SavePointInfo(current.getReservedLabelsCount(), result.renderCommand));

            int index = parent == null ? 0 : parent.reserve(infos.size() - reservedByUs)
                    - reservedByUs - reservedByChildren;

            ExpressionContext.RenderCommand loadCommand = result.loadable != null ? result.loadable.load() : null;

            return methodVisitor -> {
                if (shortCircuitLabels != null) {
                    methodVisitor.mark(shortCircuitLabels.in);
                }

                Label defaultLabel = methodVisitor.newLabel();
                Label[] labels = new Label[infos.stream().mapToInt(i -> i.reservedLabelsCount).sum()];

                for (int i = 0; i < labels.length; ++i) {
                    labels[i] = methodVisitor.newLabel();
                }

                BindingContext.FlagSet.Flag flag = null;

                if (parent != null) {
                    // Fix step counter for current switch if we're running it for the first time.
                    // switch ((flag.check() == (clear != 0)) ? min : step) {
                    //     ...
                    // }
                    flag = rendererContext.createFlag();
                    flag.check().render(methodVisitor);

                    Label clearEqual = methodVisitor.newLabel();
                    Label check = methodVisitor.newLabel();

                    methodVisitor.loadLocal(CLEAR_VARIABLE_INDEX);
                    methodVisitor.push(0);
                    methodVisitor.ifICmp(GeneratorAdapter.EQ, clearEqual);
                    methodVisitor.push(true);
                    methodVisitor.goTo(check);
                    methodVisitor.mark(clearEqual);
                    methodVisitor.push(false);
                    methodVisitor.mark(check);

                    Label step = methodVisitor.newLabel();
                    Label out = methodVisitor.newLabel();

                    methodVisitor.ifCmp(Type.getType(boolean.class), GeneratorAdapter.NE, step);
                    methodVisitor.push(index);
                    methodVisitor.goTo(out);
                    methodVisitor.mark(step);
                    methodVisitor.loadArg(0);
                    methodVisitor.mark(out);
                } else {
                    // switch (step) {
                    //     ...
                    // }
                    methodVisitor.loadArg(0);
                }

                methodVisitor.visitTableSwitchInsn(index, index + labels.length - 1, defaultLabel, labels);

                ListIterator<SavePointInfo> savePointIt = infos.listIterator();
                Iterator<Label> labelIt = Arrays.asList(labels).iterator();

                while (savePointIt.hasNext()) {
                    boolean isFirst = !savePointIt.hasPrevious();
                    SavePointInfo info = savePointIt.next();

                    for (int i = 0; i < info.reservedLabelsCount; ++i) {
                        methodVisitor.mark(labelIt.next());
                    }

                    if (info.command != null) {
                        info.command.render(methodVisitor);
                    }

                    if (flag != null && isFirst) {
                        Label out = methodVisitor.newLabel();
                        Label clear = methodVisitor.newLabel();

                        methodVisitor.loadLocal(CLEAR_VARIABLE_INDEX);
                        methodVisitor.push(0);
                        methodVisitor.ifICmp(GeneratorAdapter.NE, clear);
                        flag.set().render(methodVisitor);
                        methodVisitor.goTo(out);
                        methodVisitor.mark(clear);
                        flag.clear().render(methodVisitor);
                        methodVisitor.mark(out);
                    }

                    if (shortCircuitLabels != null && !savePointIt.hasNext()) {
                        Label out = methodVisitor.newLabel();

                        methodVisitor.loadLocal(CLEAR_VARIABLE_INDEX);
                        methodVisitor.push(0);
                        methodVisitor.ifICmp(GeneratorAdapter.EQ, out);
                        methodVisitor.iinc(CLEAR_VARIABLE_INDEX, -1);
                        methodVisitor.goTo(shortCircuitLabels.out);
                        methodVisitor.mark(out);
                    }
                }

                if (parent == null) {
                    Label breakLabel = methodVisitor.newLabel();
                    methodVisitor.goTo(breakLabel);
                    methodVisitor.mark(defaultLabel);
                    methodVisitor.throwException(Type.getType(AssertionError.class), "should never be reached");
                    methodVisitor.mark(breakLabel);
                } else {
                    methodVisitor.mark(defaultLabel);
                }

                if (loadCommand != null) {
                    loadCommand.render(methodVisitor);
                }
            };
        }
    }

    private final ExpressionContinuationVisitor<ExpressionContext.RenderCommand> visitor =
            new ExpressionContinuationVisitor<>() {

                private SavePointContainer savePointContainer;
                private ShortCircuitLabels shortCircuitLabels;

                @Override
                public ExpressionContext.RenderCommand visit(ExpressionContinuation continuation) {
                    ExpressionContext.RenderCommand renderCommand =
                            continuation.run(SavePointRenderingAdapter.this);

                    // Add render command. Underlying implementation will create first save point in case continuation
                    //  container starts from literal.
                    savePointContainer.addCommand(renderCommand, continuation.getClassElement(), rendererContext);

                    return null;
                }

                @Override
                public ExpressionContext.RenderCommand visit(ScopeReadContinuation continuation) {
                    // All continuation containers start from scope read, if first continuation is not a literal
                    savePointContainer.savePoint(rendererContext.capture(continuation.getLoadable()), rendererContext);

                    return null;
                }

                @Override
                public ExpressionContext.RenderCommand visit(PropertyReadContinuation continuation) {
                    // Complete current save point first
                    ExpressionContinuationVisitor.super.visit(continuation);

                    // Make save point if there is a property model
                    continuation.getPropertyModel()
                            .ifPresent(model -> savePointContainer.savePoint(model, rendererContext));

                    return null;
                }

                @Override
                public ExpressionContext.RenderCommand visit(ShortCircuitContinuation continuation) {
                    this.shortCircuitLabels = new ShortCircuitLabels();
                    ShortCircuitLabels shortCircuitLabels = this.shortCircuitLabels;

                    return methodVisitor -> {
                        methodVisitor.iinc(CLEAR_VARIABLE_INDEX, 1);
                        methodVisitor.goTo(shortCircuitLabels.in);
                        methodVisitor.mark(shortCircuitLabels.out);
                    };
                }

                @Override
                public ExpressionContext.RenderCommand visit(ContinuationContainer continuation) {
                    // Do not derive new save point container if continuation container is a literal. We do not want to
                    //  store literals as class fields.
                    ShortCircuitLabels shortCircuitLabels = this.shortCircuitLabels;
                    this.shortCircuitLabels = null;

                    return continuation.asLiteral()
                            .map(__ -> continuation.run(SavePointRenderingAdapter.this))
                            .orElseGet(() -> {
                                SavePointContainer oldSavePointContainer = savePointContainer;
                                SavePointContainer newSavePointContainer = Optional.ofNullable(oldSavePointContainer)
                                        .map(old -> old.derive(shortCircuitLabels))
                                        .orElseGet(SavePointContainer::new);
                                savePointContainer = newSavePointContainer;

                                try {
                                    for (ExpressionContinuation child : continuation.children) {
                                        child.accept(this);
                                    }
                                } finally {
                                    savePointContainer = oldSavePointContainer;
                                }

                                return newSavePointContainer.run(rendererContext);
                            });
                }
            };

    private final BindingContext.BindingExpressionRendererContext rendererContext;
    private boolean isClearVariableInitialized = false;

    SavePointRenderingAdapter(BindingContext.BindingExpressionRendererContext rendererContext) {
        this.rendererContext = rendererContext;
    }

    @Override
    public ExpressionContext.RenderCommand adapt(ExpressionContinuation continuation) {
        boolean isClearVariableInitialized = this.isClearVariableInitialized;
        this.isClearVariableInitialized = true;

        ExpressionContext.RenderCommand renderCommand = continuation.accept(visitor);

        if (!isClearVariableInitialized) {
            return methodVisitor -> {
                Label start = methodVisitor.mark();
                methodVisitor.push(0);
                methodVisitor.storeLocal(CLEAR_VARIABLE_INDEX, Type.getType(int.class));
                renderCommand.render(methodVisitor);
                Label end = methodVisitor.mark();

                methodVisitor.visitLocalVariable("clear", "I", null, start, end, CLEAR_VARIABLE_INDEX);
            };
        }

        return renderCommand;
    }
}
