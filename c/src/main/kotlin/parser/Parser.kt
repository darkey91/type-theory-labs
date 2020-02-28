package parser

import parser.Parser.Companion.APPLICATION_TAG
import parser.Parser.Companion.EXPRESSION_TAG
import java.lang.Exception
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

}

class Parser {

    companion object {
        const val EXPRESSION_TAG = "expr"
        const val APPLICATION_TAG = "appl"
    }

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
                result = Tree(EXPRESSION_TAG)
                result.left = Tree(curVar(), true)
                nextToken()
                nextToken()
                result.right = expression()
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
            applicants.add(Tree(APPLICATION_TAG, l, r))
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

class Equation(var left: String, var right: String, var used: Boolean = false, var deleted: Boolean = false) {

    fun isVarLeft(): Boolean = !this.left.contains("->")
    fun isVarRight(): Boolean = !this.right.contains("->")

    fun isEquationNotSolvable(): Boolean {
        return isVarLeft() && !isVarRight() && right.contains(left)
    }

    fun swapSides() {
        val tmp = left
        left = right
        right = tmp
    }

    fun areSidesEqual(): Boolean {
        return left == right
    }
}

class TypeWrapper {
    val system = ArrayList<Equation>()
    val context = HashMap<String, String>()
    var type: String

    constructor(t: String, variableName: String) {
        type = t
        context[variableName] = type
    }

    constructor(t: String, w1: TypeWrapper, w2: TypeWrapper) {
        type = t
        system.apply {
            w1.system.forEach {
                this.add(it)
            }
            w2.system.forEach {
                this.add(it)
            }
            this.add(Equation(w1.type, "(${w2.type}->$type)"))
        }

        context.putAll(w1.context)
        w2.context.forEach { (varName, type) ->
            val typeInContext = context[varName]
            if (typeInContext != null) {
                this.system.add(Equation(typeInContext, type))
            }
            context[varName] = type
        }
    }

    constructor(varName: String, expr: TypeWrapper, newLambdaVarType: String) {
        this.system.addAll(expr.system)
        this.context.putAll(expr.context)

        var typeOfVar = this.context.remove(varName)
        if (typeOfVar === null) {
            expr.context[varName] = newLambdaVarType
            typeOfVar = newLambdaVarType
        }

        this.type = "($typeOfVar->${expr.type})"
    }

    private fun isVarsUnique(eq: Equation, set: HashSet<String>): Boolean {
        var i = 0

        while (i < eq.right.length) {
            val right = eq.right
            if (!(right[i] == ')' || right[i] == '(' || right[i] == '-' || right[i] == '>')) {
                val varName = StringBuilder()
                while (right[i] != '$') {
                    varName.append(right[i++])
                }
                set.add(varName.toString())
            }
            ++i
        }

        val varName = eq.left.substring(0, eq.left.length - 1)

        if (varName.contains('$') || varName.contains('('))
            throw ParseException("fignya kakaya-to", 0)


        return set.add(varName)
    }

    //Equation has such type always "NOTVAR = NOTVAR"
    private fun findOperands(eq: String): Pair<String, String> {
        var minParen = Int.MAX_VALUE
        var curParen = 0
        var indexOfImpl = -1
        var i = 0
        while (i < eq.length) {
            if (eq[i] == '(') ++curParen
            if (eq[i] == ')') --curParen
            if (eq[i] == '-' && minParen > curParen) {
                indexOfImpl = i++
                minParen = curParen
            }
            ++i
        }
        val left = eq.substring(1, indexOfImpl)
        val right = eq.substring(indexOfImpl + 2, eq.length - 1)

        return left to right
    }

