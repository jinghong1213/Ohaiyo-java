package com.dailyresume;

/**
 * Single entry point for the fat jar.
 *
 * <p>We bundle everything into one runnable jar via the maven-shade-plugin,
 * so we need <em>one</em> {@code main} method. This class dispatches
 * to the real entry points based on the first command-line argument:
 *
 * <pre>
 *   java -jar target/ohayo.jar capture     ← run the daemon once
 *   java -jar target/ohayo.jar launcher    ← open the morning GUI
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase() : "";
        switch (mode) {
            case "capture":
                Capture.main(stripFirst(args));
                break;
            case "launcher":
                Launcher.main(stripFirst(args));
                break;
            default:
                System.err.println("Usage: java -jar ohayo.jar <capture|launcher>");
                System.exit(1);
        }
    }

    /** Drop args[0] so the sub-command sees only its own arguments. */
    private static String[] stripFirst(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }
}
