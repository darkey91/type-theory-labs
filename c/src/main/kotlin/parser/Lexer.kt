/*
package parser

import java.lang.StringBuilder
import java.text.ParseException

enum class Token {
    START, VAR, LAMBDA, DOT, LPAREN, RPAREN, END
}

class Lexer(private val str: String) {
    var variableName: StringBuilder = StringBuilder()
        private set
    var pointer = 0
        private set
    var currentToken = Token.START
        private set
    var currentChar: Char = '$'
        private set

    fun getVariableName(): String = variableName.toString()

    private fun nextChar() {

        currentChar = if (pointer < str.length) str[pointer++]
        else {
            pointer++
            '$'
        }
    }

    fun nextToken(): Token {
        nextChar()
        while (currentChar.isWhitespace()) nextChar()

        currentToken = when (currentChar) {
            '(' -> Token.LPAREN
            ')' -> Token.RPAREN
            '\\' -> Token.LAMBDA
            '.' -> Token.DOT
            '$' -> Token.END

            else -> {
                if (currentChar in 'a'..'z') {
                    variableName.clear().append(currentChar)
                    nextChar()
                    while (currentChar in 'a'..'z' || currentChar in '0'..'9' || currentChar == '\'') {
                        variableName.append(currentChar)
                        nextChar()
                    }
                    --pointer
                    Token.VAR
                } else {
                    throw ParseException("Illegal character at $currentChar $pointer", pointer)
                }
            }
        }
        return currentToken
    }

}*/
