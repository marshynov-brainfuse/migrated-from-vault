package com.eggtheory.brainfuse.server.servercomponent;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import java.util.*;
import java.util.logging.Logger;

import com.actbig.datahandler.dbholders.DbList;
import com.actbig.datahandler.dbholders.ListProcessor;
import com.actbig.datahandler.dbholders.ListProcessorBase;
import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.bizobjects.account.*;
import com.eggtheory.brainfuse.bizobjects.user.*;
import com.eggtheory.brainfuse.server.ServerConfig;
import com.eggtheory.brainfuse.utils.DateUtils;
import com.eggtheory.brainfuse.web.calendar.CalendarControllerBase;

import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import com.eggtheory.brainfuse.Utilities;

/**
 * Title: Description: Copyright: Copyright (c) 2001 Company:
 * 
 * @author
 * @version 1.0
 */

public class TimeSheetJob implements Job {
	public static final int DEBUG_LEVEL = 1;
	static com.eggtheory.brainfuse.log.Logger log						= null;
    static {
    	log	= new com.eggtheory.brainfuse.log.Logger(TimeSheetJob.class);
    	//System.out.println("Logger " + logger.getName() + " " + logger.getLevel());


    }
	public TimeSheetJob() {
	}

	public void execute(JobExecutionContext context)
			throws org.quartz.JobExecutionException {
		try {
			Date startDate = null;
			Date endDate = null;
			boolean previous = false;
			try {
				String strPrev = (String) context.getJobDetail()
						.getJobDataMap().get("previous");
				previous = Boolean.valueOf(strPrev).booleanValue();
			} catch (Exception ex) {
			}

			if (!previous) {
				SimpleDateFormat parser = new SimpleDateFormat(
						"yyyy-MM-dd HH:mm:ss");
				try {
					String strStartDate = (String) context.getJobDetail()
							.getJobDataMap().get("startDate");
					String strEndDate = (String) context.getJobDetail()
							.getJobDataMap().get("endDate");
					startDate = parser.parse(strStartDate);
					endDate = parser.parse(strEndDate);
				} catch (Exception ex) {
					startDate = TimeSheet.getCurrentPayPeriod().getStartDate();
					endDate = TimeSheet.getCurrentPayPeriod().getEndDate();
					// put the endate to be 1 day minute today.
					// ex.printStackTrace();
				}
			} else {

				startDate = TimeSheet.getPreviousPayPeriod().getStartDate();
				endDate = TimeSheet.getPreviousPayPeriod().getEndDate();

			}
			System.out
					.println("TimeSheetJob.execute: Submitting timesheets for the PayPeriod from "
							+ startDate + " to " + endDate);
			submitTimeSheets(startDate, endDate);
			// System.out.println("TimeSheetJob :: execute()" );
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private int count = 0;

	public void submitTimeSheets(final Date startDate, final Date endDate)
			throws WrappedException {
		DbList tutorList = new DbList<TutorData>();
		tutorList
				.setListProcessor(new ListProcessorBase<TutorData>() {

					public boolean process(TutorData tutorData) {
						try {
							if ( tutorData.getAccountID() == CalendarControllerBase.getSubstituteAccount().getAccountID())
								return false;
							
							int tutorID = tutorData.getID();
							TimeSheet ts = null;
							log.info("Retrieving time sheet for user {0}:{1} for the period from {2} to {3}",
									tutorData.getID() ,
									tutorData.getUserName(),
									startDate,
									 endDate);
							ts = TimeSheetService.getInstance()
									.getTimeSheetByUserIDAndDate(tutorID,
											startDate, endDate);

							if (ts == null) {
								ts = TimeSheet.getInstance();
								ts.setStartDate(startDate);
								ts.setEndDate(endDate);
								ts.setUID(tutorID);
								ts.setNewFlag(true);
								ts.setStatus(TimeSheet.TIME_SHEET_STATUS_SUBMITTED);
								log.fine("Creating a new timesheet");
								// ts.setSubmitDate( new Date() );
								// ts.setUID( tutorID );
							}
							ts.setSubmitDate(new Date());
							ts.setModifiedBy(UserData.TIME_SHEET_SUBMITTER);

							// get uncounted sessions
							List newSessionList = ts
									.getUncountedSessionEntries();
							log.fine("       new entry size: {0}", newSessionList.size());
							log.fine("CacheStatistics: {0}" , UserManager.getInstance().getCacheStatistics());
							if (newSessionList.size() > 0) {
								count++;
								ts.getEntries().addAll(newSessionList);
								
								TimeSheetService.getInstance()
										.saveTimeSheet(ts);
							}
						} catch (Exception ex) {
							log.severe("Error while processing", ex);
							WrappedException.notifyOfError("TimesheetJob.submitTimeSheets", ex);
							
						}
						return false;
					}

				});
		

		// try
		// {
//		log("total tutors: " + tutorList.size(), 0);
		tutorList = UserService.getInstance().loadLocalTutors(tutorList);
		log.info("Finished processing timesheets");

//		for (int i = 0; i < tutorList.size(); i++) {
//			try {
//				TutorData tutorData = (TutorData) tutorList.getElement(i);
//				
//			} catch (Exception ex) {
//				try {
//					WrappedException.processException(ex);
//				} catch (Exception e) {
//				}
//			}
//
//		}
		log("total timesheet submitted: " + count, 0);
		/*
		 * } catch (Exception ex) { WrappedException.processException(ex); }
		 */

	}

	private void log(String msg, int debugLevel) {
		if (debugLevel < DEBUG_LEVEL)
			System.out.println(msg);
	}

}
