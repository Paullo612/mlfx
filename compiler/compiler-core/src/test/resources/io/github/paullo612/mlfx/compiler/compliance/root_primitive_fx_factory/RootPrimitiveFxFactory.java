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
package io.github.paullo612.mlfx.compiler.compliance.root_primitive_fx_factory;

import io.github.paullo612.mlfx.api.CompileFXML;

// If instance declaration is a root element, and factory return value is primitive, such a value requires cast to
//  wrapper type, as loader always returns an Object.
@CompileFXML(fxmlDirectories = "io/github/paullo612/mlfx/compiler/compliance/root_primitive_fx_factory")
class RootPrimitiveFxFactory { }