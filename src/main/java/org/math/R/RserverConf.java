package org.math.R;

import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;
import java.io.File;

public class RserverConf {

    public static String DEFAULT_RSERVE_HOST = "localhost"; // InetAddress.getLocalHost().getHostName(); should not be used, as it seems an incoming connection, not authorized
    public static int DEFAULT_RSERVE_PORT = 6311;
    public static String DEFAULT_RSERVE_WORKDIR = System.getProperty("user.home") + "/" + ".Rserve";

    RConnection connection;
    public String host;
    public int port;
    public String login;
    public String password;
    public String workdir;

    public RserverConf(String RserverHostName, int RserverPort, String login, String password, String workdir) {
        this.host = RserverHostName;
        this.port = RserverPort;
        this.login = login;
        this.password = password;
        this.workdir = workdir != null ? workdir : DEFAULT_RSERVE_WORKDIR;
    }
    public static long CONNECT_TIMEOUT = 5000;

    public static abstract class TimeOut {

        /**
         * @return the result
         */
        public Object getResult() {
            return result;
        }

        public static class TimeOutException extends Exception {

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

    public synchronized RConnection connect() {
        //Logger.err.print("Connecting " + toString()+" ... ");

        TimeOut t = new TimeOut() {

            protected Object defaultResult() {
                return -2;
            }

            protected Object command() {
                int n = 10;
                while ((n--) > 0) {
                    try {
                        if (host == null) {
                            if (port > 0) {
                                connection = new RConnection(DEFAULT_RSERVE_HOST, port);
                            } else {
                                connection = new RConnection(DEFAULT_RSERVE_HOST, DEFAULT_RSERVE_PORT);
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
                        Log.Err.println("Failed to connect on host:" + host + " port:" + port + " login:" + login + "\n  " + ex.getMessage());
                    }
                }
                return -1;
            }
        };

        try {
            t.execute(CONNECT_TIMEOUT);
        } catch (Exception e) {
            Log.Err.println("Connection " + toString() + " failed: " + e.getMessage());
        }

        if (((Integer) t.getResult()) != 0) {
            Log.Err.println("Connection " + toString() + " failed.");
            return null;
        } else {
            return connection;
        }
    }

    public boolean isLocal() {
        return host == null || host.equals(DEFAULT_RSERVE_HOST) || host.equals("localhost");
    }

    @Override
    public String toString() {
        return RURL_START + (login != null ? (login + ":" + password + "@") : "") + (host == null ? DEFAULT_RSERVE_HOST : host) + (port > 0 ? ":" + port : "") + (workdir != null ? "/" + workdir : DEFAULT_RSERVE_WORKDIR);
    }

    public final static String RURL_START = "R://";

    public static RserverConf parse(String RURL) {
        String login = null;
        String passwd = null;
        String host = null;
        int port = -1;
        String workdir = null;
        //Properties props = null;
        try {
            String loginhostportpath = null;
            if (RURL.contains("?")) {
                loginhostportpath = beforeFirst(RURL, "?").substring((RURL_START).length());
//                String[] allprops = afterFirst(RURL, "?").split("&");
//                props = new Properties();
//                for (String prop : allprops) {
//                    if (prop.contains("=")) {
//                        props.put(beforeFirst(prop, "="), afterFirst(prop, "="));
//                    } // else ignore
//                }
            } else {
                loginhostportpath = RURL.substring((RURL_START).length());
            }

            String hostportpath = null;
            if (loginhostportpath.contains("@")) {
                hostportpath = beforeFirst(afterFirst(loginhostportpath, "@"), "/");
                String loginpasswd = beforeFirst(loginhostportpath, "@");
                login = beforeFirst(loginpasswd, ":");
                if (login.equals("user.name")) {
                    login = System.getProperty("user.name");
                }
                passwd = afterFirst(loginpasswd, ":");
            } else {
                hostportpath = loginhostportpath;
            }

            if (hostportpath.contains(":")) {
                host = beforeFirst(hostportpath, ":");
                port = Integer.parseInt(beforeFirst(afterFirst(hostportpath, ":"), "/"));
            } else {
                host = hostportpath;
            }

            if (hostportpath.contains("/")) {
                workdir = afterFirst(hostportpath, "/");
                hostportpath = beforeFirst(hostportpath, "/");
            }

            return new RserverConf(host, port, login, passwd, workdir != null ? workdir : DEFAULT_RSERVE_WORKDIR);
        } catch (Exception e) {
            throw new IllegalArgumentException("Impossible to parse " + RURL + ":\n  host=" + host + "\n  port=" + port + "\n  login=" + login + "\n  password=" + passwd + "\n  workdir=" + workdir);
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
