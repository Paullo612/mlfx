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
package io.github.paullo612.mlfx.expression

import java.util.function.BooleanSupplier

class BooleanExpressionSpec extends ExpressionSpec {

    def "Simple boolean operation (literal, literal => boolean): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$a $op $b")
    abstract boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        a        | op   | b         | expectedResult
        true     | '&&' | true      | true
        true     | '&&' | false     | false
        false    | '&&' | true      | false
        false    | '&&' | false     | false
        "'true'" | '&&' | "'true'"  | true
        "'true'" | '&&' | 1         | false
        "'true'" | '&&' | 1.0       | false
        1        | '&&' | 1         | false
        true     | '||' | true      | true
        true     | '||' | false     | true
        false    | '||' | true      | true
        false    | '||' | false     | false
    }

    def "Simple boolean operation (literal, #bType => Boolean): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $bType b = $b;
    
    @Expression.Value("$a $op b")
    abstract Boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        bType     | a        | op   | b        | expectedResult
        'boolean' | true     | '&&' | true     | true
        'Boolean' | true     | '&&' | false    | false
        'Boolean' | false    | '&&' | true     | false
        'boolean' | false    | '&&' | false    | false
        'String'  | "'true'" | '&&' | '"true"' | true
        'int'     | "'true'" | '&&' | 1        | false
        'Double'  | "'true'" | '&&' | 1.0      | false
        'boolean' | true     | '||' | true     | true
        'boolean' | true     | '||' | false    | true
        'boolean' | false    | '||' | true     | true
        'boolean' | false    | '||' | false    | false
    }

    def "Simple boolean operation (#aType, literal => boolean): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $aType a = $a;
    
    @Expression.Value("a $op $b")
    abstract boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType     | a        | op   | b        | expectedResult
        'boolean' | true     | '&&' | true     | true
        'Boolean' | true     | '&&' | false    | false
        'Boolean' | false    | '&&' | true     | false
        'boolean' | false    | '&&' | false    | false
        'String'  | '"true"' | '&&' | "'true'" | true
        'int'     | 1        | '&&' | "'true'" | false
        'Double'  | 1.0      | '&&' | "'true'" | false
        'boolean' | true     | '||' | true     | true
        'boolean' | true     | '||' | false    | true
        'boolean' | false    | '||' | true     | true
        'boolean' | false    | '||' | false    | false
    }

    def "Simple boolean operation (#aType, #bType => Boolean): #a #op #b == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final $aType a = $a;
    final $bType b = $b;
    
    @Expression.Value("a $op b")
    abstract Boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType     | bType     | a         | op   | b        | expectedResult
        'boolean' | 'boolean' | true      | '&&' | true     | true
        'boolean' | 'Boolean' | true      | '&&' | false    | false
        'Boolean' | 'Boolean' | false     | '&&' | true     | false
        'boolean' | 'boolean' | false     | '&&' | false    | false
        'String'  | 'String'  | '"true"'  | '&&' | '"true"' | true
        'int'     | 'String'  | 1         | '&&' | '"true"' | false
        'Double'  | 'String'  | 1.0       | '&&' | '"true"' | false
        'boolean' | 'boolean' | true      | '||' | true     | true
        'boolean' | 'boolean' | true      | '||' | false    | true
        'boolean' | 'boolean' | false     | '||' | true     | true
        'boolean' | 'boolean' | false     | '||' | false    | false
        'String'  | 'String'  | '"false"' | '||' | '"true"' | true
    }

    def "Boolean operator precedence: #expression == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    final boolean a = true;
    final boolean b = false;
    
    @Expression.Value("$expression")
    abstract boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        expression | expectedResult
        'a && !b'  | true
        'b || !a'  | false
    }

    def "Boolean operator short circuit (#a, #b), call count (#aCount, #bCount): #expression"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import java.util.function.BooleanSupplier;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
    
    BooleanSupplier a;
    BooleanSupplier b;
    
    @Expression.Value("$expression")
    abstract boolean compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')
        bean.a = Mock(BooleanSupplier)
        bean.b = Mock(BooleanSupplier)

        when:
        bean.a.getAsBoolean() >> a
        bean.b.getAsBoolean() >> b

        bean.compute()

        then:
        aCount * bean.a.getAsBoolean()
        bCount * bean.b.getAsBoolean()

        where:
        op   | a     | b     | aCount | bCount
        '&&' | true  | true  | 1      | 0
        '&&' | false | true  | 1      | 0
        '&&' | false | false | 1      | 0
        '||' | true  | true  | 1      | 0
        '||' | false | true  | 1      | 1
        '||' | false | false | 1      | 1

        expression = "a.getAsBoolean() $op b.getAsBoolean()"
    }

    def "Simple boolean operation NC (literal, literal => boolean): #a #op #b fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$a $op $b")
    abstract boolean compute();
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        a           | op   | b        | expectedResult
        true        | '&&' | null     | BooleanContinuation.TYPE_ERROR
        false       | '||' | null     | BooleanContinuation.TYPE_ERROR
    }
}
