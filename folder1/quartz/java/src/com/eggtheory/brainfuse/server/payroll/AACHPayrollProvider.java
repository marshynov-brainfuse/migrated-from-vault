package com.eggtheory.brainfuse.server.payroll;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.actbig.datahandler.Tools;
import com.actbig.security.CryptoMan;
import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.bizobjects.account.UserService;
import com.eggtheory.brainfuse.bizobjects.billing.JobReport;
import com.eggtheory.brainfuse.bizobjects.billing.JobReportStatusEnumType;
import com.eggtheory.brainfuse.bizobjects.user.PayrollActivity;
import com.eggtheory.brainfuse.bizobjects.user.PayrollTransaction;
import com.eggtheory.brainfuse.bizobjects.user.TutorData;
import com.eggtheory.brainfuse.bizobjects.user.TutorPayroll;
import com.eggtheory.brainfuse.server.ServerConfig;
import com.eggtheory.brainfuse.utils.XMLUtils;
import com.eggtheory.brainfuse.utils.cipher.HttpsMessage;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class AACHPayrollProvider implements PayrollProvider {
	
	static Logger log = null;

	static {
		log = Logger.getLogger("com.eggtheory.brainfuse.user.Payroll");
	}
	
	static int timeSheetSubmitter = com.eggtheory.brainfuse.bizobjects.account.UserData.TIME_SHEET_SUBMITTER;
	
	public class RequestFailedException extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public RequestFailedException() {
			super();
			// TODO Auto-generated constructor stub
		}

		public RequestFailedException(String message) {
			super(message);
			// TODO Auto-generated constructor stub
		}

		public RequestFailedException(Throwable cause) {
			super(cause);
			// TODO Auto-generated constructor stub
		}

		public RequestFailedException(String message, Throwable cause) {
			super(message, cause);
			// TODO Auto-generated constructor stub
		}

	}

	private JobReport report;

	public class AACHTransaction {

		private String transactionID;

		private String status;
		
		private float amount;

		public AACHTransaction(Element result) {
			transactionID = getChildElementValue(result, "TransactionID");
			status = getChildElementValue(result, "Status");
			if (status == null)
				status = getChildElementValue(result, "TransactionStatus");
			try {
				amount = Float.parseFloat(getChildElementValue(result, "Amount"));
			} catch (NumberFormatException e) {
			} catch (NullPointerException e) {
			}
		}

	}

	public class AACHRequestResponse {

		private int reqID;

		private String reqType;

		private String status;

		private int intStatus;

		private String statusMessage;

		private Date timeStamp;

		private List transactions;

		private AACHTransaction details;

		public AACHRequestResponse(Element reqResponseElement) throws ParseException {

			reqID = 0;
			reqType = "";
			status = "";
			intStatus = -1;
			statusMessage = "";
			String resCount = "";
			int transID = 0;
			int transStatus = 0;

			Element reqElement = reqResponseElement;
			reqID = Integer.parseInt(reqElement.getAttribute("ID"));
			reqType = getChildElementValue(reqElement, "RequestType");
			status = getChildElementValue(reqElement, "Status");

			if (status.toLowerCase().equals("success")) {
				intStatus = PayrollActivity.REQUEST_STATUS_SUCCESS;
			} else if (status.toLowerCase().equals("fail")) {
				intStatus = PayrollActivity.REQUEST_STATUS_FAIL;
				// String statusMessage =
				// reqElement.getElementsByTagName("StatusMessage").item(0).getFirstChild().getNodeValue();
				// String strTimeStamp =
				// reqElement.getElementsByTagName("TimeStamp").item(0).getFirstChild().getNodeValue();
				// Date timeStamp = sdf.parse( strTimeStamp );
			}

			statusMessage = getChildElementValue(reqElement, "StatusMessage");

			if (reqElement.getElementsByTagName("TimeStamp").item(0) != null) {
				String strTimeStamp = reqElement.getElementsByTagName("TimeStamp").item(0).getFirstChild().getNodeValue();
				timeStamp = sdf.parse(strTimeStamp);
				// System.out.println(" updated timeStamp: " + timeStamp);
			}
			Element resultsElement = (Element) reqElement.getElementsByTagName("Results").item(0);
			resCount = resultsElement.getAttribute("Count");
			NodeList resultList = resultsElement.getElementsByTagName("Result");
			if (Integer.parseInt(resCount) > 0) {
				transactions = new ArrayList();
				for (int i = 0; i < resultList.getLength(); i++) {
					AACHTransaction trans = new AACHTransaction((Element) resultList.item(i));
					transactions.add(trans);
				}

			}

			NodeList detailsList = reqElement.getElementsByTagName("Details");
			if (detailsList != null && detailsList.getLength() > 0) {
				details = new AACHTransaction((Element) detailsList.item(0));
			}

		}

		public boolean hasValidTransaction() {
			return (getFirstValidTransaction() != null);
		}

		public AACHTransaction getFirstValidTransaction() {
			if (validTransaction == null) {
				for (Iterator iter = transactions.iterator(); iter.hasNext();) {
					AACHTransaction element = (AACHTransaction) iter.next();
					if (!"VOIDED".equalsIgnoreCase(element.status)) {
						validTransaction = element;
						return validTransaction;
					}

				}
			}
			return validTransaction;
		}

		AACHTransaction validTransaction = null;

	}

	static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");

	public class AACHResponse {

		private int failCount;

		private Date timeStamp;

		private List requestResultList;

		public AACHResponse(Element docElement) throws DOMException, ParseException, WrappedException {
			Element responseSummary = (Element) docElement.getElementsByTagName("ResponseSummary").item(0);
			String errorStr = getChildElementValue(responseSummary, "Error");
			if (errorStr != null && errorStr.equalsIgnoreCase("true"))
				throw new WrappedException("Error: " + getChildElementValue(responseSummary, "Message"),
								WrappedException.ERROR);
			failCount = Integer.parseInt(responseSummary.getElementsByTagName("FailCount").item(0).getFirstChild()
							.getNodeValue());
			if (failCount > 0) {
				report.setStatus(JobReportStatusEnumType.SUCCEED_WITH_ERROR);
			}

			timeStamp = sdf.parse(responseSummary.getElementsByTagName("TimeStamp").item(0).getFirstChild().getNodeValue());
			NodeList reqList = docElement.getElementsByTagName("Request");
			requestResultList = new ArrayList();
			for (int i = 0; i < reqList.getLength(); i++) {
				AACHRequestResponse requestRes = new AACHRequestResponse((Element) reqList.item(i));
				if (requestRes.intStatus == PayrollActivity.REQUEST_STATUS_FAIL) {
					report.addError(new WrappedException("Error occurred while processing request " + requestRes.reqID + " "
									+ requestRes.reqType + " " + requestRes.statusMessage));
				}
				requestResultList.add(requestRes);
			}

		}

	}


	/**
	 * Convenience method to avoid NPEs when accessing a child elements value
	 * 
	 * @param parElement
	 * @param elementName
	 * @return
	 */

	public String getChildElementValue(Element parElement, String elementName) {
		String result = null;
		if (parElement != null) {
			NodeList childrenWithName = parElement.getElementsByTagName(elementName);
			if (childrenWithName != null && childrenWithName.getLength() > 0) {
				Node element = childrenWithName.item(0);

				Node textNode = element.getFirstChild();
				if (textNode != null) {
					result = textNode.getNodeValue();
				}
			}
		}
		return result;
	}

	/*
	 * public static final String AACH_URL =
	 * "https://gateway.allianceach.com/requester/request-v2-post-test.asp";
	 * public static final String AACH_USERNAME = "DEMO"; public static final
	 * String AACH_PASSWORD = "DEMO"; public static final String
	 * ATMVERIFY_VALIDATED = "APPROVED:P70:VALIDATED";
	 */
	public static final String AACH_URL = ServerConfig.getInstance().getServerProperty("com.brainfuse.AACH_URL");

	public static final String AACH_USERNAME = ServerConfig.getInstance().getServerProperty("com.brainfuse.AACH_USERNAME");

	public final String AACH_PASSWORD;

	public static final String ATMVERIFY_VALIDATED = ServerConfig.getInstance().getServerProperty(
					"com.brainfuse.ATMVERIFY_VALIDATED");
	static {

	}

	private static CryptoMan cryptoMan;
	
	private PayrollTransaction pt;

	public AACHPayrollProvider(PayrollTransaction pt) throws WrappedException {
		this.pt = pt;
		cryptoMan = CryptoMan.getInstance();
		AACH_PASSWORD = ServerConfig.getInstance().getEncProperty("com.brainfuse.AACH_PASSWORD");
		report = new JobReport();
	}

	public String sendAACHRequest(String xmlParam) throws Exception {
		// System.out.println(xmlParam);
		String line;
		StringBuffer sb = new StringBuffer();
		HttpsMessage httpsMsg = new HttpsMessage(new URL(AACH_URL));

		Properties prop = new Properties();
		prop.put("xml", xmlParam);
		InputStream in = httpsMsg.sendPostMessage(prop);
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		while ((line = reader.readLine()) != null) {
			// System.out.println(line);
			sb.append(line.trim());
		}
		return sb.toString();
	}

	@Override
	public PayrollActivity createTransaction() throws Exception {
		String xmlParam = null;
		String aachRes = null;
		
		xmlParam = generateSearchTransactionsXML();
		log.log(Level.INFO, "Sending Search XML {0}", xmlParam);
		aachRes = sendAACHRequest(xmlParam);
		PayrollActivity pa = parseSearchTransactionResponse(aachRes);

		if (pa == null) {
			xmlParam = generateCreateCreditTransactionXML();
			aachRes = sendAACHRequest(xmlParam);
			pa = parseCreateCreditTransactionResponse(aachRes);
		} 
		
		return pa;
	}

	@Override
	public PayrollActivity getTransactionsDetails() throws Exception {
		String xmlParam = null;
		String result = null;
		
		xmlParam = generateShowTransactionsDetails();
		result = sendAACHRequest(xmlParam);
		
		PayrollActivity pa = parseShowTransactionDetailsResponse(result);
		return pa;
	}
	
	
	/**
	 * generate the xml string to search if a transaction exist for each one of
	 * the provided PayrollTransaction objects before submitting a new
	 * transaction.
	 * 
	 * @param ptList
	 * @return
	 * @throws Exception
	 */
	private String generateSearchTransactionsXML() throws Exception {
		String xmlParam = "";

		Document doc = getAACHRequestDocument();
		Element rootElement = doc.getDocumentElement();

		generateSearchPayrollTransaction(doc, rootElement);

		xmlParam = getAACHXmlString(doc);
		return xmlParam;
	}
	
	/**
	 * @return
	 * @throws SAXException
	 * @throws IOException
	 */
	private Document getAACHRequestDocument() throws SAXException, IOException {
		Document doc = XMLUtils.getXMLDocument(new org.xml.sax.InputSource(new java.io.StringReader("<AACHRequest></AACHRequest>")), false);
		Element rootElement = doc.getDocumentElement();

		Node auth = rootElement.appendChild(doc.createElement("Authentication"));
		Node username = auth.appendChild(doc.createElement("Username"));
		username.appendChild(doc.createTextNode(AACH_USERNAME));
		Node password = auth.appendChild(doc.createElement("Password"));
		password.appendChild(doc.createTextNode(AACH_PASSWORD));

		return doc;
	}
	
	
	/**
	 * @param doc
	 * @param rootElement
	 * @param pt
	 * @throws WrappedException
	 */
	private void generateSearchPayrollTransaction(Document doc, Element rootElement)
					throws WrappedException {

		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		float amount = pt.getPayrollAmount();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.WEEK_OF_MONTH, -1);
		Date transBeginDate = cal.getTime();
		cal				= Calendar.getInstance();
		
		cal.add(Calendar.DAY_OF_MONTH, 8);
		Date transEndDate = cal.getTime();

		TutorPayroll payroll = pt.getTutorPayroll();
		int payrollID = payroll.getTutorPayrollID();
		int uid = payroll.getUID();
		String abaNum = cryptoMan.hexToPassword(payroll.getRoutingNum());
		String acctNum = cryptoMan.hexToPassword(payroll.getCheckingAcctNum());

		// TutorData tutorData = (TutorData)TutorData.getInstance();
		// tutorData.setUserID ( uid );
		// UserService.getInstance().loadTutorInfoByID ( tutorData, null );
		String firstName = payroll.getFirstName();
		String lastName = payroll.getLastName();

		// DbList tsList = pt.getTimeSheetList();

		Element req = null;
		rootElement.appendChild((req = doc.createElement("Request")));
		req.setAttribute("ID", String.valueOf(payrollID));
		Node reqType = req.appendChild(doc.createElement("RequestType"));

		req.appendChild(doc.createElement("TransactionDateBegin")).appendChild(
						doc.createTextNode(sdf.format(transBeginDate)));
		req.appendChild(doc.createElement("TransactionDateEnd")).appendChild(doc.createTextNode(sdf.format(transEndDate)));
		// req.setAttribute( "ID", String.valueOf(uid) );
		reqType.appendChild(doc.createTextNode(PayrollActivity.REQUEST_TYPE_SEARCH_TRANSACTION));
		Node aNum = req.appendChild(doc.createElement("AccountNumber"));
		aNum.appendChild(doc.createTextNode(acctNum));
		Node aba = req.appendChild(doc.createElement("ABANumber"));
		aba.appendChild(doc.createTextNode(abaNum));
		Node amt = req.appendChild(doc.createElement("Amount"));
		amt.appendChild(doc.createTextNode(String.valueOf(amount)));
		Node sec = req.appendChild(doc.createElement("SECCode"));
		sec.appendChild(doc.createTextNode("PPD"));
		req.appendChild(doc.createElement("CustomerID")).appendChild(doc.createTextNode(String.valueOf(uid)));

		//
	}
	
	/**
	 * @param doc
	 * @return
	 * @throws IOException
	 */
	private String getAACHXmlString(Document doc) throws IOException {
		String xmlParam;
		OutputFormat formatter = new OutputFormat(doc, "ISO-8859-1", true);
		formatter.setOmitXMLDeclaration(false);
		formatter.setIndenting(false);

		StringWriter out = new StringWriter(1024);

		XMLSerializer serializer = new XMLSerializer(out, formatter);
		serializer.asDOMSerializer();
		serializer.serialize(doc);

		xmlParam = out.toString();
		return xmlParam;
	}
	
	/**
	 * returns PayrollActivity object for the PayrollTransaction passed. The
	 * data of the paryroll activity is filled from the ALLIANCE ACH response.
	 * The method returns null if the PayrollTransaction was not found in the
	 * response.
	 */
	private PayrollActivity parseSearchTransactionResponse(String xmlString) throws Exception {

		Document doc = Tools.getXmlDocument(xmlString, true, false);
		Element docElement = doc.getDocumentElement();
		log.log(Level.INFO, "XML SearchTransactionResponse: \n {0} ", xmlString);
		AACHResponse response = new AACHResponse(docElement);

		log.log(Level.INFO, "SearchTransactionResponse: FailCount {0} Timestamp {1} ", new Object[] {
						String.valueOf(response.failCount), response.timeStamp });
		List reqList = response.requestResultList;
		for (int i = 0; i < reqList.size(); i++) {
			AACHRequestResponse req = (AACHRequestResponse) reqList.get(i);
			return getPayrollActivityFromSearchRequest(req);
		}
		return null;
	}

	String generateCreateCreditTransactionXML() throws Exception {
		String xmlParam = "";

		Document doc = getAACHRequestDocument();
		Element rootElement = doc.getDocumentElement();

		generateCreditPayrollTransaction(doc, rootElement);

		xmlParam = getAACHXmlString(doc);
		log.log(Level.FINEST, "Sending CreateCreditTransaction XML : {0}", xmlParam);
		return xmlParam;
	}
	
	
	/**
	 * @param doc
	 * @param rootElement
	 * @param pt
	 * @throws WrappedException
	 */
	private void generateCreditPayrollTransaction(Document doc, Element rootElement)
					throws WrappedException {

		float amount = pt.getPayrollAmount();
		TutorPayroll payroll = pt.getTutorPayroll();
		int payrollID = payroll.getTutorPayrollID();
		int uid = payroll.getUID();
		log.log(Level.FINEST, "Routing #: {0} Account Num {1} ", new Object[] {
						cryptoMan.hexToPassword(payroll.getRoutingNum()),
						cryptoMan.hexToPassword(payroll.getCheckingAcctNum()) });
		String abaNum = cryptoMan.hexToPassword(payroll.getRoutingNum());
		String acctNum = cryptoMan.hexToPassword(payroll.getCheckingAcctNum());

		TutorData tutorData = (TutorData) TutorData.getInstance();
		tutorData.setUserID(uid);
		UserService.getInstance().loadTutorInfoByID(tutorData, null);
		String firstName = payroll.getFirstName();
		String lastName = payroll.getLastName();

		// DbList tsList = pt.getTimeSheetList();

		Element req = doc.createElement("Request");
		rootElement.appendChild(req);
		// req.setAttribute( "ID", String.valueOf(uid) );
		req.setAttribute("ID", String.valueOf(payrollID));
		Node reqType = req.appendChild(doc.createElement("RequestType"));
		reqType.appendChild(doc.createTextNode(PayrollActivity.REQUEST_TYPE_DIRECT_DEPOIST));
		Node aT = req.appendChild(doc.createElement("AccountType"));
		aT.appendChild(doc.createTextNode("Checking"));
		Node aName = req.appendChild(doc.createElement("AccountName"));
		aName.appendChild(doc.createTextNode(firstName + " " + lastName));
		Node aNum = req.appendChild(doc.createElement("AccountNumber"));
		aNum.appendChild(doc.createTextNode(acctNum));
		Node aba = req.appendChild(doc.createElement("ABANumber"));
		aba.appendChild(doc.createTextNode(abaNum));
		Node amt = req.appendChild(doc.createElement("Amount"));
		amt.appendChild(doc.createTextNode(String.valueOf(amount)));
		Node sec = req.appendChild(doc.createElement("SECCode"));
		sec.appendChild(doc.createTextNode("PPD"));
		req.appendChild(doc.createElement("CustomerID")).appendChild(doc.createTextNode(String.valueOf(uid)));
	}
	
	
	/**
	 * @param pt
	 * @param req
	 * @throws WrappedException
	 */
	private PayrollActivity getPayrollActivityFromSearchRequest(AACHRequestResponse req)
					throws WrappedException {
		int reqID = req.reqID;
		if (req.intStatus == PayrollActivity.REQUEST_STATUS_SUCCESS) {
			List results = req.transactions;
			if (results != null && results.size() > 0) {
				// a transaction has already been entered for this payroll
				// transaction,
				// let's remove it from the list of
				// transactions to be processed.
				// int ptIndex = ptList.indexOf(new
				// PayrollTransactionFinder(req.reqID));
				for (Iterator iter = results.iterator(); iter.hasNext();) {
					AACHTransaction trans = (AACHTransaction) iter.next();
					// Found a correct payroll transaction let's process it 
					float rndSearchTransAmount	= new BigDecimal( trans.amount ).setScale(2, BigDecimal.ROUND_UP).floatValue();
					float rndPayAmount = new BigDecimal( pt.getPayrollAmount() ).setScale(2, BigDecimal.ROUND_UP).floatValue();
					int transStatus		= PayrollActivity.getTransactionStatusValue(trans.status);
					log.fine("TransAmt:" + rndSearchTransAmount + " == PayAmt:" + rndPayAmount + " " + trans.status);
					if (rndSearchTransAmount == rndPayAmount && transStatus != PayrollActivity.STATUS_VOIDED.getValue()
									&& transStatus != PayrollActivity.STATUS_RETURNED.getValue()) {
						
						Date createDate = new Date(req.timeStamp.getTime() + 3 * 60 * 60 * 1000);

						PayrollActivity payrollActivity = new PayrollActivity(reqID,
										PayrollActivity.REQUEST_TYPE_DIRECT_DEPOIST, req.intStatus, req.statusMessage,
										createDate, trans.transactionID,
										PayrollActivity.getTransactionStatusValue(trans.status),
										PayrollActivity.COMMENTS_DIRECT_DEPOIST, timeSheetSubmitter);

						log.log(Level.INFO, "Transaction ID {0} transaction status {1}", new Object[] {
										String.valueOf(trans.transactionID), String.valueOf(trans.status) });

						payrollActivity.setTimeSheetList(pt.getTimeSheetList());
						payrollActivity.setNewFlag(true);
						return payrollActivity;
					}
					
				}
				return null;
			} else
				return null;

		} else
			throw new WrappedException("Unexpected Request Failure '" + req.statusMessage + "'", WrappedException.ERROR);
	}
	
	/**
	 * returns DbList of PayrollActivity objects
	 */
	private PayrollActivity parseCreateCreditTransactionResponse(String xmlString)
					throws Exception {
		log.log(Level.FINE, "CreateCreditTransactionResponse: FailCount \n {0} ", xmlString);
		Document doc = Tools.getXmlDocument(xmlString, true, false);
		Element docElement = doc.getDocumentElement();

		AACHResponse response = new AACHResponse(docElement);

		log.log(Level.INFO, " FailCount {0} Timestamp {1} ", new Object[] { String.valueOf(response.failCount),
						response.timeStamp });
		// System.out.println("\nfailCountValue: " + failCountValue);
		// System.out.println("strTimeStamp: " + strTimeStamp);
		// System.out.println("Date timeStamp: " + timeStamp + "\n");
		List reqList = response.requestResultList;

		for (int i = 0; i < reqList.size(); i++) {
			return getPayrollActivityForPayrollTransaction((AACHRequestResponse) reqList.get(i));
		}
		return null;
	}
	
	
	/**
	 * @param payrollTransaction
	 * @param reqList
	 * @param reqType
	 * @param i
	 * @return
	 */
	private PayrollActivity getPayrollActivityForPayrollTransaction(AACHRequestResponse req) {

		if (pt.getTutorPayroll().getTutorPayrollID() != req.reqID) {
			throw new IllegalArgumentException("Payroll transaction passed is not the same as the request.");
		}
		String reqType = PayrollActivity.REQUEST_TYPE_DIRECT_DEPOIST;
		int intStatus = req.intStatus;
		String statusMessage = req.statusMessage;
		String transID = "";
		int transStatus = 0;

		Date timeStamp = req.timeStamp;
		try {

			List results = req.transactions;

			if (intStatus == PayrollActivity.REQUEST_STATUS_SUCCESS && results != null && results.size() > 0) {
				AACHTransaction result = (AACHTransaction) results.get(0);
				transID = result.transactionID;

				transStatus = PayrollActivity.getTransactionStatusValue(result.status);
				log.log(Level.FINE, "Transaction ID {0} transaction status {1}", new Object[] {
								String.valueOf(result.transactionID), String.valueOf(result.status) });
			} else if (intStatus == PayrollActivity.REQUEST_STATUS_FAIL) {
				throw new RequestFailedException(req.statusMessage);
			} else {

				throw new IllegalStateException("No transaction even through status is success.");
			}

			/*
			 * System.out.println(" reqID: " + reqID); System.out.println("
			 * reqType: " + reqType); System.out.println(" status: " + status + " -- " +
			 * intStatus); System.out.println(" statusMessage: " +
			 * statusMessage); System.out.println(" transID: " + transID);
			 * System.out.println(" transStatus: " + transStatus + "\n");
			 */

			// PayrollActivity payrollActivity =
			// PayrollActivity.getInstance();
			Date createDate = new Date(timeStamp.getTime() + 3 * 60 * 60 * 1000);

			PayrollActivity payrollActivity = new PayrollActivity(req.reqID, reqType, intStatus, statusMessage, createDate,
							transID, transStatus, PayrollActivity.COMMENTS_DIRECT_DEPOIST, timeSheetSubmitter);

			payrollActivity.setTimeSheetList(pt.getTimeSheetList());

			payrollActivity.setNewFlag(true);
			return payrollActivity;

		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			PayrollActivity payrollActivity = new PayrollActivity(req.reqID, reqType, PayrollActivity.REQUEST_STATUS_FAIL,
							statusMessage, new Date(), "", 0, "Exception in parseCreateCreditTransactionResponse: "
											+ e.getMessage(), timeSheetSubmitter);

			payrollActivity.setTimeSheetList(pt.getTimeSheetList());
			payrollActivity.setNewFlag(true);

			try {
				WrappedException.processException(e);
			} catch (Exception ex) {
			}
			return payrollActivity;

		}
	}
	
	/**
	 * generate the xml string to show the details of the transactions specified
	 * with the PayrollTransaction. before submitting a new transaction.
	 * 
	 * @param paList
	 * @return
	 * @throws Exception
	 */
	private String generateShowTransactionsDetails() throws Exception {
		String xmlParam = "";

		Document doc = getAACHRequestDocument();
		Element rootElement = doc.getDocumentElement();

		generateShowTransactionDetails(doc, rootElement);

		xmlParam = getAACHXmlString(doc);
		return xmlParam;
	}	
	
	/**
	 * @param doc
	 * @param rootElement
	 * @param pt
	 * @throws WrappedException
	 */
	private void generateShowTransactionDetails(Document doc, Element rootElement)
					throws WrappedException {

		Element req = doc.createElement("Request");
		rootElement.appendChild(req);
		req.setAttribute("ID", String.valueOf(pt.getTutorPayroll().getTutorPayrollID()));
		Node reqType = req.appendChild(doc.createElement("RequestType"));
		reqType.appendChild(doc.createTextNode("ShowTransactionDetails"));
		req.appendChild(doc.createElement("TransactionID")).appendChild(
						doc.createTextNode(String.valueOf(pt.getTransactionID())));

	}
	
	/**
	 * returns DbList of PayrollActivity objects
	 */
	private PayrollActivity parseShowTransactionDetailsResponse(String xmlString) throws Exception {
		Document doc = Tools.getXmlDocument(xmlString, true, false);
		Element docElement = doc.getDocumentElement();

		AACHResponse response = new AACHResponse(docElement);

		log.log(Level.INFO, "ShowTransactionDetailResponse: {0} \n FailCount {1} Timestamp {2} ", new Object[] {
						xmlString, String.valueOf(response.failCount), response.timeStamp });
		List reqList = response.requestResultList;
		AACHRequestResponse req = null;
		for (int i = 0; i < reqList.size(); i++) {
			req = (AACHRequestResponse) reqList.get(i);
			return parseShowTransactionDetailsResponse(req);
		}
		return null;
	}
	
	/**
	 * @param ptList
	 * @param list
	 * @param req
	 * @throws WrappedException
	 */
	private PayrollActivity parseShowTransactionDetailsResponse(AACHRequestResponse req)
					throws WrappedException {
		if (req.details != null) {

			Date createDate = new Date(req.timeStamp.getTime() + 3 * 60 * 60 * 1000);

			PayrollActivity payrollActivity = new PayrollActivity(req.reqID, PayrollActivity.REQUEST_TYPE_DIRECT_DEPOIST,
							req.intStatus, req.statusMessage, createDate, req.details.transactionID, PayrollActivity
											.getTransactionStatusValue(req.details.status),
							PayrollActivity.COMMENTS_DIRECT_DEPOIST, timeSheetSubmitter);

			payrollActivity.setTimeSheetList(pt.getTimeSheetList());
			payrollActivity.setNewFlag(true);
			return (payrollActivity);

		} else
			throw new WrappedException("Unexpected Request Failure " + req.statusMessage, WrappedException.ERROR);
	}

}
