package com.eggtheory.brainfuse.bizobjects.billing;

/**
 * Title:
 * Description:
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
 */

public class BillManagerServer
{

    private Thread billThread;
    private BillManager billManager;

    public BillManagerServer(int runInterval)
    {
        billManager= new BillManager (runInterval );
        //billThread = new Thread (billManager);
        //billThread.start() ;
    }

    public void stop ()
    {
        billManager.stop() ;
    }

    public static void main (String[] arg)
    {
        BillManagerServer server= new BillManagerServer (120 *1000);
        try
        {
            Thread.sleep( 10000);
        }
        catch (Exception e)
        {
            e.printStackTrace() ;
        }


        System.out.println( " ######## AWAKE FROM SLEEP ## ");
        server.stop() ;
    }

}