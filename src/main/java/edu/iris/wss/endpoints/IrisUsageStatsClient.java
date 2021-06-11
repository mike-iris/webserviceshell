package edu.iris.wss.endpoints;

import edu.iris.wss.provider.WssLogInterface;

public class IrisUsageStatsClient extends WssLogInterface {
    @Override
    public int getSometning(String wssSomething) {
        System.out.println("***** ***** WssIface get something");
        return 0;
    }

    @Override
    public void logOut1() {
        System.out.println("***** ***** WssIface logOut1");
    }

    @Override
    public int logOutTwo(String msg) {
        System.out.println("***** ***** WssIface logOutTwo msg: " + msg);
        return 223;
    }
}
