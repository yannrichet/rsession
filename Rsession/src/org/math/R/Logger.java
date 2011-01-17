package org.math.R;

public interface Logger {

    public enum Level {

        INFO,
        WARNING,
        ERROR;
    }

    /**Support R messages printing*/
    public void println(String message, Level l);
}
