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


import io.github.paullo612.mlfx.expression.test.OverloadType
import io.github.paullo612.mlfx.expression.test.Overloaded

class MethodCallExpressionSpec extends ExpressionSpec {

    def "Method call (literal#methodRef => #ret): #methodCall == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$methodCall")
    abstract $ret compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        ret       | a           | method      | args       | expectedResult
        'int'     | "'foo'"     | 'length'    | []         | 3
        'int'     | "'foo bar'" | 'length'    | []         | 7
        'boolean' | "'foo'"     | 'length'    | []         | false
        'String'  | "'bar'"     | 'substring' | [1]        | 'ar'
        'String'  | "'bar'"     | 'substring' | [1.75]     | 'ar'
        'String'  | "'bar'"     | 'substring' | [0, "'2'"] | 'ba'
        'boolean' | "'bar'"     | 'contains'  | ["'ar'"]   | true
        'boolean' | "'true'"    | 'contains'  | [true]     | true
        'boolean' | "'foo'"     | 'equals'    | [null]     | false

        methodRef = "#$method(${(['literal'] * args.size()).join(', ')})"
        methodCall = "$a.$method(${args.join(', ')})"
    }

    def "Method call (#aType#methodRef => #ret): #methodCall == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    final $aType a = $a;
        
    @Expression.Value("$methodCall")
    abstract $ret compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType        | ret            | a                  | method      | args           | expectedResult
        'String'     | 'int'          | '"foo"'            | 'length'    | []             | 3
        'String'     | 'int'          | '"foo bar"'        | 'length'    | []             | 7
        'String'     | 'boolean'      | '"foo"'            | 'length'    | []             | false
        'String'     | 'String'       | '"bar"'            | 'substring' | [1]            | 'ar'
        'String'     | 'String'       | '"bar"'            | 'substring' | [1.75]         | 'ar'
        'String'     | 'String'       | '"bar"'            | 'substring' | [0, "'2'"]     | 'ba'
        'String'     | 'boolean'      | '"bar"'            | 'contains'  | ["'ar'"]       | true
        'String'     | 'boolean'      | '"true"'           | 'contains'  | [true]         | true
        'String'     | 'boolean'      | '"foo"'            | 'equals'    | [null]         | false
        'Overloaded' | 'OverloadType' | 'new Overloaded()' | 'overload'  | [1]            | OverloadType.LONG
        'Overloaded' | 'OverloadType' | 'new Overloaded()' | 'overload'  | [1, 1]         | OverloadType.INT_AND_LONG
        'Overloaded' | 'OverloadType' | 'new Overloaded()' | 'overload'  | ["'1'", "'1'"] | OverloadType.INT_AND_STRING
        'Overloaded' | 'OverloadType' | 'new Overloaded()' | 'overload'  | [null, 1.0]    | OverloadType.DOUBLE_OBJECT_ARRAY_AND_DOUBLE
        'Overloaded' | 'OverloadType' | 'new Overloaded()' | 'overload'  | [null, false]  | OverloadType.BOOLEAN_ARRAY_AND_BOOLEAN

        methodRef = "#$method(${(['literal'] * args.size()).join(', ')})"
        methodCall = "a.$method(${args.join(', ')})"
    }

    def "Method call (literal#substring(int) => String): 'foo'.substring(a) == oo"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    final String a = "1";
        
    @Expression.Value("'foo'.substring(a)")
    abstract String compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == 'oo'
    }

    def "Method call (literal#methodRef => #ret) NC: #methodCall fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$methodCall")
    abstract $ret compute();
}
""")
        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        ret   | a       | method      | args | expectedResult
        'int' | "'foo'" | 'blah'      | []   | String.format(MethodCallContinuation.NO_SUCH_METHOD_ERROR_FORMAT, method, String.name)
        'int' | "'foo'" | 'length'    | [1]  | String.format(MethodCallContinuation.NO_SUCH_METHOD_ERROR_FORMAT, method, String.name)

        methodRef = "#$method(${(['literal'] * args.size()).join(', ')})"
        methodCall = "$a.$method(${args.join(', ')})"
    }

    def "Method call (#aType#methodRef => #ret) NC: #methodCall fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    final $aType a = $a;
        
    @Expression.Value("$methodCall")
    abstract $ret compute();
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        aType        | ret            | a                  | method      | args
        'Overloaded' | 'OverloadType' | 'new Overloaded()' | 'overload'  | [1, 1.0]
        'Overloaded' | 'OverloadType' | 'new Overloaded()' | 'overload'  | [null, null]
        _________________________________________________________________________________________________
        expectedResult                                                                                | _
        String.format(MethodCallContinuation.AMBIGUOUS_METHOD_CALL_ERROR_FORMAT, 'a.overload(1,1.0)') | _
        String.format(MethodCallContinuation.NO_SUCH_METHOD_ERROR_FORMAT, method, Overloaded.name) | _

        methodRef = "#$method(${(['literal'] * args.size()).join(', ')})"
        methodCall = "a.$method(${args.join(', ')})"
    }
}
