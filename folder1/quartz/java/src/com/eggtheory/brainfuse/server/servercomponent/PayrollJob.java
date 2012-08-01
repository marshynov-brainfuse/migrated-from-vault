package com.eggtheory.brainfuse.server.servercomponent;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.struts.util.MessageResources;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import com.actbig.datahandler.Tools;
import com.actbig.datahandler.dbholders.DbList;
import com.actbig.datahandler.dbholders.ListProcessor;
import com.actbig.datahandler.dbholders.ListProcessorBase;
import com.actbig.datahandler.service.ItemNotFoundException;
import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.bizobjects.account.UserData;
import com.eggtheory.brainfuse.bizobjects.account.UserService;
import com.eggtheory.brainfuse.bizobjects.user.PayrollActivity;
import com.eggtheory.brainfuse.bizobjects.user.PayrollActivityService;
import com.eggtheory.brainfuse.bizobjects.user.PayrollTransaction;
import com.eggtheory.brainfuse.bizobjects.user.TimeSheet;
import com.eggtheory.brainfuse.bizobjects.user.TimeSheetService;
import com.eggtheory.brainfuse.bizobjects.user.TutorData;
import com.eggtheory.brainfuse.bizobjects.user.TutorPayroll;
import com.eggtheory.brainfuse.inbox.Inbox;
import com.eggtheory.brainfuse.web.tutor.XmlRequester;

/**
 * Title:
 * Description:
 * Copyright:    Copyright (c) 2001
 * Company:
 * @author
 * @version 1.0
 */

public class PayrollJob implements Job
{
    public static String undoneTaskSubject;
    public static String undoneTaskMsgBody;
    public static String payrollSetupSubj;
    public static String payrollSetupMsgBody;
    public static UserData sysAdmin;
    static
    {
        Connection tmpCon               = null;
        try
        {
            Locale l                    = new Locale ("en", "US");
            MessageResources resources  = MessageResources.getMessageResources("com.eggtheory.brainfuse.bizobjects.user.Payroll");
            undoneTaskSubject           = resources.getMessage(l, "payroll.inbox.undoneTask.subject");
            undoneTaskMsgBody           = resources.getMessage(l, "payroll.inbox.undoneTask.msgBody");
            payrollSetupSubj            = resources.getMessage(l, "payroll.inbox.noPayrollOptionSetup.subject");
            payrollSetupMsgBody         = resources.getMessage(l, "payroll.inbox.noPayrollOptionSetup.msgBody");
            sysAdmin                    = UserData.getInstance();
            sysAdmin.setUserID(UserData.TIME_SHEET_SUBMITTER);
            UserService.getInstance().loadUserData(sysAdmin, tmpCon);
        }
        catch (Exception ex)
        {
            undoneTaskSubject           = "Please complete your mandatory task";
            undoneTaskMsgBody           = "You have undone task(s). Please click on the Task tab to view and finish your tasks.";
            payrollSetupSubj            = "Please setup paryroll option";
            payrollSetupMsgBody         = "You have not set up your payroll option yet. Please go to My Account page, then scroll down to edit Payroll Option.";
        }
        finally
        {
            try { tmpCon.close();} catch (Exception ex) {}
        }
    }
    static Logger log						= null;
    static {
    	log	= Logger.getLogger("com.eggtheory.brainfuse.user.Payroll");
    	//System.out.println("Logger " + logger.getName() + " " + logger.getLevel());


    }


    public PayrollJob()
    {
    }

