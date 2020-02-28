/*
package parser

import guru.nidi.graphviz.attribute.Color
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.model.Factory.mutGraph
import guru.nidi.graphviz.model.Factory.mutNode
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.model.MutableNode
import parser.Parser.Companion.APPLICATION_TAG
import parser.Parser.Companion.EXPRESSION_TAG
import java.io.File
import java.io.InputStream
import java.lang.Exception
import java.lang.StringBuilder
import java.lang.reflect.Type
import java.text.ParseException
import java.time.LocalDateTime


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

class Wrap {
    //system.second = this rule was deleted
    val system = ArrayList<Pair<Pair<String, String>, Boolean>>()
    val context = HashMap<String, String>()
    var type: String

    constructor(t: String, variableName: String) {
        type = t
        context[variableName] = type
    }

    constructor(t: String, w1: Wrap, w2: Wrap) {
        type = t
        system.apply {
            w1.system.forEach {
                this.add(it)
            }
            w2.system.forEach {
                this.add(it)
            }
            this.add((w1.type to "(${w2.type}->$type)") to false)
        }

        context.putAll(w1.context)
        w2.context.forEach { (varName, type) ->
            val typeInContext = context[varName]
            if (typeInContext != null) {
                this.system.add((typeInContext to type) to false)
            }

            context[varName] = type
        }
    }

    constructor(varName: String, expr: Wrap, newLambdaVarType: String) {
        this.system.addAll(expr.system)
        this.context.putAll(expr.context)

        var typeOfVar = this.context.remove(varName)
        if (typeOfVar === null) {
            expr.context[varName] = newLambdaVarType
            typeOfVar = newLambdaVarType
        }

        this.type = "($typeOfVar->${expr.type})"
    }

    private fun isVar(expr: String) = !expr.contains("->")

    fun isEquationNotSolvable(eq: Pair<String, String>): Boolean {
        return isVar(eq.first) && eq.second.contains(eq.first)
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
        var changed: Boolean

        outer@ do {
            changed = false
            loop@ for (i in this.system.indices) {
                if (this.system[i].second)
                    continue

                val curRule = this.system[i].first

                if (isEquationNotSolvable(curRule))
                    throw TypeException("Type doesn't exist")

                when {
                    !isVar(curRule.first) && isVar(curRule.second) -> {
                        changed = true
                        this.system[i] = (curRule.second to curRule.first) to false
                        break@loop
                    }

                    isVar(curRule.first) && curRule.first == curRule.second -> {
                        this.system[i] = Pair(curRule, true)
                    }
                    isVar(curRule.first) -> {

                        for (j in this.system.indices) {
                            if (this.system[j].second || i == j)
                                continue
                            val leftPartOfRule = this.system[j].first.first
                            val rightPartOfRule = this.system[j].first.second

                            if (leftPartOfRule.contains(curRule.first)) {
                                changed = true
                                this.system[j] = (leftPartOfRule.replace(
                                    curRule.first,
                                    curRule.second
                                ) to this.system[j].first.second) to false
                            }
                            if (rightPartOfRule.contains(curRule.first)) {
                                changed = true
                                this.system[j] = (this.system[j].first.first to rightPartOfRule.replace(
                                    curRule.first,
                                    curRule.second
                                )) to false
                            }

                        }
                        continue@loop
                    }
                    !isVar(curRule.first) && !isVar(curRule.second) -> {
                        changed = true
                        //TODO think..
                        this.system[i] = Pair(curRule, true)
                        val leftOp = findOperands(curRule.first)
                        val rightOp = findOperands(curRule.second)

                        this.system.apply {
                            add((leftOp.first to rightOp.first) to false)
                            add((leftOp.second to rightOp.second) to false)
                        }
                        break@loop
                    }
                }
            }

        } while (changed)

        this.system.forEach {
            if (!it.second && isEquationNotSolvable(it.first)) throw TypeException("Type doesn't exist")
        }

        return true
    }
}

class Tree {
    val name: String
    var left: Tree? = null
    var right: Tree? = null
    var wrap: Wrap? = null

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
        printTree(this)
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
        if (tree.name == APPLICATION_TAG) print(" ")
        printTree(tree.right)
        print(")")
    }
}

class TreeVisualizer(private val name: String = "G") {
    private var g: MutableGraph = mutGraph(this.name)

    init {
        g.isDirected = true
    }

    private fun getNodeId(node: Tree): String = System.identityHashCode(node).toString()

    private fun addNode(parent: MutableNode, ch: Tree) {
        val curNode = mutNode(getNodeId(ch), true).add("label", ch.name)
        g.add(curNode)
        parent.addLink(curNode)
        buildGraph(ch, curNode)
    }

    private fun buildGraph(nodeTree: Tree, node: MutableNode) {
        if (nodeTree.isLeaf()) {
            node.add(Color.GREEN1)
            return
        }

//        if (nodeTree == null) throw TypeException("tree == null in TreeVisualizer.buildGraph()")
        addNode(node, nodeTree.left!!)
        addNode(node, nodeTree.right!!)

    }

    fun visualize(root: Tree) {
        val rootLocal = mutNode(getNodeId(root), true).add("label", root.name)
        g.add(rootLocal)
        buildGraph(root, rootLocal)
        Graphviz.fromGraph(g).render(Format.PNG).toFile(File("src/img/${LocalDateTime.now()}.png"))
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

    private fun getType(node: Tree): Wrap {
        //if (node.isLambdaLeaf) throw  TypeException("getType in lambda leaf")
        when {
            //it'll never go to lambda leaf
            node.isLeaf() -> {
                node.wrap = Wrap(getBasicTypeAndInc(), node.name)
                return node.wrap!!
            }
            node.isApplication() -> {
                val leftWrap = getType(node.left!!)
                val rightWrap = getType(node.right!!)

                node.wrap = Wrap(getBasicTypeAndInc(), leftWrap, rightWrap)
            }
            node.isExpression() -> {
                val exprWrap = getType(node.right!!)
                val varName = node.left!!.name
                node.wrap = Wrap(varName, exprWrap, getBasicTypeAndInc())
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
                S[it.first.first] = it.first.second
            }
            printProof(root, 0)
        } catch (e: TypeException) {
            println(e.message)
            println("Expression has no type")
        }
    }

    private fun getChangedType(oldType: String): String {
        val newType = S[oldType]
        return newType?.replace("$", "") ?: oldType.replace("$", "")
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
        out.append("-| ")

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

    while (true) {
        val line = readLine() ?: break
        lambda.append(" ")
        lambda.append(line)
    }

    typeInferencer.getType(lambda.toString())
}
*/
