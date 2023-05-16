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
package io.github.paullo612.mlfx.expression.test;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyIntegerProperty;

public interface Properties {

    String getProperty1();

    void setProperty1(String property1);

    String getProperty2();

    void setProperty2(String value);

    Property<String> property2Property();

    int getProperty3();

    ReadOnlyIntegerProperty property3Property(int garbageHere);

    int getProperty4();

    ReadOnlyIntegerProperty property4Property();

    int getProperty5();

    void setProperty5(int value);

    IntegerProperty property5Property();

    void setWriteOnly(String writeOnly);
}
