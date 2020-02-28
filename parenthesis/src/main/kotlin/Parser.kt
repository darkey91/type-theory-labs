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
                    throw ParseException("Illegal character $currentChar at $pointer in $str", pointer)
                }
            }
        }
        return currentToken
    }

}

class Parser {
    private var lexer: Lexer? = null

    private fun curChar(): Char = lexer!!.currentChar
    private fun curToken(): Token = lexer!!.currentToken
    private fun curVar(): String = lexer!!.getVariableName()
    private fun pointer(): Int = lexer!!.pointer

    private fun nextToken() = lexer!!.nextToken()

    private fun getErrorMessage(token: Token, pointer: Int, funcName: String): String =
        "Unexpected token $token at $pointer in $funcName"

    private fun expression(): Tree {
        val result: Tree
        when (curToken()) {
            Token.LAMBDA -> {
                assert(curChar() == '\\')
                nextToken()
                //result.addChild(Tree("\\"))
                result = Tree("Expr")
                //при визуализации не отображается '\\'
                result.addChild(Tree("\\${curVar()}."))
                nextToken()
                nextToken()

                result.addChildren(expression())
            }

            Token.VAR, Token.LPAREN -> {
                result = application()
            }
            else -> throw ParseException(getErrorMessage(curToken(), pointer(), "expression()"), lexer!!.pointer)
        }
        return result
    }

    private fun application(): Tree {
        val applicants = ArrayList<Tree>()

        loop@ while (true) {
            when (curToken()) {
                Token.LPAREN, Token.VAR -> applicants.add(atom())
                Token.LAMBDA -> {
                    applicants.add(expression())
                    break@loop
                }
                Token.END, Token.RPAREN -> {
                    break@loop
                }
                else -> throw ParseException(getErrorMessage(curToken(), pointer(), "application()"), lexer!!.pointer)

            }
        }
        applicants.reverse()


        while (applicants.size > 1) {
            val l = applicants.removeAt(applicants.size - 1)
            val r = applicants.removeAt(applicants.size - 1)
            applicants.add(Tree("appl", l, r))
        }
        return applicants.removeAt(applicants.size - 1)
    }

    private fun atom(): Tree {
        val result: Tree

        when (curToken()) {
            Token.LPAREN -> {
                assert(curChar() == '(')
                nextToken()
                result = expression()

                if (curChar() != ')') {
                    throw ParseException("No right parenthesis at ${pointer()}", lexer!!.pointer)
                }

                nextToken()
            }

            Token.VAR -> {
                result = Tree(curVar())
                nextToken()
            }
            else -> throw ParseException(getErrorMessage(curToken(), pointer(), "atom()"), lexer!!.pointer)
        }
        return result
    }

    fun parse(str: String): Tree {
        lexer = Lexer(str)
        nextToken()
        val root = expression()
        if (Token.END == curToken()) return root
        else throw ParseException(getErrorMessage(curToken(), pointer(), "parse()"), lexer!!.pointer)

    }
}

class Tree {
    var node: String
        private set

    private val children = ArrayList<Tree>()

    constructor(node: String) {
        this.node = node
    }

    constructor(node: String, vararg children: Tree) {
        this.node = node

        for (ch in children) {
            this.children.add(ch)
        }
    }

    fun addChild(ch: Tree) {
        children.add(ch)
    }

    fun addChildren(vararg children: Tree) {
        for (ch in children) {
            this.children.add(ch)
        }
    }

    fun printTree() {
        printTree(this)
    }

    private fun isLeaf(): Boolean = children.isEmpty()

    private fun printTree(tree: Tree) {
        if (tree.isLeaf()) {
            print(tree.node)
            return
        }

        print("(")

        for (i in tree.children.indices) {
            printTree(tree.children[i])
            if (tree.node == "appl" && i == 0) {
                print(" ")
            }
        }

        print(")")
    }
}

fun main() {
     val lambda = StringBuilder("")

     while (true) {
         val line = readLine() ?: break
         lambda.append(" ")
         lambda.append(line)
     }


    val parser = Parser()
    val tree = parser.parse(lambda.toString())
    tree.printTree()
}
