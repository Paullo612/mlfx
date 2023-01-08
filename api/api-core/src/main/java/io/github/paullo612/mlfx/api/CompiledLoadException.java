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
package io.github.paullo612.mlfx.api;

/**
 * Thrown when an error is encountered during compiled FXML evaluation.
 *
 * @author Paullo612
 */
public class CompiledLoadException extends Exception {

    static final long serialVersionUID = -2472483119610974606L;

    /**
     * Constructs an {@code CompiledLoadException} with {@code null} as its error detail message.
     */
    public CompiledLoadException() {
        super();
    }

    /**
     * Constructs an {@code CompiledLoadException} with the specified detail message.
     *
     * @param message the detail message
     */
    public CompiledLoadException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code CompiledLoadException} with the specified cause.
     *
     * @param cause the cause
     */
    public CompiledLoadException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs an {@code CompiledLoadException} with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public CompiledLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
