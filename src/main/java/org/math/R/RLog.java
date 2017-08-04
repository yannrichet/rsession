package org.math.R;

public interface RLog {

    public enum Level {
        OUTPUT,
        INFO,
        WARNING,
        ERROR;
    }

    /**Support R messages printing*/
    public void log(String message, Level l);

    public void close();
}
