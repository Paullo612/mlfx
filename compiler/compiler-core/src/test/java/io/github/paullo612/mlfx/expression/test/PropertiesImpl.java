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
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class PropertiesImpl implements Properties {

    public static final String PROPERTY1_VALUE = "property1";
    public static final String PROPERTY2_VALUE = "property2";

    public static final int PROPERTY3_VALUE = 1;
    public static final int PROPERTY4_VALUE = 42;
    public static final int PROPERTY5_VALUE = 7;

    private String property1 = PROPERTY1_VALUE;
    private final Property<String> property2 = new SimpleStringProperty(PROPERTY2_VALUE);

    private final int property3 = PROPERTY3_VALUE;
    private final IntegerProperty property4 = new SimpleIntegerProperty(PROPERTY4_VALUE);
    private final IntegerProperty property5 = new SimpleIntegerProperty(PROPERTY5_VALUE);

    public String writeOnly;

    @Override
    public String getProperty1() {
        return property1;
    }

    @Override
    public void setProperty1(String property1) {
        this.property1 = property1;
    }

    @Override
    public String getProperty2() {
        return property2.getValue();
    }

    @Override
    public void setProperty2(String value) {
        property2.setValue(value);
    }

    @Override
    public Property<String> property2Property() {
        return property2;
    }

    @Override
    public int getProperty3() {
        return property3;
    }

    @Override
    public ReadOnlyIntegerProperty property3Property(int garbageHere) {
        return null;
    }

    @Override
    public int getProperty4() {
        return property4.get();
    }

    @Override
    public ReadOnlyIntegerProperty property4Property() {
        return property4;
    }

    @Override
    public int getProperty5() {
        return property5.get();
    }

    @Override
    public void setProperty5(int value) {
        property5.setValue(value);
    }

    @Override
    public IntegerProperty property5Property() {
        return property5;
    }

    @Override
    public void setWriteOnly(String writeOnly) {
        this.writeOnly = writeOnly;
    }
}
