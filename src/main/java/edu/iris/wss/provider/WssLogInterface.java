package edu.iris.wss.provider;

import org.apache.log4j.Logger;

/**
 *
 * @author mike
 *
 * The interface for logging interface.
 */
public abstract class WssLogInterface {
    public static final Logger logger = Logger.getLogger(WssLogInterface.class);

    public WssLogInterface() { }

    public abstract int getSometning(String wssSomething);

    public abstract void logOut1();

    public abstract int logOutTwo(String msg);
}

