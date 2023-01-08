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
package io.github.paullo612.mlfx.compiler;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class TaskFactory {

    private static class TaskImpl implements CompileTask {

        private static class Result {
            private final Charset charset;
            private final CompiledFXMLLoaderReference reference;

            Result(Charset charset, CompiledFXMLLoaderReference reference) {
                this.charset = charset;
                this.reference = reference;
            }
        }

        private final TaskFactory factory;
        private final URI location;
        private final ClassElement targetType;
        private TaskImpl parent;
        private Result result;

        TaskImpl(TaskFactory factory, URI location, ClassElement targetType) {
            this.factory = factory;
            this.location = location;
            this.targetType = targetType;
        }

        private void checkForCycles() {
            TaskImpl current = factory.current;

            while (current != null) {
                if (current == this) {
                    throw new CompileErrorException("Cycle detected.");
                }

                current = current.parent;
            }
        }

        @Override
        public CompiledFXMLLoaderReference compile(Charset charset) {
            if (result != null) {
                if (!result.charset.equals(charset)) {
                    throw new CompileErrorException(
                            "FXML file \"" + location + "\" is already compiled with charset \"" + result.charset
                                    + "\"."
                    );
                }

                return result.reference;
            }

            checkForCycles();

            this.parent = factory.current;
            factory.current = this;

            CompiledFXMLLoaderReference reference;
            try {

                try {
                    reference = factory.doCompile(location.toURL(), charset, targetType);
                } catch (MalformedURLException e) {
                    throw new AssertionError();
                }

                this.result = new Result(charset, reference);
            } finally {
                factory.current = this.parent;
                this.parent = null;
            }

            return reference;
        }
    }

    private final FXMLCompiler compiler;
    private final URI sourceRoot;
    private final FXMLCompiler.Delegate compilerDelegate;
    private TaskImpl current;

    private final Map<URI, CompileTask> tasks = new HashMap<>();

    TaskFactory(VisitorContext visitorContext, URI sourceRoot, FXMLCompiler.Delegate compilerDelegate) {
        this.compiler = new FXMLCompiler(visitorContext, this);
        this.sourceRoot = sourceRoot.normalize();
        this.compilerDelegate = compilerDelegate;
    }

    private CompileTask.CompiledFXMLLoaderReference doCompile(URL location, Charset charset, ClassElement targetType) {
        return compiler.compile(location, charset, targetType, compilerDelegate);
    }

    CompileTask registerTask(URI location, ClassElement targetType) {
        URI actualLocation = sourceRoot.resolve(location);

        return tasks.computeIfAbsent(actualLocation, __ -> new TaskImpl(this, actualLocation, targetType));
    }

    Optional<CompileTask> getTask(URI location) {
        assert current != null : "getTask should only be called from inside other task";

        String locationString = location.toString();

        URI actualLocation = locationString.startsWith("/")
                ? sourceRoot.resolve(locationString.substring(1))
                : current.location.resolve(location);

        return Optional.ofNullable(tasks.get(actualLocation));
    }
}
