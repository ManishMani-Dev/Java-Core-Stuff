package jvmInternalStuff;

public class InterpretExample {
    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            printSquare(i);
        }
    }

    public static void printSquare(int x) {
        int square = x * x;
        System.out.println("Square of " + x + " is " + square);
    }
}

