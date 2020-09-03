package org.math.R;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

public class RserverConf {

    public static String DEFAULT_RSERVE_HOST = "localhost";
    public static Object lockPort = new Object();
    RConnection connection;
    public String host;
    public int port;
    public String login;
    public String password;
    //public String RLibPath;
    public Properties properties;
    //public String http_proxy;

    public RserverConf(String RserverHostName, int RserverPort, String login, String password, Properties props) {
        this.host = RserverHostName;
        this.port = RserverPort;
        this.login = login;
        this.password = password;
        properties = props;
    }
    public static long CONNECT_TIMEOUT = 2000;

    public abstract class TimeOut {

        /**
         * @return the result
         */
        public Object getResult() {
            return result;
        }

        public class TimeOutException extends Exception {

            public TimeOutException(String why) {
                super(why);
            }
        }

        private class TimeoutThread implements Runnable {

            public void run() {
                Object res = command();
                synchronized (TimeOut.this) {
                    if (timedOut && res != null) {
                    } else {
                        result = res;
                        TimeOut.this.notify();
                    }
                }
            }
        }
        private boolean timedOut = false;
        private Object result = null;

        protected TimeOut() {
        }

        public synchronized void execute(long timeout) throws TimeOutException {
            new Thread(new TimeoutThread()).start();

            try {
                this.wait(timeout);
            } catch (InterruptedException e) {
                if (getResult() == null) {
                    timedOut = true;
                    result = defaultResult();
                    throw new TimeOutException("timed out");
                } else {
                    return;
                }
            }

            if (getResult() != null) {
                return;
            } else {
                timedOut = true;
                result = defaultResult();
                throw new TimeOutException("timed out");
            }
        }

        protected abstract Object defaultResult();

        protected abstract Object command();
    }

    /*private class ConnectionThread implements Runnable {
    
    public void run() {
    try {
    if (host == null) {
    if (port > 0) {
    connection = new RConnection(DEFAULT_RSERVE_HOST, port);
    } else {
    connection = new RConnection();
    }
    if (connection.needLogin()) {
    connection.login(login, password);
    }
    } else {
    if (port > 0) {
    connection = new RConnection(host, port);
    } else {
    connection = new RConnection(host);
    }
    if (connection.needLogin()) {
    connection.login(login, password);
    }
    }
    } catch (RserveException ex) {
    //ex.printStackTrace();
    //return null;
    }
    
    synchronized (this) {
    this.notify();
    }
    }
    }*/
    public synchronized RConnection connect() {
        //Logger.err.print("Connecting " + toString()+" ... ");

        TimeOut t = new TimeOut() {

            protected Object defaultResult() {
                return -2;
            }

            protected Object command() {
                try {
                    if (host == null) {
                        if (port > 0) {
                            connection = new RConnection(DEFAULT_RSERVE_HOST, port);
                        } else {
                            connection = new RConnection();
                        }
                        if (connection.needLogin()) {
                            connection.login(login, password);
                        }
                    } else {
                        if (port > 0) {
                            connection = new RConnection(host, port);
                        } else {
                            connection = new RConnection(host);
                        }
                        if (connection.needLogin()) {
                            connection.login(login, password);
                        }
                    }
                    return 0;
                } catch (RserveException ex) {
                    //Log.Err.println("Failed to connect: " + ex.getMessage());
                    return -1;
                }
            }
        };

        try {
            t.execute(CONNECT_TIMEOUT);
        } catch (Exception e) {
            Log.Err.println("  failed: " + e.getMessage());
        }

        if (((Integer) t.getResult()) == 0 && connection != null && connection.isConnected()) {
            if (properties != null) {
                for (String p : properties.stringPropertyNames()) {
                    try {
                        connection.eval("Sys.setenv(" + p + "='" + properties.getProperty(p) + "')");
                    } catch (RserveException ex) {
                        ex.printStackTrace();
                    }
                }
            }

            return connection;
        } else {
            Log.Err.println("Connection " + toString() + " failed.");
            return null;
        }

    }
    public final static int RserverDefaultPort = 6311;
    private static int RserverPort = RserverDefaultPort; //used for windows multi-session emulation. Incremented at each new Rscript instance.

