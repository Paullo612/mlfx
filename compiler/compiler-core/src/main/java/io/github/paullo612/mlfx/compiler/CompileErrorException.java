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

// NB: This was a checked exception initially. There were more boilerplate in code, as all CompileErrorException
//  instances are only caught by FXMLCompiler user, but checked exception had to be declared on tons of methods to
//  allow propagation.
public class CompileErrorException extends RuntimeException {

    CompileErrorException(String message) {
        super(message);
    }

    CompileErrorException(Throwable cause) {
        super(cause);
    }

    CompileErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
