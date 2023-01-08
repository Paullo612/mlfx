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
package io.github.paullo612.mlfx.compiler

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.Specification

import javax.tools.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class CompileSpec extends Specification {

    // We still want the tests to be runnable from Intellij Idea, so, this one is hardcoded here, sorry.
    static final File TEST_CLASSES_OUTPUT_PATH = new File('target/test-classes')

    static class CompileResult {
        final Boolean result
        final List<Diagnostic<? extends JavaFileObject>> diagnostics

        CompileResult(Boolean result, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
            this.result = result
            this.diagnostics = diagnostics
        }
    }

    CompileResult compile(Path path) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler()

        JavaCompiler.CompilationTask task
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>()

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(collector, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Set.of(new File('./')))

            File[] toCompile

            try (Stream<Path> stream = Files.list(path)) {
                toCompile = stream
                        .filter(file -> Files.isRegularFile(file))
                        .filter(file -> file.toString().endsWith(JavaFileObject.Kind.SOURCE.extension))
                        .sorted(Comparator.comparing(Path::toString))
                        .map(file -> file.toFile())
                        .toArray(File[]::new)
            }

            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(toCompile)

            task = compiler.getTask(
                    null,
                    fileManager,
                    collector,
                    Arrays.asList(
                            '-g',
                            '-parameters',
                            '-A' + CompileFXMLVisitor.RESOURCES_DIRECTORY_OPTION + '=src/test/resources'
                    ),
                    null,
                    compilationUnits
            )

            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(TEST_CLASSES_OUTPUT_PATH))

            TypeElementVisitorProcessor typeElementVisitorProcessor = new TypeElementVisitorProcessor() {

                // Specify where project is
                {
                    visitorAttributes.put(
                            VisitorContext.MICRONAUT_PROCESSING_PROJECT_DIR, Path.of('.')
                    )
                }
            }

            task.setProcessors(List.of(typeElementVisitorProcessor))

            return new CompileResult(task.call(), collector.getDiagnostics())
        }
    }
}