    fun unify(): Boolean {
        var solvable: Boolean

        outer@ do {
            loop@ for (i in this.system.indices) {
                if (this.system[i].deleted)
                    continue

                val curEquation = this.system[i]
                when {
                    !curEquation.isVarLeft() && curEquation.isVarRight() -> {
                        curEquation.swapSides()
                        break@loop
                    }

                    curEquation.areSidesEqual() -> {
                        curEquation.deleted = true
                    }

                    !curEquation.isVarLeft() && !curEquation.isVarRight() -> {
                        curEquation.deleted = true
                        val leftOp = findOperands(curEquation.left)
                        val rightOp = findOperands(curEquation.right)

                        this.system.apply {
                            add(Equation(leftOp.first, rightOp.first))
                            add(Equation(leftOp.second, rightOp.second))
                        }

                        break@loop
                    }

                    curEquation.isVarLeft() && !curEquation.used -> {
                        if (curEquation.isEquationNotSolvable()) throw TypeException("Type doesn't exist")

                        curEquation.used = true
                        var changed = false
                        for (j in this.system.indices) {
                            if (this.system[j].deleted || i == j)
                                continue

                            val eq = this.system[j]

                            if (eq.left.contains(curEquation.left)) {
                                eq.left = eq.left.replace(
                                    curEquation.left, curEquation.right
                                )
                                changed = true
                            }
                            if (eq.right.contains(curEquation.left)) {
                                eq.right = eq.right.replace(
                                    curEquation.left, curEquation.right
                                )
                                changed = true
                            }
                        }
                        if (changed) break@loop
                    }
                }
            }
            solvable = true
            val set = HashSet<String>()
            for (k in this.system.indices) {
                if (this.system[k].deleted) continue
                val it = this.system[k]
                if (it.isEquationNotSolvable()) throw TypeException("Type doesn't exist")
                if (!it.isVarLeft() || it.areSidesEqual()) {
                    solvable = false
                    break
                }
                if (!isVarsUnique(it, set)) {
                    solvable = false
                    break
                }
            }

        } while (!solvable)



        return true
    }
}

class Tree {
    val name: String
    var left: Tree? = null
    var right: Tree? = null
    var wrap: TypeWrapper? = null

    var isLambdaLeaf = false

    constructor(node: String, isLambda: Boolean = false) {
        this.name = node
        this.isLambdaLeaf = isLambda
    }

    constructor(node: String, left: Tree, right: Tree) {
        this.name = node

        this.left = left
        this.right = right
    }

    fun isApplication(): Boolean = name == APPLICATION_TAG
    fun isExpression(): Boolean = name == EXPRESSION_TAG

    fun printTree() {
        fillContext(this, null)
        printTree(this)
    }

    private fun fillContext(tree: Tree?, parent: Tree?) {
        if (tree == null || tree.isLambdaLeaf) return
        if (parent != null) {
            parent.wrap!!.context.forEach {
                tree.wrap!!.context.putIfAbsent(it.key, it.value)
            }
        }
        fillContext(tree.left, tree)
        fillContext(tree.right, tree)
    }

    fun isLeaf(): Boolean = left == null

    private fun printTree(tree: Tree?) {
        if (tree == null) return

        if (tree.isLeaf()) {
            if (tree.isLambdaLeaf) {
                print("\\${tree.name}.")

            } else print(tree.name)
            return
        }

        print("(")
        printTree(tree.left)
        if (tree.name == APPLICATION_TAG) {
            print(" ")
   /*         tree.wrap!!.context.forEach {
                tree.right!!.wrap!!.context.putIfAbsent(it.key, it.value)
                tree.left!!.wrap!!.context.putIfAbsent(it.key, it.value)
            }*/
        }
        printTree(tree.right)
        print(")")
    }
}

class TypeException(private val msg: String) : Exception(msg)

class TypeInferencer {
    companion object {
        val parser = Parser()
    }

    private var S = HashMap<String, String>()

    private var typeIndex = 1

    private fun getBasicTypeAndInc(): String = "t${typeIndex++}$"

    private fun getType(node: Tree): TypeWrapper {
        //if (node.isLambdaLeaf) throw  TypeException("getType in lambda leaf")
        when {
            //it'll never go to lambda leaf
            node.isLeaf() -> {
                node.wrap = TypeWrapper(getBasicTypeAndInc(), node.name)
                return node.wrap!!
            }
            node.isApplication() -> {
                val leftTypeWrapper = getType(node.left!!)
                val rightTypeWrapper = getType(node.right!!)

                node.wrap = TypeWrapper(getBasicTypeAndInc(), leftTypeWrapper, rightTypeWrapper)
            }
            node.isExpression() -> {
                val exprTypeWrapper = getType(node.right!!)
                val varName = node.left!!.name
                node.wrap = TypeWrapper(varName, exprTypeWrapper, getBasicTypeAndInc())
            }
            else -> throw  TypeException("Strange in getType")
        }
        return node.wrap!!
    }

