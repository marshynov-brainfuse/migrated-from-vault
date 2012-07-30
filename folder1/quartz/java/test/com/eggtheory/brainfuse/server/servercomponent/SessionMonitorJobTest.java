package com.eggtheory.brainfuse.server.servercomponent;

import static org.junit.Assert.assertEquals;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Test;

import com.actbig.datahandler.XMLResultSetFactory;
import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.bizobjects.account.AccountManager;
import com.eggtheory.brainfuse.bizobjects.calendar.AppointmentAttribute;
import com.eggtheory.brainfuse.bizobjects.calendar.Session;
import com.eggtheory.brainfuse.bizobjects.calendar.SessionDataService;
import com.mockrunner.jdbc.PreparedStatementResultSetHandler;

public class SessionMonitorJobTest extends com.actbig.datahandler.BasicJDBCTestCaseAdapter {

	static final int USER_ID = 809;// trainme2
	static final int SESSION_ID = 303089;
	static final String MOCK_DATA = "java/test/com/eggtheory/brainfuse/server/servercomponent/SessionMonitorJobTest.xml";
	
	@Test
	public void testHandleFinishedQuizFeedbackItems() throws Exception {

	        PreparedStatementResultSetHandler handler = getPreparedStatementResultSetHandler();		
		XMLResultSetFactory mockFactory = new XMLResultSetFactory(MOCK_DATA, "testHandleFinishedQuizFeedbackItems", handler);

		SessionMonitorJob job = new SessionMonitorJob();

		
		job.handleFinishedQuizFeedbackItems();

		//e mail was sent already 
		
	}
	

	
	
	@Test
	public void testMissedSessionNotificationSent() throws Exception {
			
		List<Session> sessions = getSessionsByDateRange("testMissedSessionNotificationSent", "2012-04-05T11:00:00.000", "2012-04-05T11:15:00.000");

		SessionMonitorJob job = new SessionMonitorJob();

	    assertValuesAreEqual(sessions, "MISSED_SESSION_NOTIFICATION_SENT", "-1");

		job.handleMissedSessions(sessions);

		assertValuesAreEqual(sessions, "MISSED_SESSION_NOTIFICATION_SENT", "1");
		
	}

	@Test
	public void testCreateMissedSessionNotificationSentAttribute() throws Exception {
			
		List<Session> sessions = getSessionsByDateRange("testCreateMissedSessionNotificationSentAttribute", "2012-04-05T11:00:00.000", "2012-04-05T11:15:00.000");

		SessionMonitorJob job = new SessionMonitorJob();

	    
	    for (Session session : sessions) {
			
			assertEquals(session.getAttributes().getAttribute("MISSED_SESSION_NOTIFICATION_SENT"), null);

		}

	    
		job.handleMissedSessions(sessions);

		 for (Session session : sessions) {
				
				assertEquals(session.getAttributes().getAttribute("MISSED_SESSION_NOTIFICATION_SENT").getValue(), "1");

			}
		
	}
	
	@Test
	public void testWantsToBeNotified() throws Exception {
				
		List<Session> sessions = getSessionsByDateRange("testWantsToBeNotified", "2012-04-05T11:00:00.000", "2012-04-05T11:15:00.000");

		SessionMonitorJob job = new SessionMonitorJob();

		 assertValuesAreEqual(sessions, "MISSED_SESSION_NOTIFICATION_SENT", "-1");
		
		job.handleMissedSessions(sessions);

		// is notifyable --> e-mail must have been sent
		assertValuesAreEqual(sessions, "MISSED_SESSION_NOTIFICATION_SENT", "1");
		
	}

	@Test
	public void testDoesNotWantToBeNotified() throws Exception {

		List<Session> sessions = getSessionsByDateRange("testDoesNotWantToBeNotified", "2012-04-05T11:00:00.000", "2012-04-05T11:15:00.000");

		SessionMonitorJob job = new SessionMonitorJob();

		assertValuesAreEqual(sessions, "MISSED_SESSION_NOTIFICATION_SENT", "-1");
		
		job.handleMissedSessions(sessions);

		// is not notifyable --> e-mail must not have been sent
		assertValuesAreEqual(sessions, "MISSED_SESSION_NOTIFICATION_SENT", "-1");
	}

	
	@Test
	public void testEmailWasAlreadySent() throws Exception {

		List<Session> sessions = getSessionsByDateRange("testEmailWasAlreadySent", "2012-04-05T11:00:00.000", "2012-04-05T11:15:00.000");

		SessionMonitorJob job = new SessionMonitorJob();

		
		job.handleMissedSessions(sessions);

		//e mail was sent already 
		assertValuesAreEqual(sessions, "MISSED_SESSION_NOTIFICATION_SENT", "1");
	}
	
	
	

	
	/**
	 * @throws ParseException
	 * @throws WrappedException
	 */
	private List<Session> getSessionsByDateRange(String resultSetIdString, String startDateString, String endDateString) throws ParseException, WrappedException {

		PreparedStatementResultSetHandler handler = getPreparedStatementResultSetHandler();		
		XMLResultSetFactory mockFactory = new XMLResultSetFactory(MOCK_DATA, resultSetIdString, handler);
		
		SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH);
		Date startDate = dateFormatter.parse(startDateString);
		Date endDate = dateFormatter.parse(endDateString);

		return SessionDataService.getInstance().getSessionByDateRange(startDate, endDate);
	}
	@After
	public void reset(){
		AccountManager.getInstance().invalidate(108);
	}
	

	/**
	 * @throws Exception
	 */
	private void assertValuesAreEqual(List<Session> sessions, String attributeName, String expectedAttributeValue) throws Exception {

		for (Session session : sessions) {

			AppointmentAttribute actualtAttribute = session.getAttributes().getAttribute(attributeName);

			assertEquals(expectedAttributeValue, actualtAttribute.getValue());

		}
	}

}
