package com.eggtheory.brainfuse.server.servercomponent;

import java.sql.Connection;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.ResourceBundle;
import java.util.TimeZone;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import com.actbig.datahandler.Tools;
import com.actbig.datahandler.dbholders.DbList;
import com.actbig.datahandler.dbholders.IDbHolder;
import com.actbig.mail.Emailer;
import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.Utilities;
import com.eggtheory.brainfuse.bizobjects.account.Account;
import com.eggtheory.brainfuse.bizobjects.account.Attribute;
import com.eggtheory.brainfuse.bizobjects.account.Attributes;
import com.eggtheory.brainfuse.bizobjects.account.PreferredSched;
import com.eggtheory.brainfuse.bizobjects.account.UserData;
import com.eggtheory.brainfuse.bizobjects.account.UserService;
import com.eggtheory.brainfuse.bizobjects.calendar.AppointmentAttribute;
import com.eggtheory.brainfuse.bizobjects.calendar.AppointmentAttributes;
import com.eggtheory.brainfuse.bizobjects.calendar.AttendanceEntry;
import com.eggtheory.brainfuse.bizobjects.calendar.ScheduleConflictException;
import com.eggtheory.brainfuse.bizobjects.calendar.Session;
import com.eggtheory.brainfuse.bizobjects.calendar.SessionDataService;
import com.eggtheory.brainfuse.bizobjects.calendar.TutorRepComparator;
import com.eggtheory.brainfuse.bizobjects.common.BizObjectsContext;
import com.eggtheory.brainfuse.bizobjects.user.CallLog;
import com.eggtheory.brainfuse.bizobjects.user.CallLogDataService;
import com.eggtheory.brainfuse.bizobjects.user.TutorData;
import com.eggtheory.brainfuse.bizobjects.user.UserAttributeList;
import com.eggtheory.brainfuse.bizobjects.user.UserManager;
import com.eggtheory.brainfuse.inbox.Inbox;
import com.eggtheory.brainfuse.inbox.NotificationManager;
import com.eggtheory.brainfuse.quizzers.QuestionFeedBackCategory.FeedBackSkill;
import com.eggtheory.brainfuse.quizzers.QuizFeedBack;
import com.eggtheory.brainfuse.quizzers.QuizFeedBackService;
import com.eggtheory.brainfuse.utils.DateUtils;
import com.eggtheory.brainfuse.utils.StartDateEndDate;

/**
 * Title:
 * Description:
 * Copyright:    Copyright (c) 2001
 * Company:
 * @author
 * @version 1.0
 */

public class SessionMonitorJob implements Job
{
    public static final int SESSION_MONITOR_CHECK_MINUTE = 8; //check sessions 8 minutes in advance;

    public List iaSessList                     = null;
    private static final int SESSION_MONITOR_LOWER_LIMIT = 6;
    
 
    public SessionMonitorJob()
    {
    }
    StartDateEndDate getStartDateAndEndDate(Date now)
    {
        Calendar cal    = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.MINUTE, 5);
        Date startDate                  = cal.getTime();

