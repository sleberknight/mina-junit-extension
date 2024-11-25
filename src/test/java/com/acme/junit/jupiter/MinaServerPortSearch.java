package com.acme.junit.jupiter;

/**
 * How to find the port for the MINA server to bind to.
 * <p>
 * This can be important depending on the operating system and its handling of
 * the {@code SO_REUSEADDR} socket option. In Linux, you can only reuse a port
 * after the {@code TIME_WAIT} period. But macOS allows both reuse and simultaneous
 * bindings to the same port by multiple processes. This means that in macOS, if
 * you use either of the non-random port searches, there is a higher chance
 * of getting a "port already in use" error.
 */
public enum MinaServerPortSearch {

    /**
     * Find the first open port above the starting port (excludes the starting port).
     */
    ABOVE,

    /**
     * Find the first open port from the starting port (includes the starting port).
     */
    FROM,

    /**
     * Find a random port above the starting port (excludes the starting port).
     * <p>
     * This is the default in the MINA session extension.
     */
    RANDOM_ABOVE,

    /**
     * Find a random port from the starting port (includes the starting port).
     */
    RANDOM_FROM
}