    fun getType(lambda: String) {
        S.clear()
        typeIndex = 1
        val root = parser.parse(lambda)
        try {
            root.wrap = getType(root)
            root.wrap!!.unify()

            root.wrap!!.system.forEach {
                if (!it.deleted)
                    S[it.left] = it.right
            }
            printProof(root, 0)
        } catch (e: TypeException) {
            println("Expression has no type")
        }
    }

    private fun getChangedType(oldType: String): String {
        val newType = StringBuilder()
        var i = 0
        while (i < oldType.length) {
            if (oldType[i] == ')' || oldType[i] == '(' || oldType[i] == '-' || oldType[i] == '>') {
                newType.append(oldType[i])
            } else {
                val varName = StringBuilder()
                while (oldType[i] != '$') {
                    varName.append(oldType[i++])
                }

                val newVarName = S["${varName.toString()}$"]
                if (newVarName == null) {
                    newType.append(varName.toString())
                } else {
                    newType.append(newVarName.replace("$", ""))
                }
            }

            ++i
        }
        return newType.toString()
    }

    private fun getRule(n: Int): String = "[rule #$n]\n"

    private fun printProof(node: Tree?, depth: Int) {
        val out = StringBuilder()
        if (node == null || node.isLambdaLeaf) return
        for (i in 1..depth) {
            out.append("*   ")
        }
        node.wrap!!.context.forEach {
            out.append("${it.key} : ${getChangedType(it.value)}, ")
        }
        if (node.wrap!!.context.isNotEmpty()) {
            out.delete(out.length - 2, out.length)
            out.append(" ")
        }
        out.append("|- ")

        print(out.toString())
        when {
            node.isLeaf() -> {
                print("${node.name} : ${getChangedType(node.wrap!!.type)} ${getRule(1)}")
            }

            node.isApplication() -> {
                node.printTree()
                print(" : ${getChangedType(node.wrap!!.type)} ${getRule(2)}")
            }

            node.isExpression() -> {
                node.printTree()
                print(" : ${getChangedType(node.wrap!!.type)} ${getRule(3)}")
            }
        }

        printProof(node.left, 1 + depth)
        printProof(node.right, 1 + depth)
    }
}

fun main() {
    val typeInferencer = TypeInferencer()
    val lambda = StringBuilder("")
   /* while (true) {
        val line = readLine() ?: break
        lambda.append(" ")
        lambda.append(line)
    }
    typeInferencer.getType(lambda.toString())*/
    val le = arrayListOf(
        "x",
        "(\\x. x) (\\y. y)",
        "\\a. a' a z8'",
        "\\x.\\y.x",
        "\\x.\\y.\\z. x z (y z)",
        "\\f.\\x. f (f x)",
        "\\m.\\n. n m",
        "(\\m.\\n. n m) (\\f.\\x. f (f (f x))) (\\f.\\x. f (f x))",
        "(\\s.(\\m.\\n. n m) s s) (\\f.\\x. f (f (f x)))",
        "\\m.\\n.\\f.\\x. m f (n f x)",
        "(\\x. x x)(\\y. y y)",
        "\\x.\\f.\\g. f x",
        "\\x.\\f.\\g. g x",
        "\\a.\\f.\\g. a f g",
        "\\p.p(\\a.\\b.a)",
        "\\p.p(\\a.\\b.b)",
        "\\a.\\b.\\f. f a b",
        "\\n. n (\\x.\\a.\\b.b) (\\p.\\b.p)",
        "\\x. x x",
        "(\\x. x x)(\\x. x x)",
        "(\\x. x)(\\y. x)",
        "(\\y. x)(\\x. x)",
        "a x (f (\\g. g)f)",
        "a x (f f)",
        "(\\x.x) x x",
        "(\\x.x) y x",
        "\\x. y (y x) ",
        "\\f.(\\x.f(x x)(\\x.f(x x)))",
        "\\f.\\x. f (f (f (f x)))",
        "(\\x.x) x",
        "x (\\x.x)",
        "(\\x.\\y.x) (\\y.\\x.y)",
        "(\\x.x)(\\x.x)",
        "\\x.(\\y.y)\\z.z",
        "x y"

    )

    typeInferencer.getType(le.last())

    le.forEach {
        println("---------------------------------------------------------------------------------------------")
        println(it)
        typeInferencer.getType(it)
    }
}