        //startDateCal.set(Calendar.HOUR, startDateCal.get(Calendar.HOUR)+3);
        cal.add(Calendar.MINUTE, 5);
        Date endDate                    = cal.getTime();
        StartDateEndDate d              = new StartDateEndDate(startDate, endDate);
        return d;
    }

    private List<Session> getSessionsStartedMinutesAgo(int minutes) throws WrappedException
    {
        Calendar cal    = Calendar.getInstance();
        
        cal.setTime(new Date());        
        cal.add(Calendar.MINUTE, -(minutes) );
        Date startDate  = cal.getTime();
        
        cal.setTime(new Date());
        Date endDate    = cal.getTime();
        
       return SessionDataService.getInstance().getIASessionByDateRange(startDate,endDate);      
        
    }
    
    public void execute(JobExecutionContext context) throws org.quartz.JobExecutionException
    {
        try {
        	
//            doAutomatedSessionMonitor(); 
            
            handleMissedSessions();
            
            handleFinishedQuizFeedbackItems();          
            
            iaSessList = null;

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

    }
    public void handleFinishedQuizFeedbackItems() throws WrappedException {
	    
	    
	    Connection con = null;

	        try
	        {
	            con = Tools.establishConnection(false);

	            DbList<QuizFeedBack> finishedQuizFeedbackItems = QuizFeedBackService.getInstance().loadFinishedQuizFeedbackItems(con);
	            
	 	   for(QuizFeedBack quizFeedbackItem:finishedQuizFeedbackItems){	 	       
	 	       quizFeedbackItem.done(con);
	 	       quizFeedbackItem.save(con);
	 	   }	            
	            
	            con.commit();

	        }
	        catch (Exception e) {
	            try {
	        	con.rollback();
	            }
	            catch (Exception ex) {}
	            e.printStackTrace();
	            WrappedException.processException(e);
	        }
	        finally
	        {
	            try {
	                con.close();
	            }catch (Exception e) {}
	        }
       }

          
	
    
	private void doAutomatedSessionMonitor() throws WrappedException,
			ScheduleConflictException {
		StartDateEndDate d = getStartDateAndEndDate(new Date());
		//System.out.println("StartDate=" + startDate + " EndDate=" + endDate);
		List sessionList = SessionDataService.getInstance().getSessionByDateRange(d.getStartDate(), d.getEndDate());

		Iterator it = sessionList.iterator();
		//int timeDiff = 0;
		while (it.hasNext())
		{
		    Session session = (Session) it.next();
		    Date now        = new Date();
		    long timeDiff   = session.getStartDate().getTime() - now.getTime();
		    if (timeDiff <= SESSION_MONITOR_CHECK_MINUTE * 60 * 1000 && timeDiff >= (SESSION_MONITOR_LOWER_LIMIT * 60 * 1000 + 30 * 1000))
		    {
		        startSessionMonitor(session);
		    }
		   
		}
	}

    
	public void handleMissedSessions() throws WrappedException {
		
		List<Session> sessions =  getSessionsStartedMinutesAgo(60);		 
		innerHandelMissedSessions(sessions);
	}
	
	public void handleMissedSessions(List<Session> sessions)  throws WrappedException{
			
		innerHandelMissedSessions(sessions);
		
	}
	
    /**
     *      
	 *  Detects sessions of the last 60 minutes that have finished without a tutor present.
	 *  If he hasn't already, the tutor is notified of his missed tutoring-session.
	 *                
     */

	private void innerHandelMissedSessions(List<Session> sessionList) throws WrappedException {
		
		Iterator<Session> sessionIterator = sessionList.iterator();
		
		while (sessionIterator.hasNext())
		{ 
			 Session session =  sessionIterator.next();
			
			 boolean isSessionFinished = session.getEndDate().before(new Date());
			 boolean isNotifyable = false;
			 boolean wasTutorAbsent = false; 
			 boolean notificationWasSent = false;            	 
			 
			 if(isSessionFinished){
				 
				 Attribute attribute = session.getModerator().getAccount().getAttribute("MISSED_SESSION_NOTIFICATION");
				 isNotifyable = Integer.valueOf(attribute!=null?attribute.getAttributeValue():"-1")==1;
            	  
				 if(isNotifyable){
					  wasTutorAbsent = session.getUserAttendence(session.getModerator()) == Session.ABSENT;
					  if(wasTutorAbsent){
						  AppointmentAttribute appointmentAttribute = session.getAttributes().getAttribute("MISSED_SESSION_NOTIFICATION_SENT");
						  notificationWasSent = Integer.valueOf(appointmentAttribute!=null?appointmentAttribute.getValue():"-1")==1;		  
					  }
				  }
			 } 
			  
			 if(isSessionFinished && wasTutorAbsent && isNotifyable && !notificationWasSent){
				
				 notifyTutorOfMissedSession(session);
			 }
			 
		}
	}
	
    public void startSessionMonitor ( Session sessionToCheck ) throws WrappedException, ScheduleConflictException
    {
        sessionToCheck.resetAttendance();
        UserData tutor      = sessionToCheck.getModerator();
        //List iaSessList     = null;
        //List filteredList   = null;
      
        if ( sessionToCheck.getUserAttendence( tutor ) == Session.NOT_PRESENT_YET
                && !tutor.isUserInSession()
                && tutor.getAccount().getType().equals(Account.AccountType.TUTORS_BRAINFUSE)
                && sessionToCheck.isSessionValid() )
        {
            loadIASessionList ();           
            //filteredList    =  filterSessionList ( iaSessList, sessionToCheck );
            //moveSession ( filteredList, sessionToCheck );
            moveSession ( iaSessList, sessionToCheck );
        }
                
    }

    public void loadIASessionList () throws WrappedException
    {
        if ( iaSessList == null )
        {
            Calendar cal    = Calendar.getInstance();
            cal.add(Calendar.HOUR, -2 ) ;
            Date startDate  = cal.getTime();
            cal.add(Calendar.HOUR, 2 );
            Date endDate    = cal.getTime();
            iaSessList      = SessionDataService.getInstance().getIASessionsByDateRange(startDate, endDate);
            iaSessList      = new ArrayList(iaSessList);
        }
    }

    /**
     *  filter list of sessions based on certain criteria
     */
    public List filterSessionList ( List iaSessList, Session sessionToCheck ) throws WrappedException, ScheduleConflictException
    {
        List filteredList   = new ArrayList();

        String subjToCheck  = sessionToCheck.getDescription();
        PreferredSched prefSched    = sessionToCheck.getPreferredSchedule();
        int langIDToCheck   = 900;
        if ( prefSched != null )
            langIDToCheck = prefSched.getLanguage();

        for(Iterator i = iaSessList.iterator();i.hasNext();)
        {
            Session iaSession   = (Session) i.next();
            iaSession.resetAttendance();
            TutorData iaTutor   = null;
            
            try {
            	iaTutor = (TutorData)iaSession.getModerator();
            } catch (ClassCastException castException ) {
            	/**
            	 * Skip to next if the moderator is not tutor data class
            	 */
            }

            /*System.out.println( iaTutor.getUserName() +
                                " canTeach = " + iaTutor.canTeach(subjToCheck, langIDToCheck)
                                + " numOfStud = " + iaSession.getNumberOfPresentParticipants()
                                + " attd = " + iaSession.getUserAttendence( iaTutor )
                                + " ia time = " + iaSession.getStartDate() + " -- " + iaSession.getEndDate()
                                + " pa time = " + sessionToCheck.getStartDate() + " -- " + sessionToCheck.getEndDate()
                                );  */

            if ( iaTutor != null && iaTutor.canTeach(subjToCheck, langIDToCheck)
                    && iaSession.getNumberOfPresentParticipants() <= 0
                    && iaSession.getUserAttendence( iaTutor ) == Session.PRESENT
                    && ( iaSession.getStartDate().getTime() <= sessionToCheck.getStartDate().getTime() && iaSession.getEndDate().getTime() >= sessionToCheck.getEndDate().getTime() )
            )
            {
                try
                {
                    SessionDataService.getInstance().checkForConflict( iaSession, iaTutor );
                    filteredList.add( iaSession );
                }
                catch (ScheduleConflictException ex)
                {
                }
            }
        }
        TutorRepComparator comp     = new TutorRepComparator( sessionToCheck, sessionToCheck.getFirstParticipant() );
        Collections.sort( filteredList, comp );
        System.out.println ("           filteredList size :: " + filteredList.size() );
        return filteredList;
    }

    public void moveSession ( List iaSessList, Session sessionToMove ) throws WrappedException, ScheduleConflictException
    {
        boolean matched         = false;
        UserData newModerator   = UserData.getInstance();
        UserData oldModerator   = sessionToMove.getModerator();
        Connection con          = null;

        for ( int j=0; j<3 && !matched; j++ )
        {
            try
            {
                con = Tools.establishConnection(false);

                sessionToMove.resetAttendance();
                SessionDataService.getInstance().loadSession(sessionToMove, con);
                oldModerator   = sessionToMove.getModerator();
                //System.out.println("    oldModerator: " + oldModerator.getUserName() );
                if (sessionToMove.getActive() != 0 && sessionToMove.getUserAttendence(oldModerator) == Session.NOT_PRESENT_YET)
                {
                    List filteredList = filterSessionList(iaSessList, sessionToMove);
                    if (filteredList.size() > 0)
                    {
                        Session iaSession = (Session) filteredList.get(0);
                        UserData iaTutor  = iaSession.getModerator();
                        //System.out.println("            iaTutor: " + iaTutor.getUserName() );

                        sessionToMove.setTitle( iaTutor.getUserName() );
                        sessionToMove.setModifiedBy(String.valueOf(UserData.TIME_SHEET_SUBMITTER));
                        sessionToMove.setDateModified(new Date());
                        sessionToMove.setModerator( iaTutor );
                        SessionDataService.getInstance().saveSession(sessionToMove, con);
                        con.commit();
                        matched         = true;
                        newModerator    = iaTutor;

                        try
                        {
                            NotificationManager notificationMgr = NotificationManager.getInstance();
                            notificationMgr.sendAdminMessage("RefreshSessionList " + newModerator.getUserName()) ;
                            notificationMgr.sendNotification(newModerator.getUserName(),
                                                             "You are assigned a new session",
                                                             "To check your session list, please select the Refresh option from the Sessions menu on your QuickConenct");
                        }
                        catch(Exception e ) {}
                        
                       // notifyUserOfMissedIASession(sessionToMove);
                        
                    }
                    else
                        break;
                }
                else
                    break;
            }
            catch (ScheduleConflictException e)
            {}
            catch (Exception ex)
            {
                System.out.println("SessionReminder.moveSession : Exception -- " + ex.getClass().getName() + ": " + ex.getMessage() );
            }
            finally
            {
                try { con.close(); }catch(Exception e){}
            }

        }
        if ( matched )      // record into CallLog
        {
            CallLog call    = CallLog.getInstance();
            call.setSessionID( sessionToMove.getSessionID() );
            call.setTypeID( CallLog.CALL_LOG_TYPE_TUTOR_REMOVED );
            call.setUserID( oldModerator.getUserID() );
            call.setNewFlag(true);
            CallLogDataService.getInstance().saveCallLog(call);

            call.setTypeID( CallLog.CALL_LOG_TYPE_TUTOR_ADDED );
            call.setUserID( newModerator.getUserID() );
            call.setNewFlag(true);
            CallLogDataService.getInstance().saveCallLog(call);

        }

    }

    /**
     *      
	 *  If a tutor missed a session an email notification is sent to him. 
     *  Subject and body of the email are retrieved from a Properties file.   
     *  Additionally, an attribute is associated with the session that was missed  
     *  noting that an e-mail notification has been sent.             
     */
    
    private void notifyTutorOfMissedSession(Session session) {
    	
    	try {
    		
    		ResourceBundle messages = ResourceBundle.getBundle("com.eggtheory.brainfuse.server.servercomponent.SessionMonitorJobResourceBundle");
    		    	   
    	    String subject          = messages.getString("message.subject");    		
    	    String body             = messages.getString("message.body");
    	    UserData tutor          = session.getModerator();
    	   
    	    //MessageFormat formatter = new MessageFormat("");
    	    //formatter.applyPattern(body);    	   
    	    //String dateString = DateUtils.dateFormat(session.getStartDate(),  "MM/dd/yyyy", tutor.getTimeZoneOffset());
    	    //String startTime  = DateUtils.dateFormat(session.getStartDate(),  "hh:mm a", tutor.getTimeZoneOffset());
    	    //String endTime    = DateUtils.dateFormat(session.getEndDate(),  "hh:mm a", tutor.getTimeZoneOffset());
    	  	   
		    //String substitutedBody = formatter.format(new Object[]{  tutor.getUserName(), dateString, startTime, endTime});
    		//UserService.getInstance().loadUserDataByUsername("System");	   
		         
		    UserData admin = UserService.getInstance().loadUserRegData(5);
		    
		    tutor.getInbox().sendMessage (admin.getUserName(), tutor.getUserName(), subject,body);		    
		    Emailer.getInstance().sendMessage(admin.getUserName(), tutor.getEmail1(), subject,body);
		    
		    AppointmentAttribute appointmentAttribute = session.getAttributes().getAttribute("MISSED_SESSION_NOTIFICATION_SENT");
		    
		    if(appointmentAttribute!=null){		    
		    	appointmentAttribute.setValue("1");
		    
		    }else{
		    	
		    	appointmentAttribute = new AppointmentAttribute();
		    	HashMap<String, Object> hsValues = new HashMap<String, Object>();		    		    			    	
		    	hsValues.put("EVENT_ID", session.getID());
		    	hsValues.put("NAME", "MISSED_SESSION_NOTIFICATION_SENT");
		    	hsValues.put("VALUE", "1");
		    	appointmentAttribute.fillValues(hsValues);
		    	appointmentAttribute.setNewFlag(true);
				session.getAttributes().addElement(appointmentAttribute);
		    	
		    }
				
		    session.getAttributes().save();
		    
		} catch (WrappedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
    
	/**
     *  this method calculates the init time for session monitor to run,
     *  supposedly monitor runs 8 minutes before session start time
     */
    public Date calculateInitSessionMonitorTime ( Date now )
    {
        //Date now = new Date();
        //System.out.println("now: " + now);

        Calendar nowCal = Calendar.getInstance();
        nowCal.setTime(now);
        nowCal.set(Calendar.SECOND, 0);
        nowCal.set(Calendar.MILLISECOND, 0);
        //System.out.println("clean 0: " + nowCal.getTime());

        nowCal.add(Calendar.MINUTE, SESSION_MONITOR_CHECK_MINUTE);
        //System.out.println("+8: " + nowCal.getTime());

        int newMinute = nowCal.get(Calendar.MINUTE);
        for ( int i=1; i<6; i++ )
        {
            if ( (newMinute + i) % 5 == 0 )
            {
                newMinute += i;
                break;
            }
        }
        nowCal.set(Calendar.MINUTE, newMinute);
        //System.out.println("session time: " + nowCal.getTime());
        nowCal.add(Calendar.MINUTE, -SESSION_MONITOR_CHECK_MINUTE);
        //System.out.println("run time: " + nowCal.getTime());
        return nowCal.getTime();
    }

    public int calculateThreadWaitTime ()
    {
        long firstWait              = 0l;
        Date nowDate                = new Date();
        Date sessionMonitorInitTime = calculateInitSessionMonitorTime(nowDate);
        firstWait                   = sessionMonitorInitTime.getTime() - nowDate.getTime();
        return (new Long(firstWait)).intValue();

    }
	
}