    public static boolean isPortAvailable(int p) {
        boolean[] free = new boolean[1];
        free[0] = false;

        try {
            final ServerSocket ss = new ServerSocket(p);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket s = ss.accept();
                        DataInputStream dis = new DataInputStream(s.getInputStream());
                        String str = (String) dis.readUTF();
                        if (str.equals("" + p)) {
                            free[0] = true;
                        }
                        ss.close();
                    } catch (IOException ex) {
                        Log.Out.println("> port " + p + " not free.");
                    }
                }
            }).start();

            Socket cs = new Socket("localhost", p);
            DataOutputStream dout = new DataOutputStream(cs.getOutputStream());
            dout.writeUTF("" + p);
            dout.flush();
            dout.close();
            cs.close();
        } catch (BindException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
        return free[0];
    }

    public static final boolean UNIX_OPTIMIZE = false; // if we want to re-use older sessions. May wrongly fil if older session is already stucked...

    public static RserverConf newLocalInstance(Properties p) {
        RserverConf server = null;
        if (RserveDaemon.isWindows() || !UNIX_OPTIMIZE) {
            while (!isPortAvailable(RserverPort)) {
                RserverPort++;
            }
            server = new RserverConf(null, RserverPort, null, null, p);
        } else { // Unix supports multi-sessions natively, so no need to open a different Rserve on a new port
            server = new RserverConf(null, -1, null, null, p);
        }
        return server;
    }

    public boolean isLocal() {
        return host == null || host.equals(DEFAULT_RSERVE_HOST) || host.equals("127.0.0.1");
    }

    @Override
    public String toString() {
        String props = null;
        if (properties != null && properties.size() > 0) {
            props = "";
            for (String p : properties.stringPropertyNames()) {
                props = props + p + "=" + properties.getProperty(p, "") + "&";
            }
            if (props.endsWith("&")) {
                props = props.substring(0, props.length() - 1); // remove trailing '&'
            }
        }
        return RURL_START + (login != null ? (login + ":" + password + "@") : "") + (host == null ? DEFAULT_RSERVE_HOST : host) + (port > 0 ? ":" + port : "") + (props != null ? "?" + props : "");
    }

    public final static String RURL_START = "R://";

    public static RserverConf parse(String RURL) {
        String login = null;
        String passwd = null;
        String host = null;
        int port = -1;
        Properties props = null;
        try {
            String loginhostport = null;
            if (RURL.contains("?")) {
                loginhostport = beforeFirst(RURL, "?").substring((RURL_START).length());
                String[] allprops = afterFirst(RURL, "?").split("&");
                props = new Properties();
                for (String prop : allprops) {
                    if (prop.contains("=")) {
                        props.put(beforeFirst(prop, "="), afterFirst(prop, "="));
                    } // else ignore
                }
            } else {
                loginhostport = RURL.substring((RURL_START).length());
            }

            String hostport = null;
            if (loginhostport.contains("@")) {
                hostport = afterFirst(loginhostport, "@");
                String loginpasswd = beforeFirst(loginhostport, "@");
                login = beforeFirst(loginpasswd, ":");
                if (login.equals("user.name")) {
                    login = System.getProperty("user.name");
                }
                passwd = afterFirst(loginpasswd, ":");
            } else {
                hostport = loginhostport;
            }

            if (hostport.contains(":")) {
                host = beforeFirst(hostport, ":");
                port = Integer.parseInt(afterFirst(hostport, ":"));
            } else {
                host = hostport;
            }

            return new RserverConf(host, port, login, passwd, props);
        } catch (Exception e) {
            throw new IllegalArgumentException("Impossible to parse " + RURL + ":\n  host=" + host + "\n  port=" + port + "\n  login=" + login + "\n  password=" + passwd);
        }
    }

    static String beforeFirst(String txt, String sep) {
        if (txt == null) {
            return null;
        }
        if (txt.contains(sep)) {
            int i = txt.indexOf(sep);
            return txt.substring(0, i);
        } else {
            return txt;
        }
    }

    static String afterFirst(String txt, String sep) {
        if (txt == null) {
            return null;
        }
        if (txt.contains(sep)) {
            int i = txt.indexOf(sep);
            if (i >= txt.length()) {
                return "";
            } else {
                return txt.substring(i + 1);
            }
        } else {
            return "";
        }
    }
}
