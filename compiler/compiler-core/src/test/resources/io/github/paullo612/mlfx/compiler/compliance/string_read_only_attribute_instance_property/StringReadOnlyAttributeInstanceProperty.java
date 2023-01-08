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
package io.github.paullo612.mlfx.compiler.compliance.string_read_only_attribute_instance_property;

import io.github.paullo612.mlfx.api.CompileFXML;

// There is a special handling for read only instance property that can be coersed to list of strings. Property value
//  is split by , and applied to list not as single property, but as list of Strings.
@CompileFXML(
        fxmlDirectories = "io/github/paullo612/mlfx/compiler/compliance/string_read_only_attribute_instance_property"
)
class StringReadOnlyAttributeInstanceProperty { }