package npe.sheo.typescript2haxe

import java.io.File
import java.util.*
import java.util.regex.Pattern

/**
 * Created by I on 15.09.2017.
 */

val p_funcDecl = Pattern.compile("""declare function (\w+)\((.*)\): (.+);""")
val p_argDecl = Pattern.compile("""(.+): (.+)""")

fun main(args: Array<String>) {
    File("Natives.hx").writeText(Ts2Hx("D:\\Games\\FiveM\\FiveM.app\\citizen\\scripting\\v8\\natives_universal.d.ts").process("Natives"))
}

class Ts2Hx(fileName: String) {
    
    val inputContent = File(fileName).readLines()
    
    val sb = StringBuilder()
    var indentNum: Int = 0
    var declCount: Int = 0
    
    var allowSameNames = false
    
    var declaredNames = ArrayList<String>()
    
    fun process(className: String): String {
        line("// Generated ${Date()}")
        line("import haxe.extern.EitherType;")
        line()
        line("extern class $className {")
    
        indent {
            inputContent.forEach {
                val line = it.trim()
        
                when {
                    line.startsWith("/") -> {
                        line()
                        line(line)
                    }
                    line.startsWith("*") -> line(line)
                    isFuncDecl(line) -> {
                        val decl = FunctionDecl(line)
                        if(decl.name !in declaredNames) {
                            line(decl.toString())
                            declCount++
                            declaredNames.add(decl.name)
                        }
                        else {
                            println("Redefinition of: $line")
                        }
                        
                    }
                    else -> {}
                }
                
            }
        }
        
        line("}")
        
        println("Transpiled $declCount functions")
        return sb.toString()
    }
    
    fun indent(fn: ()->Unit) {
        indentNum++
        fn()
        indentNum--
    }
    
    fun line(str: String = "") {
        (1..indentNum).forEach { sb.append("    ") }
        sb.append(str).append('\n')
    }
    
    fun isFuncDecl(line: String) = p_funcDecl.matcher(line).matches()
}

class FunctionDecl(private val line: String) {
    
    val target = "lua"
    
    val name: String
    val args = ArrayList<ArgDecl>()
    val returnType: String
    
    init {
        val matcher = p_funcDecl.matcher(line)
        matcher.find()
        name = matcher.group(1)
        matcher.group(2).split(", ").filter { it.isNotEmpty() }.forEach {
            args += ArgDecl(it)
        }
        returnType = translateType(matcher.group(3))
    }
    
    fun getStrArgs() = listToCSString(List(args.size) { args[it] })
    
    fun getStrArgNames() = listToCSString(List(args.size) { args[it].name })
    
    
    override fun toString(): String {
        return "@:pure static inline function $name(${getStrArgs()}): $returnType { return untyped __${target}__('$name')(${getStrArgNames()}); }"
    }
}

class ArgDecl(private val line: String) {
    
    val name: String
    val type: String
    
    init {
        val matcher = p_argDecl.matcher(line)
        matcher.find()
        name = translateName(matcher.group(1))
        type = translateType(matcher.group(2))
    }
    
    override fun toString(): String {
        return "$name: $type"
    }
}

fun translateType(s: String): String {
    return when {
        s == "void" -> "Void"
        s == "number" -> "Int"
        s == "string" -> "String"
        s == "boolean" -> "Bool"
        s == "any" -> "Any"
        s.contains('|') -> {
            val s1 = s.split('|')
            return "EitherType<${translateType(s1[0].trim())}, ${translateType(s1[1].trim())}>"
        }
        s.startsWith('[') && s.endsWith(']') -> {
            val sb = StringBuilder()
            val types = s.substring(1, s.length -1).split(", ").filter { it.isNotEmpty() }
            sb.append("{ ")
            sb.append(listToCSString(List(types.size) { "a$it: ${translateType(types[it])}" }))
            sb.append(" }")
            return sb.toString()
        }
        s.endsWith("[]") -> "Array<${translateType(s.substring(0, s.length-2))}>"
        else -> "Dynamic"
    }
}

fun translateName(s: String): String {
    return when(s) {
        "dynamic" -> "dynamic_"
        else -> s
    }
}

fun <T : Any> listToCSString(list: List<T>): String {
    val sb = StringBuffer()
    list.forEachIndexed { index, element ->
        sb.append(element.toString())
        if(index != list.size - 1)
            sb.append(", ")
    }
    return sb.toString()
}