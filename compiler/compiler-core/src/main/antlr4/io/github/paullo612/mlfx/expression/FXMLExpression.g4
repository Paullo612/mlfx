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
grammar FXMLExpression;

root[io.github.paullo612.mlfx.expression.ExpressionContext context]
    : expression EOF
    ;

expression
    :    e=expression CallSeparator Identifier                                   # propertyReadExpression
    |    l=expression CallSeparator Identifier
             OpenBrace
                 (args+=expression (MethodArgumentSeparator args+=expression)*)?
             CloseBrace                                                          # methodCallExpression
    |    l=expression OpenSquareBrace r=expression CloseSquareBrace              # collectionExpression
    |    t=(Subtract | Not) e=expression                                         # substractOrNegateExpression
    |    l=expression t=(Multiply | Divide | Remainder) r=expression             # multiplyOrDivideOrRemainderExpression
    |    l=expression t=(Add | Subtract) r=expression                            # addOrSubtractExpression
    |    l=expression t=(GT | GE | LT | LE) r=expression                         # comparisonExpression
    |    l=expression t=(EqualTo | NotEqualTo) r=expression                      # equalityExpression
    |    l=expression And r=expression                                           # andExpression
    |    l=expression Or r=expression                                            # orExpression
    |    OpenBrace e=expression CloseBrace                                       # parenthesisedExpression
    |    Identifier                                                              # propertyReadExpression
    |    StringLiteral                                                           # stringLiteral
    |    DecimalLiteral                                                          # decimalLiteral
    |    FloatingPointLiteral                                                    # floatingPointLiteral
    |    NullKeyword                                                             # nullLiteral
    |    (TrueKeyword | FalseKeyword)                                            # booleanLiteral
    ;

StringLiteral
    :	 (DoubleQuote StringCharacters? DoubleQuote)
    |    (Quote StringCharacters? Quote)
    ;

fragment
StringCharacters
    :   ~["'\\]+
    ;


NullKeyword
    :    'null'
    ;

TrueKeyword
    :    'true'
    ;

FalseKeyword
    :    'false'
    ;

CallSeparator
    :    '.'
    ;

Not
    :    '!'
    ;

Multiply
    :    '*'
    ;

Divide
    :    '/'
    ;

Remainder
    :    '%'
    ;

Add
    :    '+'
    ;

Subtract
    :    '-'
    ;

GT
    :    '>'
    ;

GE
    :    '>='
    ;

LT
    :    '<'
    ;

LE
    :    '<='
    ;

EqualTo
    :    '=='
    ;

NotEqualTo
    :    '!='
    ;

And
    :    '&&'
    ;

Or
    :    '||'
    ;

OpenSquareBrace
    :   '['
    ;

CloseSquareBrace
    :   ']'
    ;

OpenBrace
    :   '('
    ;

CloseBrace
    :   ')'
    ;

DoubleQuote
    :   '"'
    ;

Quote
    :   '\''
    ;

Identifier
    :	[a-zA-Z] [a-zA-Z0-9]*
    ;

MethodArgumentSeparator
    :   ','
    ;

DecimalLiteral
	:	'0'
	|	NonZeroDigit (Digits?)
    ;


fragment
Digits
    :	Digit+
    ;

fragment
Digit
	:	'0'
	|	NonZeroDigit
	;

fragment
NonZeroDigit
	:	[1-9]
	;

FloatingPointLiteral
	:	Digits '.' Digits? ExponentPart?
	|	'.' Digits ExponentPart?
	|	Digits ExponentPart
	|	Digits
	;

fragment
ExponentPart
	:	ExponentIndicator SignedInteger
	;

fragment
ExponentIndicator
	:	[eE]
	;


fragment
SignedInteger
	:	Sign? Digits
	;


fragment
Sign
	:	[+-]
	;

Whitespace: [ \n\t\r]+ -> skip;

Any
    : .
    ;