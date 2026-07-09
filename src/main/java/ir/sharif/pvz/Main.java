package ir.sharif.pvz;

import ir.sharif.pvz.controller.GameApp;

/**
 * Entry point of the CLI application.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        new GameApp().run();
    }
}
