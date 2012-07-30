package com.eggtheory.brainfuse.server.servercomponent;

/**
 * Title:
 * Description:
 * Copyright:    Copyright (c) 2001
 * Company:
 * @author
 * @version 1.0
 */
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.actbig.datahandler.dbholders.DbList;
import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.bizobjects.account.UserData;
import com.eggtheory.brainfuse.bizobjects.account.UserService;
import com.eggtheory.brainfuse.bizobjects.user.TimeSheet;
import com.eggtheory.brainfuse.bizobjects.user.TimeSheetService;
import com.eggtheory.brainfuse.bizobjects.user.TutorData;
import com.eggtheory.brainfuse.server.ServerConfig;


public class TimeSheetSubmitter 
{

    private static java.text.SimpleDateFormat sp    = new java.text.SimpleDateFormat("hh:mma");
    private boolean stopRunning                     = false;
    public static final long DEFAULT_RUN_INTERVAL   = 10000;
    private long runInterval                        = DEFAULT_RUN_INTERVAL;
    private long regRunInterval                     = DEFAULT_RUN_INTERVAL;
    private long firstWait                          = 1;


    public TimeSheetSubmitter()
    {
    }

    public void init()
    {
        String runTime      = ServerConfig.getInstance().getServerProperty("com.brainfuse.TimeSheetSubmitter.firstRunTime");
        if ( runTime != null && runTime.trim().length()!=0)
        {
            try
            {
                Date d = sp.parse(runTime, new ParsePosition(0));
                System.out.println("TimeSheetSubmitter RunTime: " + d);
                Date currDate = new Date();
                Calendar c = Calendar.getInstance();
                c.setTime(d);

                Calendar currCalendar = Calendar.getInstance();
                int date = currCalendar.get(Calendar.DATE);
                if (c.get(Calendar.HOUR_OF_DAY) < currCalendar.get(Calendar.HOUR_OF_DAY))
                {
                    date++;
                }
                else if (c.get(Calendar.HOUR_OF_DAY) == currCalendar.get(Calendar.HOUR_OF_DAY) &&
                         c.get(Calendar.MINUTE) < currCalendar.get(Calendar.MINUTE))
                {
                    //System.out.println("Hour is the same Here" + c.get(Calendar.MINUTE) + " " + currCalendar.get(Calendar.MINUTE));
                    date++;
                }
                c.set(currCalendar.get(Calendar.YEAR), currCalendar.get(Calendar.MONTH), date);
                Date nextRun = c.getTime();
                System.out.println("TimeSheetSubmitter NextRun: " + nextRun);
                firstWait = nextRun.getTime() - currDate.getTime();
                if ( firstWait< 0) firstWait  = Math.abs(firstWait);
            }
            catch (Exception ex)
            {
                firstWait   = 1;
            }
        }
        this.regRunInterval    = ServerConfig.getInstance().getServerIntProperty("com.brainfuse.TimeSheetSubmitter.runInterval", 60 *1000);
        System.out.println("TimeSheetSubmitter firstWait: " + firstWait + "   TimeSheetSubmitter regRunInterval: "  + regRunInterval );
        runInterval            = firstWait;

    }

    public void stop ()
    {
        synchronized (this)
        {
            stopRunning = true;
            notify ();
        }
    }
    public void shutDown()
    {
        stop();
    }

//    public void run ()
//    {
//        while (!stopRunning)
//        {
//            if (!stopRunning)
//            {
//                synchronized (this)
//                {
//                    try
//                    {
//                        System.out.println (" ### IN WAIT STATE ## ");
//                        wait (runInterval);
//                    }
//                    catch (InterruptedException ie)
//                    {
//                        ie.printStackTrace() ;
//                    }
//                }
//                runInterval = regRunInterval;
//                try
//                {
//                    submitTimeSheets();
//                }
//                catch (Exception e )
//                {
//                    e.printStackTrace() ;
//                }
//                //System.out.println (" ### AWAKE STATE ## ");
//            }
//        }
//    }
//
//
//    public void submitTimeSheets () throws WrappedException
//    {
//        DbList tutorList = null;
//        int count = 0;
//
//        try
//        {
//            tutorList = UserService.getInstance().loadLocalTutors();
//            System.out.println("total tutors: " + tutorList.size());
//            for ( int i=0; i< tutorList.size(); i++)
//            {
//                TutorData tutorData = (TutorData) tutorList.getElement(i);
//                int tutorID         = tutorData.getID();
//                TimeSheet ts        = TimeSheetService.getInstance().getCurrentTimeSheetByUserID( tutorID );
//
//                System.out.println( tutorData.getID() + " : " + tutorData.getUserName() );
//
//                if ( ts == null )
//                {
//                    ts = TimeSheet.getInstance();
//                    ts.setStartDate( TimeSheet.getCurrentPayPeriod().getStartDate() );
//                    ts.setEndDate( TimeSheet.getCurrentPayPeriod().getEndDate() );
//                    ts.setUID( tutorID );
//                    ts.setNewFlag( true );
//                    ts.setStatus( TimeSheet.TIME_SHEET_STATUS_SUBMITTED );
//                    //ts.setSubmitDate( new Date() );
//                    //ts.setUID( tutorID );
//                }
//                ts.setSubmitDate( new Date() );
//                ts.setModifiedBy ( UserData.TIME_SHEET_SUBMITTER );
//               
//
//                // get uncounted sessions
//                List newSessionList = ts.getUncountedSessionEntries();
//                System.out.println( "       new entry size: " + newSessionList.size());
//
//                if ( newSessionList.size() > 0 )
//                {
//                   count ++;
//                   ts.getEntries().addAll( newSessionList );
//                   //System.out.println( "       new new entry size: " + ts.getEntries().size());
//
//                   //if ( tutorData.getUserName().equals("Calum") )
//                       TimeSheetService.getInstance().saveTimeSheet( ts );
//                }
//            }
//            System.out.println( "total timesheet submitted: " + count);
//        }
//        catch (Exception ex)
//        {
//            WrappedException.processException(ex);
//        }
//
//    }

    public static void main(String[] args)
    {
        try
        {
            TimeSheetSubmitter tss = new TimeSheetSubmitter();
            //tss.submitTimeSheets();
            tss.init();
//            tss.run();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }

    }

}
