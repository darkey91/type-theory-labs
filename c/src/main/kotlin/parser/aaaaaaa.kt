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
import java.lang.Exception
import java.lang.StringBuilder
import java.lang.reflect.Type
import java.text.ParseException
import java.time.LocalDateTime

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

class BinaryTree {
    var name: String
    var left: BinaryTree? = null
    var right: BinaryTree? = null

    constructor(name: String) {
        this.name = name
    }

    constructor(name: String, l: BinaryTree, r: BinaryTree) {
        this.name = name
        left = l
        right = r
    }

    fun contains(e: BinaryTree) {

    }
}

class Wrap {
    //system.second = this rule was deleted
    val system = ArrayList<Pair<Pair<BinaryTree, BinaryTree>, Boolean>>()
    val context = HashMap<String, BinaryTree>()
    var type: BinaryTree

    constructor(t: String, variableName: String) {
        type = BinaryTree(t)
        context[variableName] = type
    }

    constructor(t: String, w1: Wrap, w2: Wrap) {
        type = BinaryTree(t)
        system.apply {
            w1.system.forEach {
                this.add(it)
            }
            w2.system.forEach {
                this.add(it)
            }
            this.add((w1.type to BinaryTree("->", w2.type, type)) to false)
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
            expr.context[varName] = BinaryTree(newLambdaVarType)
            typeOfVar = expr.context[varName]
        }
        if (typeOfVar == null) throw TypeException("TypeOfVar is null in constructor")

        this.type = BinaryTree("->", typeOfVar, expr.type)
    }

    private fun isVar(expr: BinaryTree) = expr.left == null

    private fun isEquationSolvable(eq: Pair<BinaryTree, BinaryTree>): Boolean {
        return !isVar(eq.first) || !eq.second.contains(eq.first)
    }

    fun unify(): Boolean {
        var changed = false

        outer@ do {
            for (i in this.system.indices) {
                if (this.system[i].second) continue

                var curRule = this.system[i].first

                if (!isEquationSolvable(curRule)) return false
                when {
                    isVar(curRule.second) && !isVar(curRule.first) -> {
                        changed = true
                        this.system[i] = Pair(Pair(curRule.second, curRule.first), false)
                    }
                    isVar(curRule.first) && curRule.second == curRule.first -> {
                        this.system[i] = Pair(Pair(curRule.first, curRule.second), true)
                    }


                }

            }

        } while (changed)
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
            print(tree.name)
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

    private var typeIndex = 1

    private fun getBasicTypeAndInc(): String = "t${typeIndex++}"

    private fun getType(node: Tree): Wrap {
        if (node.isLambdaLeaf) throw  TypeException("getType in lambda leaf")
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
        val root = parser.parse(lambda)
        root.wrap = getType(root)
        printProof(root)
    }

    fun printProof(node: Tree?) {
        if (node == null || node.isLambdaLeaf) return

        println("\n${node.name} HAS TYPE: ${node.wrap!!.type}")
        print("Context: ")
        node.wrap!!.context.forEach {
            print("${it.key} : ${it.value} ")
        }
        println("\nSystem: ")
        node.wrap!!.system.forEach {
            if (!it.second)
                println("\t${it.first.first} = ${it.first.second}")
        }
        printProof(node.left)
        printProof(node.right)
    }
}

fun main() {

    val lambdaExpressions = arrayListOf(
        "\\a.\\b.a",
        "(\\a.\\b.a)",
        "((c(\\a.\\b.a)))",
        "(\\x. x) (\\y. y)",
        "((\\x. x) (\\y. y))",
        "\\a.\\b.a b c (\\d.e \\f.g) h",
        "((a\\bbb.c)d)e\n" +
                "f g",
        "x y",
        "(\\x.x)(\\x.x)",
        "(\\y.x)(\\x.x)",
        "(\\x.x)(\\y.x)",
        "(\\f.\\x.f(f x))"
    )

    val parser = Parser()
    lambdaExpressions.forEach {
        val tree = parser.parse(it)
        TreeVisualizer().visualize(tree)

    }

    val typeInferencer = TypeInferencer()
    typeInferencer.getType(lambdaExpressions.last())
}
*/
