package com.acme.example.mina;

import java.io.IOException;

public class MinaTimeServerApp {

    private static final int PORT = 9123;

    public static void main(String[] args) throws IOException {
        new TimeServer().createAndStart(PORT);
    }
}
