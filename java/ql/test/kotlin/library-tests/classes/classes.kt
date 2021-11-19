
class ClassOne { }

class ClassTwo (val arg: Int) {
    val x: Int = 3
}

abstract class ClassThree {
    abstract fun foo(arg: Int)
}

open class ClassFour: ClassThree() {
    override fun foo(arg: Int) {
    }
}

class ClassFive: ClassFour() {
}

interface IF1 {
    fun funIF1() {}
}

interface IF2 {
    fun funIF2() {}
}

class ClassSix(): ClassFour(), IF1, IF2 {
    constructor(i: Int): this(){ }
}

fun f(s: String) {}

class ClassSeven {
    constructor(i: String) {
        f(i)
    }
    init {
        f("init1")
    }

    val x: Int = 3

    init {
        f("init2")
    }
}

enum class Direction {
    NORTH, SOUTH, WEST, EAST
}

enum class Color(val rgb: Int) {
    RED(0xFF0000),
    GREEN(0x00FF00),
    BLUE(0x0000FF)
}

interface Interface1 {}
interface Interface2 {}
interface Interface3<T> {}

class Class1 {
    private fun getObject1(b: Boolean) : Any {
        if (b)
            return object : Interface1, Interface2 { }
        else
            return object : Interface1, Interface2, Interface3<String> { }
    }

    private fun getObject2() : Interface1 {
        return object : Interface1, Interface2 {
            val x = 1
            fun foo(): Any {
                return object { }
            }
         }
    }

    private fun getObject3() : Any {
        return object : Interface1 { }
    }

    private fun getObject4() : Any {
        return object { }
    }

    private fun getObject5() : Any {
        return object : Interface3<Int?> { }
    }
}