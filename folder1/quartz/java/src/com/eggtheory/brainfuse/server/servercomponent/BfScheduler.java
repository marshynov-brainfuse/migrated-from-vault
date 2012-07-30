package com.eggtheory.brainfuse.server.servercomponent;

/**
 * Title:
 * Description:
 * Copyright:    Copyright (c) 2001
 * Company:
 * @author
 * @version 1.0
 */
import java.util.Date;

import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.SchedulerMetaData;
import org.quartz.SimpleTrigger;
import org.quartz.helpers.TriggerUtils;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.JobListener;
import org.quartz.TriggerListener;
import org.quartz.Trigger;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;


import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.server.ServerConfig;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class BfScheduler
{
    private StdSchedulerFactory stdSF = null;

    public BfScheduler()
    {
        stdSF = new StdSchedulerFactory();
    }

    public void schedule () throws WrappedException
    {
        try
        {
            Log lg          = LogFactory.getLog( BfScheduler.class );
            Scheduler sched = stdSF.getScheduler();
            sched.addGlobalJobListener( new BfJobListener ("bfJobListener", lg) );
            sched.addGlobalTriggerListener( new BfTriggerListener("bfTriggerListener", lg) );
            cleanUp( sched );

            // JOB: TimeSheet
            /*JobDetail job           = new JobDetail( "jobTimeSheet", "groupTimeSheet", TimeSheetJob.class );
            CronTrigger cTrigger    = new CronTrigger( "triggTimeSheet", "group1", "jobTimeSheet", "groupTimeSheet", "0 27 11 ? * *");
            Date firstFireTime      = sched.scheduleJob(job, cTrigger);
            lg.info( job.getFullName() + " will run at: " + firstFireTime + " & repeat based on expression: " + cTrigger.getCronExpression());
            */

            // JOB: Payroll
            /*JobDetail job           = new JobDetail( "jobPayroll", "groupPayroll", TimeSheetJob.class );
            CronTrigger cTrigger    = new CronTrigger( "triggPayroll", "triggGroupPayroll", "jobPayroll", "groupPayroll", "0 0 15 24/14 * ?");*/
            JobDetail job           = new JobDetail( "jobPayroll", "groupPayroll", TimeSheetJob.class );
            Calendar cal            = Calendar.getInstance();
            cal.set(2004, 7, 23);
            SimpleDateFormat formater       = new SimpleDateFormat("MM/dd/yyyy");
            Date [] startTimeArr          = new Date[]{cal.getTime(), formater.parse("08/13/2004")};
            String [] cropExprArr         = new String[]{"0 0 15 ? * TUES/2", "0 0 15 ? * WED/2"};
            Date startTime          = null;
            String cropExpr         = null;

            for (int i = 0; i < startTimeArr.length; i++)
            {
                startTime          = startTimeArr[i];
                cropExpr         = cropExprArr[i];


                CronTrigger cTrigger    = new CronTrigger( "triggPayroll", "triggGroupPayroll", "jobPayroll", "groupPayroll", startTime, new Date(2005, 7, 1),cropExpr, TimeZone.getDefault());

                Date firstFireTime           = cTrigger.getFireTimeAfter(new Date());
                System.out.println("First fire time = " + firstFireTime);
            }
            CronTrigger cTrigger    = new CronTrigger( "triggPayroll", "triggGroupPayroll", "jobPayroll", "groupPayroll", startTime, new Date(2005, 7, 1),cropExpr, TimeZone.getDefault());
            Date firstFireTime      = sched.scheduleJob(job, cTrigger);
            sched.start();
            System.out.println(new Date(1093374000000l));

            //lg.info(" will run at: " + firstFireTime + " & repeat based on expression: " + cTrigger.getCronExpression());


            //sched.start();
        }
        catch (Exception ex)
        {
            WrappedException.processException(ex);
        }
    }

    /**
     * remove job/trigger entries that may be lingering in a JDBCJobStore
     */
    public void cleanUp ( Scheduler sched )  throws Exception
    {
        String[] groups = sched.getTriggerGroupNames();
        for (int i = 0; i < groups.length; i++)
        {
            String[] names = sched.getTriggerNames(groups[i]);
            for (int j = 0; j < names.length; j++)
                sched.unscheduleJob(names[j], groups[i]);
        }
        groups = sched.getJobGroupNames();
        for (int i = 0; i < groups.length; i++)
        {
            String[] names = sched.getJobNames(groups[i]);
            for (int j = 0; j < names.length; j++)
                sched.deleteJob(names[j], groups[i]);
        }
    }


    public static void main(String[] args)
    {
        try
        {
            ServerConfig.getInstance();

            BfScheduler testSched = new BfScheduler();
            testSched.schedule();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


class BfJobListener implements JobListener
{
    private String name;
    private Log log;

    public BfJobListener(String name, Log log)
    {
        this.name = name;
        this.log  = log;
    }

    public String getName()
    {
        return name;
    }
    public Log getLog()
    {
        return log;
    }

    public void jobToBeExecuted (JobExecutionContext context)
    {
        log.info(".......(" + name + "): jobToBeExecuted: " + context.getJobDetail().getFullName());
    }

    public void jobWasExecuted (JobExecutionContext context, JobExecutionException jobException)
    {
        log.info(".......(" + name + "): jobWasExecuted: " + context.getJobDetail().getFullName());
    }

    public void jobExecutionVetoed (JobExecutionContext context)
    {
         // TODO Auto-generated method stub
    }
}

class BfTriggerListener implements TriggerListener
{
    private String name;
    private Log log;

    public BfTriggerListener(String name, Log log)
    {
        this.name = name;
        this.log  = log;
    }

    public String getName()
    {
        return name;
    }
    public Log getLog()
    {
        return log;
    }

    public void triggerFired (Trigger trigg, JobExecutionContext context)
    {
        log.info(".......(" + name + "): triggerFired: " + trigg.getFullName() + " at: " + context.getFireTime() + " for job: " + context.getJobDetail().getFullName());
    }

    public boolean vetoJobExecution (Trigger trigg, JobExecutionContext context)
    {
        log.info(".......(" + name + "): vetoJobExecution: " + trigg.getFullName() + " job: " + context.getJobDetail().getFullName());
        return false;
    }

    public void triggerComplete (Trigger trigg, JobExecutionContext contest, int code)
    {
         log.info(".......(" + name + "): triggerComplete: " + trigg.getFullName() + " job: " + trigg.getFullJobName() );
    }

    public void triggerMisfired(Trigger trigg)
    {
        log.info(".......(" + name + "): triggerMisfired: " + trigg.getFullName() + " job: " + trigg.getFullJobName() );
        try
        {
            throw new WrappedException("(" + name + "): triggerMisfired", WrappedException.FATAL);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }


}



