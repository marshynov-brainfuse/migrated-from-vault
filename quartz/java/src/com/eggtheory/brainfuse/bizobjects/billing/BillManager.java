package com.eggtheory.brainfuse.bizobjects.billing;

/**
 * Title:
 * Description:
 * Copyright:    Copyright (c) 2002
 * Company:
 * @author
 * @version 1.0
* Editing for vault2git
 */

import static com.eggtheory.brainfuse.utils.resources.ResourceBundleUtils.getString;

import java.sql.Connection;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Vector;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;

import com.actbig.datahandler.Tools;
import com.actbig.datahandler.dbholders.DbList;
import com.actbig.mail.Emailer;
import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.Utilities;
import com.eggtheory.brainfuse.bizobjects.account.Account;
import com.eggtheory.brainfuse.bizobjects.account.AccountService;
import com.eggtheory.brainfuse.bizobjects.account.Attribute;
import com.eggtheory.brainfuse.bizobjects.account.BillingContract;
import com.eggtheory.brainfuse.bizobjects.account.UserData;
import com.eggtheory.brainfuse.bizobjects.account.UserService;
import com.eggtheory.brainfuse.bizobjects.user.SessionLogService;
import com.eggtheory.brainfuse.server.ServerConfig;


public class BillManager implements Job
{


    private static final String TIME_ALLOWANCE_ATTR="TIME_ALLOWANCE";
    private static final String BILLABLE_ATTR="BILLABLE";
    private static final String ACCT_WRAP_UP_TIME="WRAP_UP_TIME";

    private static final String USER_TYPE_STUDENT="1";
    private static final String USER_TYPE_TUTOR="2";

    private static final int SECONDS_PER_MINUTE=60;
    private static final int MINUTES_PER_HOUR=60;
    private static final int MILLIS_PER_SECOND=1000;

    public static final long DEFAULT_RUN_INTERVAL=10 * MILLIS_PER_SECOND;
    public static final int DEFAULT_TIME_ALLOWANCE=5 ;
    public static final int DEFAULT_ACCT_WRAP_UP_TIME=16;

    private long runInterval = DEFAULT_RUN_INTERVAL;
    private long regRunInterval = DEFAULT_RUN_INTERVAL;
    private long firstWait      = 1;
    private int timeAllowance = DEFAULT_TIME_ALLOWANCE;


    private boolean stopRunning=false;
    private BillComment billComment;
    private static Hashtable calculators;
    private static Vector tutorCalculators;
    private static Vector studentCalculators;
    private static java.text.SimpleDateFormat sp    = new java.text.SimpleDateFormat("hh:mma");

    private JobReport jobReport = new JobReport ("BillManager");
    
