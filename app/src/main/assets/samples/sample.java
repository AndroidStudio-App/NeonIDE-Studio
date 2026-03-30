// Sample Java file opened on app start to demonstrate Tree-sitter highlighting
package com.example;

import java.util.List;

public class HelloTreeSitter {

    private static final String GREETING = "Hello from Tree-sitter";

    @Deprecated
    public static void main(String[] args) {
        var list = List.of(1, 2, 3);
        for (int i : list) {
            System.out.println(GREETING + " #" + i);
        }

        if (args != null && args.length > 0) {
            System.out.println("Args: " + String.join(", ", args));
        }
    }
}
