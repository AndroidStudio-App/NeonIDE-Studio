// Sample Kotlin file opened manually to demonstrate Tree-sitter highlighting
package com.example

class HelloKotlin(private val name: String) {

    fun greet(times: Int = 3) {
        repeat(times) {
            println("Hello, $name #$it")
        }
    }
}

fun main() {
    HelloKotlin("Tree-sitter").greet()
}
