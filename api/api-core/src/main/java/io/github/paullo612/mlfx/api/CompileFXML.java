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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows compiling set of FXML files to a bunch of factories, that require no reflections use at runtime.
 *
 * @author Paullo612
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CompileFXML {

    /**
     * Returns an array of directories on classpath to scan for FXML files.
     *
     * @return an array of directories to scan for FXML files
     */
    String[] fxmlDirectories();

    /**
     * Returns FXML files charset.
     *
     * @return FXML files charset
     */
    String charset() default "UTF-8";
}
