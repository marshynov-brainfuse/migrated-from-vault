package com.eggtheory.brainfuse.server.servercomponent;

import java.sql.Connection;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.StringTokenizer;


import org.quartz.JobExecutionContext;
import org.quartz.StatefulJob;

import com.actbig.datahandler.dbholders.DbList;
import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.Utilities;
import com.eggtheory.brainfuse.bizobjects.account.UserData;
import com.eggtheory.brainfuse.bizobjects.account.UserService;
import com.eggtheory.brainfuse.bizobjects.user.PreferredSchedMatchingInfo;
import com.eggtheory.brainfuse.bizobjects.user.PreferredSchedMatchingInfoService;
import com.eggtheory.brainfuse.bizobjects.user.schedule.MatchEvent;
import com.eggtheory.brainfuse.bizobjects.user.schedule.PreferredScheduleSubjectGroup;
import com.eggtheory.brainfuse.bizobjects.user.schedule.ScheduleMatchListenerAdapter;
import com.eggtheory.brainfuse.bizobjects.user.schedule.ScheduleMatcher;
import com.eggtheory.brainfuse.bizobjects.user.schedule.ScheduleVerifierListener;
import com.eggtheory.brainfuse.inbox.Inbox;
import com.eggtheory.brainfuse.log.Logger;
import com.eggtheory.brainfuse.utils.DateUtils;

/**
 * Title: Description: Copyright: Copyright (c) 2001 Company:
 *
 * @author
 * @version 1.0
 */

public class SchedMatchJob implements StatefulJob{
	public static UserData sysAdmin;
	private static Logger log = new Logger("com.eggtheory.brainfuse.bizobjects.user.schedule.schedule");
	
	static {
		Connection tmpCon = null;
		try {
			
			sysAdmin = UserData.getInstance();
			sysAdmin.setUserID(UserData.TIME_SHEET_SUBMITTER);
			UserService.getInstance().loadUserData(sysAdmin, tmpCon);
		} catch (Exception ex) {
		} finally {
			try {
				tmpCon.close();
			} catch (Exception ex) {
			}
		}
		
	}
	public SchedMatchJob() {
	}
	class JobScheduleListener extends ScheduleMatchListenerAdapter{
		PreferredSchedMatchingInfo psmi;
		UserData admin;
		/**
		 * @param psmi
		 */
		public JobScheduleListener(PreferredSchedMatchingInfo psmi, UserData admin) {
			this.psmi = psmi;
			this.admin	= admin;
		}

		@Override
		public void failed(MatchEvent evt) {
			StringBuffer sb	= new StringBuffer();
			sb.append("Job failed while matching one or more of the following users: ");
			try {
				Inbox.sendTask(sysAdmin, admin, "Schedule Matching Failed", sb.toString(), DateUtils.addDate(
								Calendar.DAY_OF_MONTH, new Date(), 1), evt.getUser().getUserID());
			} catch (WrappedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			resetMatchingInfo(evt);
		}
		
		private void resetMatchingInfo(MatchEvent evt){
			psmi.setStatus(PreferredSchedMatchingInfo.STATUS_DONE);
			try {
				PreferredSchedMatchingInfoService.getInstance().savePreferredSchedMatchingInfo(psmi);
			} catch (WrappedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		@Override
		public void partiallyCompleted(MatchEvent evt) {
			StringBuffer sb = new StringBuffer();
			sb.append("<br>The following students are not fully matched:<br>");
			UserData ud = evt.getUser();
			sb.append(getUsernameLink(ud)).append("<br>");

			try {
					Inbox.sendTask(sysAdmin, admin, "Students not fully matched", 
									sb.toString(), DateUtils.addDate(
									Calendar.DAY_OF_MONTH, new Date(), 1), 
									ud.getUserID());
			} catch (WrappedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			resetMatchingInfo(evt);
		}

		@Override
		public void started(MatchEvent evt) {
			log.info("Starting matching " + evt.getUser());
		}

		@Override
		public void alreadyMatched(MatchEvent evt) {
			resetMatchingInfo(evt);
		}

		@Override
		public void completed(MatchEvent evt) {
			resetMatchingInfo(evt);
		}
		
	}
	public void execute(JobExecutionContext context) throws org.quartz.JobExecutionException
    {
		UserData admin = null;
		
		DbList userList = DbList.getInstance();
		StringBuffer sb	= new StringBuffer();
		try
        {
			System.out.println("  SchedMatchJob.execute() START ");

            DbList pendingList = PreferredSchedMatchingInfoService.getInstance().getPreferredSchedMatchingInfosByStatus(PreferredSchedMatchingInfo.STATUS_PENDING);
            for (Iterator iter = pendingList.iterator(); iter.hasNext(); )
            {
            	
                PreferredSchedMatchingInfo psmi = (PreferredSchedMatchingInfo)iter.next();
                try {
                	sb				= new StringBuffer();
                	
					String tutorIDs = psmi.getTutorIds();
					String userIDs = String.valueOf(psmi.getOwnerID());
					Date startDate = psmi.getStartDate();
					int weeksRange = psmi.getNumOfWeeks();
					int minPerSession = psmi.getMinutePerSession();
					boolean bPsCheck = psmi.getCheckTutorPrefSched();
					boolean bEmailToTutor = psmi.getNotifyTutor();
					int adminID = psmi.getAdminID();

					admin = UserData.getInstance();
					admin.setUserID(adminID);
					UserService.getInstance().loadUserData(admin, null);

					// still go through tokenizer for now, since later on will add handling of categories
					StringTokenizer st = new StringTokenizer(userIDs, ",");
					int tokenCount = st.countTokens();
					Integer[] uids = new Integer[tokenCount];

					for (int i = 0; i < tokenCount; i++) {
						int uid = Integer.parseInt(((String) st.nextElement()).trim());
						uids[i] = new Integer(uid);
					}

					DbList tutorList = (!Utilities.isEmpty(tutorIDs))? UserService.getInstance().loadTutors(tutorIDs): null;
					ScheduleMatcher sm = new ScheduleMatcher(startDate, weeksRange, minPerSession);
					sm.setTutors(tutorList);
					sm.setPsCheck(bPsCheck);
					sm.setEmailToTutor(bEmailToTutor);
					sm.addListener(new ScheduleVerifierListener());
					sm.addListener(new JobScheduleListener(psmi, admin));
					userList = UserService.getInstance().loadUsers(uids);
					sm.match(userList);

					

					
				} catch (Exception e) {
					e.printStackTrace();
				}

			}

			System.out.println("  SchedMatchJob.execute() is DONE ");

			// context.getScheduler().deleteJob("schedMatchJob",
			// "schedMatchGroup");

		} catch (Exception e) {
			e.printStackTrace();
			

		}
	}
	public String getUsernameLink(UserData element){
		return new StringBuffer().append("<a href='openWin_WhoIs(\"")
					.append(element.getUserName()).append("\",leftNavWebRoot)'>")
					.append(element.getUserName()).append("</a><br>").toString();
	}



}
