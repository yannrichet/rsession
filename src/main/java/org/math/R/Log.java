package org.math.R;

/**
 *
 * @author richet
 */
public abstract class Log {

    static { // should be changed to get logging from apache here...
        System.setProperty("org.apache.commons.logging.Log",
                "org.apache.commons.logging.impl.NoOpLog");
    }

    public static Log Out = new Log() {

        @Override
        public void print(String s) {
            System.out.print(s);
        }

        @Override
        public void println(String s) {
            System.out.println(s);
        }

    };

   public static Log Err = new Log() {

        @Override
        public void print(String s) {
            System.err.print(s);
        }

        @Override
        public void println(String s) {
            System.err.println(s);
        }

    };

    public abstract void println(String s);

    public abstract void print(String s);
}
