package com.acme.example.mina;

public class MinaTimeServerApp {

    private static final int PORT = 9123;

    public static void main(String[] args) {
        new TimeServer().createAndStart(PORT);
    }
}