    public void execute ( JobExecutionContext context ) throws org.quartz.JobExecutionException
    {
        try
        {
            String jobName = context.getJobDetail().getName();
            if ( jobName.equals("jobTaskCheck") )
                processTaskCheck();
            else if ( jobName.equals("jobPayroll") )
                processPayroll ();
            else if ( jobName.equals("jobPayrollUpdate") )
            	processPendingTransactionUpdate();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

	/**
	 * check if tutor has undone tasks, send inbox msg
	 */
	public void processTaskCheck() throws WrappedException {
		DbList tutorList = null;

		try {
			tutorList = new DbList<TutorData>()
					.setListProcessor(new ListProcessorBase<TutorData>() {

						public boolean process(TutorData tutorData) {

							try {
								Inbox inbox = tutorData.getInbox();
								String msgBody = "";
								// System.out.println("== " + tutorData.getID()
								// + "  -- " + tutorData.getUserName() + " -- "
								// + tutorData.getEmail1() );

								if (hasUnDoneMandatoryTask(tutorData))
									inbox.sendMessage(sysAdmin, tutorData,
											undoneTaskSubject,
											undoneTaskMsgBody, false);

								// TutorPayroll payroll =
								// TutorPayrollService.getInstance().loadTutorPayrollByUID(tutorData.getUserID(),
								// con);
								TutorPayroll payroll = tutorData
										.getPayroll(true);
								if (payroll.getTutorPayrollID() == 0)
									inbox.sendMessage(sysAdmin, tutorData,
											payrollSetupSubj,
											payrollSetupMsgBody, false);
							} catch (ItemNotFoundException e) {
								e.printStackTrace();
							} catch (WrappedException e) {
								e.printStackTrace();
							}
							return false;
						}
					});
			UserService.getInstance().loadLocalTutors(tutorList);

		} catch (Exception e) {
			WrappedException.processException(e);
		} finally {
		}
	}

	public void processPendingTransactionUpdate() throws WrappedException {
		final List ddTransactionList = new ArrayList(); // direct deposit list

		try {

			java.util.Date startDate = TimeSheet.timeSheetInitDate;
			java.util.Date endDate = TimeSheet.getCurrentPayPeriod()
					.getStartDate();

			DbList timeSheetsToProcess = new DbList<TimeSheet>()
					.setListProcessor(new ListProcessorBase<TimeSheet>() {

						public boolean process(TimeSheet ts)
								throws RuntimeException {
							try {
								List payrollActivities = PayrollActivityService
										.getInstance()
										.loadPayrollActivitiesByTimeSheetID(
												ts.getTimeSheetID());
								// Since it is ordered descendengly the last one
								// should be on
								// top.
								PayrollActivity last = (PayrollActivity) payrollActivities
										.get(0);
								// if (last.isPaymentInProcess()) {
								PayrollTransaction pt = null;
								XmlRequester.PayrollTransactionFinder finder = new XmlRequester.PayrollTransactionFinder(
										last.getRequestID());
								int ptIndex = ddTransactionList.indexOf(finder);
								if (ptIndex > -1) {
									pt = (PayrollTransaction) ddTransactionList
											.get(ptIndex);
									pt.getTimeSheetList().add(ts);
								} else {

									pt = new PayrollTransaction(ts, last
											.getPayroll());
									pt.setTransactionID(last.getTransactionID());
									ddTransactionList.add(pt);
								}
							} catch (WrappedException e) {
								throw new RuntimeException(e);
							}
							
							return false;
						}
					});
			
				TimeSheetService.getInstance().getTimeSheetByDateAndStatus(timeSheetsToProcess, startDate, endDate,
							TimeSheet.TIME_SHEET_STATUS_PAYMENT_INITIATED);
			log.log(Level.INFO, "processing {0} time sheets", new Integer(timeSheetsToProcess.size()));
//			DbList submittedList = TimeSheetService.getInstance().getTimeSheetByDateAndStatus(startDate, endDate,
//							TimeSheet.TIME_SHEET_STATUS_PAYMENT_SETTLED, con);
//			timeSheetsToProcess.addAll(submittedList);

			log.log(Level.FINE, "Finished the loop trans count was {0}", new Integer(ddTransactionList.size()));
			if (ddTransactionList.size() > 0) {
				XmlRequester req = new XmlRequester();
				try {
					DbList paList = req.updatePendingTransactionStatus(ddTransactionList);
					log.log(Level.FINE, "Gotten {0} paList	" , new Integer(paList.size()));
					this.savePayrollActivityListAndSetTimeSheetStatus(paList);

				} catch (Exception e) {
					WrappedException.processException(e);
				}
			}else{
				log.log(Level.WARNING, "No transactions to process");
			}

		} catch (Exception e) {
			WrappedException.processException(e);
		} finally {
		}
	}


    public void processPayroll () throws WrappedException
    {
        Connection con              = null;
        List ddTransactionList = new ArrayList();       // direct deposit list
        DbList checkByMailActivityList      = DbList.getInstance();  // check
																		// by
																		// mail
																		// list
        DbList payrollActivityList  = DbList.getInstance();

        try
        {
        	log.fine("Starting payroll update");
            con       = Tools.establishConnection( true );

            java.util.Date startDate    = TimeSheet.timeSheetInitDate;
            java.util.Date endDate      = TimeSheet.getCurrentPayPeriod().getStartDate();
            DbList failedList           = TimeSheetService.getInstance().getTimeSheetByDateAndStatus(startDate, endDate, TimeSheet.TIME_SHEET_STATUS_FAILED, con);

            DbList submittedList        = TimeSheetService.getInstance().getTimeSheetByDateAndStatus(startDate, endDate, TimeSheet.TIME_SHEET_STATUS_SUBMITTED, con);
            failedList.addAll( submittedList );
            failedList.addAll(TimeSheetService.getInstance().getTimeSheetByDateAndStatus(startDate, endDate, TimeSheet.TIME_SHEET_STATUS_FAILED_DIRECT_DEPOSIT, con));

            log.info("Processing " + failedList.size() + " timesheets.");
            for ( int i=0; i< failedList.size(); i++)
            {
                TimeSheet ts        = (TimeSheet) failedList.getElement(i);
                //System.out.println( ts.getUID() + ": " + ts.getStartDate() + " -- " + ts.getEndDate() );
                TutorData tutorData = (TutorData)TutorData.getInstance();
                tutorData.setUserID ( ts.getUID() );
                UserService.getInstance().loadTutorInfoByID ( tutorData, con );
                int tutorID         = tutorData.getID();



                TutorData parentTutor = (TutorData)tutorData.getParent();

                if (tutorData.isLocalTutor()) {
					// System.out.println( tutorData.getUserName() );
					// TutorPayroll payroll =
					// TutorPayrollService.getInstance().loadTutorPayrollByUID(tutorID,
					// con);
					TutorPayroll payroll = tutorData.getPayroll(true);
					int payrollOptionID = payroll.getPayrollOptionID();
					if (payrollOptionID == 0) {
						// no payroll option found, send tutor inbox msg to set
						// up payroll option
						// System.out.println( " NO PAYROLL OPTION FOUND " );
						/*
						 * String msgBody = "You have not set up your payroll
						 * option yet. Please go to My Account page, then scroll
						 * down to edit Payroll Option. " + "Payroll option is
						 * used for issueing a check when processing your
						 * timesheet. If you don't setup a payroll option, the
						 * system will process your timesheet but no check will
						 * be issued to you.";
						 */

						if (parentTutor.getUserID() != 0)
							parentTutor.getInbox().sendMessage(sysAdmin, parentTutor, payrollSetupSubj, payrollSetupMsgBody,
											false);
						if (tutorData.getAccount().getAttribute("PAYROLL_PARENT_ID") == null)
							tutorData.getInbox().sendMessage(sysAdmin, tutorData, payrollSetupSubj, payrollSetupMsgBody,
											false);

						payroll.setTutorPayrollID(-1);
						addTimeSheetToPayrollActivityList(payrollActivityList, ts, payroll,
										PayrollActivity.REQUEST_STATUS_FAIL, PayrollActivity.COMMENTS_NO_PAYROLL_OPTION);
					} else {
						if (hasUnDoneMandatoryTask(tutorData, endDate)) {
							tutorData.getInbox().sendMessage(sysAdmin, tutorData, undoneTaskSubject, undoneTaskMsgBody,
											false);
							if (parentTutor.getUserID() != 0)
								parentTutor.getInbox().sendMessage(sysAdmin, parentTutor, undoneTaskSubject,
												undoneTaskMsgBody, false);

							addTimeSheetToPayrollActivityList(payrollActivityList, ts, payroll,
											PayrollActivity.REQUEST_STATUS_FAIL, PayrollActivity.COMMENTS_UNDONE_MANDATORY);
							
						} else if (payrollOptionID == TutorPayroll.TUTOR_PAYROLL_PAYPAL) {
							// if the payroll is not yet authroized we can't use
							// this payroll
							// for direct deposit process DD payroll through
							// Alliance. if succeed,
							// set timesheet status to 'paid'. otherwise set to
							// 'failed', and send tutor inbox msg
							addTimeSheetToList(ddTransactionList, ts, payroll);
							// System.out.println( " DirectDeport" );
						} else {
							// Change this to just else to allow direct
							// deposit to be paid using this payroll// (
							// payrollOptionID ==
							// TutorPayroll.TUTOR_PAYROLL_CHECK_BY_MAIL )
							// change timesheet status to 'processed' so that
							// admin can print
							addTimeSheetToPayrollActivityList(checkByMailActivityList, ts, payroll,
											PayrollActivity.REQUEST_STATUS_SUCCESS, PayrollActivity.COMMENTS_CHECK_BY_MAIL);
							// System.out.println( " checkByMail " );
						}
					}
				}
			}

            savePayrollActivityListAndSetTimeSheetStatus ( payrollActivityList );
            savePayrollActivityListAndSetTimeSheetStatus ( checkByMailActivityList );

            processAutomaticPayroll ( ddTransactionList );


        }
        catch (Exception e)
        {
            WrappedException.processException(e);
        }
        finally
        {
            try { con.close();} catch (Exception ex) {}
        }
    }

    public boolean hasUnDoneMandatoryTask (TutorData tutorData) throws WrappedException
    {
        boolean hasTask = false;
        try
        {
            hasTask = tutorData.getInbox().hasUndoneMandatoryTasks();
        }
        catch (Exception ex)
        {
            WrappedException.processException(ex);
        }
        return hasTask;

    }
    public boolean hasUnDoneMandatoryTask (TutorData tutorData, Date endDate) throws WrappedException
    {
        boolean hasTask = false;
        try
        {
            hasTask = tutorData.getInbox().hasUndoneMandatoryTasks(endDate);
        }
        catch (Exception ex)
        {
            WrappedException.processException(ex);
        }
        return hasTask;
    }

    private void addTimeSheetToList ( List payrollTransactionList, TimeSheet ts, TutorPayroll payroll ) throws WrappedException
    {
        boolean ptFound = false;
        for (Iterator iter = payrollTransactionList.iterator(); iter.hasNext(); )
        {
            PayrollTransaction item = (PayrollTransaction) iter.next();
            //if ( item.getTutorPayroll().getUID() == ts.getUID() )
            if ( item.getTutorPayroll().getTutorPayrollID() == payroll.getTutorPayrollID() )
            {
                item.getTimeSheetList().addElement( ts );
                ptFound = true;
                break;
            }
        }
        if ( !ptFound )
        {
            try
            {
                PayrollTransaction newPT = new PayrollTransaction( ts, payroll );
                payrollTransactionList.add( newPT );
            }
            catch (Exception ex)
            {
                WrappedException.processException(ex);
            }
        }
    }

    public void addTimeSheetToPayrollActivityList( DbList payrollActivityList, TimeSheet ts, TutorPayroll payroll, int reqStatus, String comments )
    {
        boolean paFound = false;
        for (Iterator iter = payrollActivityList.iterator(); iter.hasNext(); )
        {
            PayrollActivity item = (PayrollActivity)iter.next();
            if ( item.getRequestID() == payroll.getTutorPayrollID() )
            {
                item.getTimeSheetList().addElement( ts );
                paFound = true;
                break;
            }
        }
        if ( !paFound )
        {
            PayrollActivity activity = PayrollActivity.getInstance();
            activity.setNewFlag( true );
            activity.setRequestID( payroll.getTutorPayrollID() );
            activity.setRequestType(PayrollActivity.REQUEST_TYPE_GENERAL);
            //activity.setRequestStatus( PayrollActivity.REQUEST_STATUS_FAIL );
            activity.setRequestStatus( reqStatus );
            activity.setCreateDate( new java.util.Date() );
            activity.setTransactionID( "" );
            activity.setTransactionStatus( 0 );
            //activity.setComments( "Undone mandatory task found." );
            activity.setComments( comments );
            activity.setInputBy(UserData.TIME_SHEET_SUBMITTER);
            activity.getTimeSheetList().addElement( ts );
            activity.getTimeSheetList().setNewFlag( true );
            payrollActivityList.addElement( activity );
        }

    }

    private void savePayrollActivityListAndSetTimeSheetStatus(DbList payrollActivityList) throws WrappedException {
		if (payrollActivityList != null) {
			for (int i = 0; i < payrollActivityList.size(); i++) {
				try {
					/*
					 * save into PAYROLL_ACTIVITY and PAYROLL_TIMESHEET table
					 */
					
					PayrollActivity pa = (PayrollActivity) payrollActivityList.getElement(i);
					log.log(Level.FINE, "Saving payroll {0}" , Tools.safeToString(pa));
					pa.save();

					// if request failed, notify tutor by sending inbox msg
				} catch (Exception ex) {
					log.log(Level.SEVERE, "Error saving timesheet", ex);
					try {
						WrappedException.processException(ex);
					} catch (Exception e) {
					}
				}
			}
		}
	}

	

    public void processAutomaticPayroll ( List payrollTransactionList ) throws WrappedException
    {
        //System.out.println("\n ptList size: " + payrollTransactionList.size() );
    	log.info("Processing direct deposit for " + payrollTransactionList.size());
        if ( payrollTransactionList.size() > 0 )
        {
            XmlRequester req = new XmlRequester();
            try
            {
                DbList paList    = req.createCreditTransaction( payrollTransactionList );

                this.savePayrollActivityListAndSetTimeSheetStatus( paList );

            }
            catch (Exception e)
            {
                WrappedException.processException(e);
            }
        }
    }

    /**
     *
     */
    static public void main(String[] args)
    {
        try
        {
            PayrollJob job          = new PayrollJob ();
            job.processPayroll();
        }
        catch (Exception ex) { ex.printStackTrace();

        }


    }
}
