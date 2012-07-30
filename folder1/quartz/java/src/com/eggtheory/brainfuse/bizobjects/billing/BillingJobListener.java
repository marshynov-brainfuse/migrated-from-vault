package com.eggtheory.brainfuse.bizobjects.billing;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
public class BillingJobListener implements org.quartz.JobListener 
{
	private String name;
	
	public BillingJobListener()
	{
		this ("BillingJobListener");
	}
	
	public BillingJobListener (String name)
	{
		this.name = name;
	}
	
	public String getName()
	{
		return name;
	}

	public void jobExecutionVetoed(JobExecutionContext context)
	{
		
	}
	
	public void jobToBeExecuted(JobExecutionContext context)
	{
		
	}
	
	public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException)
	{
		JobReport jobReport = (JobReport)context.getResult() ;
		jobReport.sendStatus() ;
	}
}