    static ResourceBundle         resources      ; 
    static List sessionLogFilters  = null;
    static{
    	try {
    	    resources 		= ResourceBundle.getBundle("com.eggtheory.brainfuse.bizobjects.billing.Billing");
    	    sessionLogFilters = new ArrayList();
    	    SessionLogFilter quizFilter = new QuizSessionFilter();
    	    sessionLogFilters.add( quizFilter );
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    
    public BillManager()
    {
        calculators = new Hashtable ();
        tutorCalculators = new Vector ();
        studentCalculators = new Vector ();
        MinutesCalculator calculator = null;
        calculator = new PreArrangedTutorMinutesCalculator ();
        calculator.addJobListener( jobReport  );
        tutorCalculators.addElement( calculator );

        calculator = new InstantAccessTutorMinutesCalculator ();
        calculator.addJobListener( jobReport  );
        tutorCalculators.addElement( calculator );

        calculators.put(USER_TYPE_TUTOR, tutorCalculators );

        calculator = new StudentMinutesCalculator ();
        calculator.addJobListener( jobReport  );
        studentCalculators.add( calculator);
        calculators.put(USER_TYPE_STUDENT, studentCalculators );

    }


    public BillManager (long runInterval)
    {
        this();
    	this.runInterval = runInterval;
    }
    
    public void init()
    {
        String runTime      = ServerConfig.getInstance().getServerProperty("com.brainfuse.billManager.firstRunTime");
        if ( runTime != null && runTime.trim().length()!=0)
        {
            try
            {
                Date d = sp.parse(runTime, new ParsePosition(0));
                System.out.println(d);
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
                System.out.println(nextRun);
                firstWait = nextRun.getTime() - currDate.getTime();
                if ( firstWait< 0) firstWait  = Math.abs(firstWait);
            }
            catch (Exception ex)
            {
                firstWait   = 1;
            }

        }

        this.regRunInterval    = ServerConfig.getInstance().getServerIntProperty("com.brainfuse.billManager.runInterval", 60 *1000);
        System.out.println("First wait" + firstWait + " regRun="  + regRunInterval );
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

    private void calculateBill () throws Exception
    {

        int lastRunSessionID =-1;
        int acctWrapUpTime = 0;
        Connection con = null;
        try
        {
            con =Tools.establishConnection( false );

            DbList activeAccounts = queryAllActiveAccounts (con);
            if (activeAccounts.size() > 0)
            {
                for (int i=0; i<activeAccounts.size(); i++)
                {
                    

                    Account account = (Account)activeAccounts.getElement( i ) ;
                    Attribute acctAttr = account.getAttribute(BILLABLE_ATTR);
                    Attribute acctWrapUpTimeAttr = account.getAttribute( ACCT_WRAP_UP_TIME );

                    if ( acctAttr != null && acctAttr.getAttributeValue().equals("1"))
                    {
                        DbList billingContractList = account.getContracts() ;
                        
                        int acctID = account.getAccountID() ;

                        Attribute timeAllowanceAttrib= account.getAttribute( TIME_ALLOWANCE_ATTR );

                        if (timeAllowanceAttrib != null)
                           this.timeAllowance = Utilities.cInt( timeAllowanceAttrib.getAttributeValue(), DEFAULT_TIME_ALLOWANCE ) ;
                        else
                            this.timeAllowance = DEFAULT_TIME_ALLOWANCE ;

                        System.out.println (" ##### Active Account: " + account.getAccountID() + ", BILLABLE " + timeAllowance
                                            + ((timeAllowanceAttrib != null)?timeAllowanceAttrib.getAttributeValue():"timeallowance is null"));
                        
                        this.timeAllowance = this.timeAllowance * 60 * 1000;
                        acctWrapUpTime = DEFAULT_ACCT_WRAP_UP_TIME;
                        if (acctWrapUpTimeAttr != null)
                           acctWrapUpTime = Utilities.cInt( acctWrapUpTimeAttr.getAttributeValue() , DEFAULT_ACCT_WRAP_UP_TIME );
                        
                        processAccountUsers(acctWrapUpTime, con, billingContractList, acctID);
                    }
                }
            }

            // If no error, commit the changes.
            con.commit()   ;
            System.out.println(" ############## COMMITED CHANGES #### ");
        }
        catch(Exception e)
        {
        	jobReport.setStatus( JobReportStatusEnumType.FAILED );
        	e.printStackTrace() ;
            WrappedException.processException(e);
        }
        finally
        {
            try
            {
                con.close();
            }
            catch (Exception ex) {}
        }
        
        
        
    }

    private void processAccountUsers(int acctWrapUpTime, Connection con, DbList billingContractList, int acctID) throws
        WrappedException {
        //System.out.println(" ### Account Wrap up time for Acct: "  + acctID + " is "  + acctWrapUpTime );
        DbList users = queryUsersFromAccount ( acctID , con  );
        
        for (int j=0 ; j<users.size() ; j++) {
            UserData userData = (UserData)users.getElement(j);
            processUserSessionLogs(acctWrapUpTime, billingContractList, userData);
        }
    }
    
    
    private void updateSessionLogs(int acctWrapUpTime, DbList billingContractList, UserData userData, Vector minuteCalculatorList, List sessionLogs, Connection con) throws WrappedException
    {

    	long currentRefPoint = -1;
        long refPoint = -1;

        for (int k = 0; k < minuteCalculatorList.size(); k++) 
        {
            MinutesCalculator minuteCal = getMinutesCalculator(acctWrapUpTime, billingContractList, userData, minuteCalculatorList, k);
            refPoint = minuteCal.processSessionList((DbList)sessionLogs);
            if (refPoint > currentRefPoint) 
            {
                currentRefPoint = refPoint;
            }
        }
        SessionLogService.getInstance().saveSessionLogs((DbList)sessionLogs, con);
        processUserBilling(userData, minuteCalculatorList, currentRefPoint, billingContractList, con);
    }
    
    private void processUserSessionLogs(int acctWrapUpTime, DbList billingContractList
                                        , UserData userData) throws WrappedException
    {

        Connection con = null;
        try {
            con = Tools.establishConnection(false);
            int userType = userData.getType();

            List sessionLogs = null;
            Vector minuteCalculatorList = null;
            if (userType == UserData.UT_STUDENT)
            {
                minuteCalculatorList = (Vector) calculators.get(USER_TYPE_STUDENT);
            	sessionLogs = SessionLogService.getInstance().loadStudentSessionLogsBySessionID(userData.getUserID() , con);
            	for ( int i=0; i<sessionLogFilters.size() ; i++)
            	{
            		SessionLogFilter sessionLogFilter = (SessionLogFilter)sessionLogFilters.get(i);
            		Object[] args = {userData.getAccount()};
            		sessionLogs = sessionLogFilter.filter( sessionLogs, args);
            	}
            }
            else
            {
                minuteCalculatorList = (Vector) calculators.get(USER_TYPE_TUTOR);
            	//sessionLogs = SessionLogService.getInstance().loadStudentSessionLogsBySessionID(userData.getUserID() , con);
            }
             
            if (sessionLogs != null && sessionLogs.size() > 0) {
            	updateSessionLogs (acctWrapUpTime, billingContractList, userData, minuteCalculatorList, sessionLogs, con);
            }
            con.commit();
        }
        catch (Exception ex) {
//            ex.printStackTrace();
            try {
                
                WrappedException.processErrorException("BillManager.processUserSessionLogs():" + ex.getClass().getName() +
                                                       ":" + userData + ":", ex);
            }
            catch (Exception ex2) {
            	ex2.printStackTrace();
            	jobReport.addError( ex2 );
            }
        }
        finally 
        {
        	Tools.closeConnection(con);
        }

    }

    private MinutesCalculator getMinutesCalculator(int acctWrapUpTime, DbList billingContractList, UserData userData
                                                   , Vector cal, int k) {
        MinutesCalculator minuteCal = (MinutesCalculator) cal.elementAt( k );
        minuteCal.setTimeAllowance( timeAllowance );
        minuteCal.setUserData( userData );
        minuteCal.setBillingContractList( billingContractList );
        minuteCal.setAcctWrapUpTime( acctWrapUpTime );
        return minuteCal;
    }

    private DbList queryAllActiveAccounts (Connection existedCon)
    {
        DbList result = null;
        try
        {
            result = AccountService.getInstance() .loadAllActiveAccounts(existedCon) ;
            //System.out.println (" #### Number Of Active Found : "+ result.size() );
        }
        catch (Exception e)
        {
            e.printStackTrace() ;
        }
        return result;
    }

    private DbList queryUsersFromAccount(int accountID, Connection con)
    {
        DbList result = null;
        try
        {
            result = UserService.getInstance() .loadUserDataByAcctID( accountID , con);
        }
        catch (Exception e)
        {
            e.printStackTrace() ;
        }

        return result;
        //Vector tmpVector = new Vector();
        //tmpVector.addElement( new Integer(5));
        //return tmpVector;
    }

    private void processUserBilling(UserData userData, Vector vCal, long refPoint, DbList billingContractList
                                   , Connection con) throws WrappedException
    {

        int calSize = vCal.size();
        if (calSize > 0) 
        {
            boolean emailSent=false;
        	for (int i = 0; i < calSize; i++) 
            {
                MinutesCalculator cal = (MinutesCalculator) vCal.elementAt(i);
                HashMap hBillingList = cal.getBillingList();
                for ( Iterator b=hBillingList.keySet () .iterator(); b.hasNext() ;)
                {
                	Integer contractID = (Integer)b.next() ;
                	ArrayList billingInfoList = (ArrayList)hBillingList.get(contractID);
                	updateUserBilling ( billingInfoList , userData, refPoint, con );

                	/**
	                 * Max hours has to be checked, and warning email has to send out if it exceeded the max hours allowed.
	                 */
                	if (verifyUserMaxHours ( contractID.intValue(), billingInfoList, userData, con))
                	{
                		emailSent = true;
                	}
                }
            }
        	if ( emailSent )
        	{
        		userData.setFlag( userData.getFlag() | UserData.FLAG_MAX_HOUR_EXCEEDED);
    			userData.save( con );        		
        	}
        }
    }

    private boolean verifyUserMaxHours ( int contractID, ArrayList billingInfoList, UserData userData, Connection con) throws WrappedException
    {

    	BillingContract contract = (BillingContract )userData.getAccount().getContracts().getElementByID( contractID );
    	boolean emailSent =false;
    	if ( contract != null)
    	{
            UserBillingList userBillingList = (UserBillingList) BillDataService.getInstance().loadUserBillingByUserContract(userData, contract, con );
	    	userBillingList.initBillEntryHours( true );
        	if ( userBillingList.hasMaxedOut() && !userData.maxHoursExceededEmailSent() )
        	{
        		notifyUsers(userData, userBillingList.getTotalHours(), userBillingList.getBonusHours());
        		emailSent =true;
        	}
    	}
    	return emailSent;
    }
    
    private void updateUserBilling (ArrayList billingList, UserData userData, long refPoint, Connection con) throws WrappedException
    {
        int size = billingList.size();

        for (int j = 0; j < size; j++) 
        {
            BillingInfo billingInfo = (BillingInfo) billingList.get (j);
            if ( billingInfo.minutes > 0)
            {
	            BillEntry newBillEntry = billingInfo.billEntry;
	
	            /**
	             * New UserBilling Entry is going to insert.
	             */
	            newBillEntry.setNewFlag(true);
	            newBillEntry.setCurrentFlag(refPoint == Integer.parseInt(newBillEntry.getRefPoint()));
	            newBillEntry.setUserID(userData.getUserID());
	            newBillEntry.setPaid(false);
	            newBillEntry.setNumberOfMinutes(billingInfo.minutes);
	            BillDataService.getInstance().saveBillEntry(newBillEntry, con);
	
	            int currentBillingID = newBillEntry.getUserBillingID();
	            
	            // INSERT A NEW RECORD INTO BILLING_LOG TABLE
	            BillLogEntry billLog = BillLogEntry.getInstance();
	            billLog.setNewFlag(true);
	            billLog.setUserBillingID(currentBillingID);
	            billLog.setUserID(userData.getUserID());
	            billLog.setLogDate(new Date());
	            billLog.setLogType(1);
	            billLog.setNumOfPoints(billingInfo.minutes);
	            billLog.setComments(billingInfo.billComment.toXMLString());
	            BillDataService.getInstance().saveBillLog(billLog, con);
            }
        }
    }
    	
    /*
     * To Student, Parent and Admin
     * You are approaching your maximum hours. To date, you have received x hours of tutoring and your district pays for y hours. 
     * We hope you have enjoyed our services. Please make sure to finish your last few hours and take your post-test. 
     * We hope to see you again next year!
     * 
     * To Admin:
     * Student x is approaching his maximum hours. 
     */
    private void notifyUsers(UserData userData, double totalHours,
			double bonusHours) {
		String studentEmail = userData.getEmail1();
		String parentEmail = userData.getEmail2();

		String adminEmail = ServerConfig.getInstance().getServerProperty(
				"com.brainfuse.properties.billing.admin.email.address",
				ServerConfig.getInstance().getServerProperty(
						"com.brainfuse.email.schedule"));

		Object[] msgParams = new Object[] { userData.getUserName() };
		String adminMsgTitle = getString(resources,
				"com.brainfuse.properties.billing.admin.email.subject",
				msgParams);

		msgParams = new Object[] { userData.getUserName(),
				new Double(totalHours), new Double(bonusHours) };
		String adminMsgBody = getString(resources,
				"com.brainfuse.properties.billing.admin.email.body", msgParams);

		Emailer.getInstance().sendMessage(
				ServerConfig.getInstance().getServerProperty(
						"com.brainfuse.email.do-not-reply.notifications"),
				adminEmail, adminMsgTitle, adminMsgBody, "", "", false);

		String msgTitle = getString(resources,
				"com.brainfuse.properties.billing.student.email.subject");
		msgParams = new Object[] { new Double(totalHours + bonusHours),
				new Double(totalHours) };
		String studentMsgBody = getString(resources,
				"com.brainfuse.properties.billing.student.email.body",
				msgParams);
		if (studentEmail != null) {
			Emailer.getInstance().sendMessage(
					ServerConfig.getInstance().getServerProperty(
							"com.brainfuse.email.do-not-reply.notifications"),
					studentEmail, msgTitle, studentMsgBody, "", "", false);
		}

		if (parentEmail != null) {
			Emailer.getInstance().sendMessage(
					ServerConfig.getInstance().getServerProperty(
							"com.brainfuse.email.do-not-reply.notifications"),
					parentEmail, msgTitle, studentMsgBody, "", "", false);
		}

	}
    
    /*
    private int getTimeAllowance (int acctID, Connection con) throws com.actbig.utils.WrappedException
    {
        DbList acctAttr = AccountAttribDefDataService.getInstance() .loadAccountAttribDef(acctID, con) ;


        int result =-1;
        for (int i=0; i < acctAttr.size() ; i++)
        {
            AccountAttribDef acctAttrDef = (AccountAttribDef)acctAttr.getElement( i );
            if (acctAttrDef.getName().equals(TIME_ALLOWANCE_ATTR) )
            {
                DbList optionList = AcctAttrValueOptionDataService.getInstance() .loadAcctAttrValueOptions(acctAttrDef.getAccountAttribID() , con) ;
                if (optionList.size() > 0)
                    result = Integer.parseInt(   ((AccountAttrValueOption)optionList.getElement(0) ).getValue() );
                break;
            }

        }

        return result;

    }
    */


    class MinutePerDay
    {
        public Date date;
        public int minutes;
        public BillComment billComment;
        public String refPoint;
    }

	public void execute(JobExecutionContext jobContext) throws JobExecutionException
	{
		try
        {
			final String jobListenerName ="BillingJobListener"; 
			Scheduler scheduler = jobContext.getScheduler(); 
			if ( scheduler.getJobListener( jobListenerName) == null)
			{
				scheduler.addJobListener( new BillingJobListener (jobListenerName) );
			}
			jobContext.getJobDetail() .addJobListener(jobListenerName );
			jobReport.setStatus( JobReportStatusEnumType.SUCCEED );
			calculateBill ();
			jobContext.setResult( jobReport );
        }
        catch (Exception e )
        {
            jobReport.setStatus(JobReportStatusEnumType.FAILED  );
            jobContext.setResult(jobReport);
        	e.printStackTrace() ;
        }
	}
	
	public static void main (String[] argv )
	{
		try
		{
			BillManager manager = new BillManager();
			manager.calculateBill();
		}
		catch (Exception e)
		{
			e.printStackTrace() ;
		}
	}

}
