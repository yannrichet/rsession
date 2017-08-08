package org.math.R;

public interface RLog {

    public enum Level {
        OUTPUT,
        INFO,
        WARNING,
        ERROR;
    }

    /**Support R messages printin
     * @param message to log
     * @param l Level*/
    public void log(String message, Level l);

    public void close();
}
