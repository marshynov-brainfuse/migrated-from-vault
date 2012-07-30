package com.eggtheory.brainfuse.web.tutor;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.actbig.datahandler.dbholders.DbList;
import com.eggtheory.brainfuse.bizobjects.billing.JobReport;
import com.eggtheory.brainfuse.bizobjects.user.PayrollActivity;
import com.eggtheory.brainfuse.bizobjects.user.PayrollTransaction;
import com.eggtheory.brainfuse.server.payroll.PayrollProvider;
import com.eggtheory.brainfuse.server.payroll.PayrollProviderFactory;

public class XmlRequester {

	static Logger log = null;

	static {
		log = Logger.getLogger("com.eggtheory.brainfuse.user.Payroll");
	}
	
	private JobReport report;
	
	public XmlRequester() {
		report = new JobReport();
	}
	
	public static class PayrollTransactionFinder {

		int tutorPayrollID;

		public PayrollTransactionFinder(int aTutorPayrollID) {
			tutorPayrollID = aTutorPayrollID;
		}

		public boolean equals(Object o) {
			boolean result = false;
			PayrollTransaction trans = (PayrollTransaction) o;
			if (trans.getTutorPayroll().getTutorPayrollID() == tutorPayrollID)
				return true;
			else
				return false;
		}
	}

	class PayrollActivityFinder {
		String transactionID;

		public PayrollActivityFinder(String aTransactionID) {
			transactionID = aTransactionID;
		}

		public boolean equals(Object o) {
			boolean result = false;
			PayrollActivity trans = (PayrollActivity) o;
			if (trans.getTransactionID().equals(transactionID))
				return true;
			else
				return false;
		}
	}

	/**
	 * main method for ATMVerify
	 */
	/*public List ATMVerify(String amount, String abaNum, String acctNum, String acctType) throws Exception {
		String xmlParam = generateATMVerifyXML(amount, abaNum, acctNum, acctType);
		String result = sendAACHRequest(xmlParam);
		return parseATMVerifyResponse(result);
	}*/

	/**
	 * main method for CreateCreditTransaction
	 */
	public DbList createCreditTransaction(List ptList) throws Exception {
		DbList<PayrollActivity> payrollActivityList = DbList.getInstance();
		// First let's find out if any of these transaction have already been
		// submitted for processing.
		for (Iterator iter = ptList.iterator(); iter.hasNext();) {
			try {
				
				PayrollTransaction pt = (PayrollTransaction) iter.next();				
				log.fine("Processing transaction for " + pt);
				
				int payrollOption = pt.getTutorPayroll().getPayrollOptionID();
				PayrollProvider payrollProvider = PayrollProviderFactory.getPayrollProvider(payrollOption, pt);
				PayrollActivity pa = payrollProvider.createTransaction();
				payrollActivityList.add(pa);
				
			} catch (RuntimeException e) {
				log.log(Level.SEVERE, "Error:", e);
			}

		}
		report.sendStatus();

		return payrollActivityList;
	}

	/**
	 * main method for CreateCreditTransaction
	 */
	public DbList updatePendingTransactionStatus(List payrollTransactions) throws Exception {
		DbList<PayrollActivity> pas = DbList.getInstance();
		// First let's find out if any of these transaction have already been
		// submitted for processing.
		for (Iterator iter = payrollTransactions.iterator(); iter.hasNext();) {
			PayrollTransaction pt = (PayrollTransaction) iter.next();
			
			int payrollOption = pt.getTutorPayroll().getPayrollOptionID();
			PayrollProvider payrollProvider = PayrollProviderFactory.getPayrollProvider(payrollOption, pt);
			PayrollActivity pa = payrollProvider.getTransactionsDetails();
			
			// clear any that might have been.
			pas.add(pa);

		}
		return pas;
	}

	/*private List parseATMVerifyResponse(String xmlString) throws Exception {
		// System.out.println(xmlString);
		// String response = "";
		List response = new ArrayList();
		// try
		// {
		Document doc = Tools.getXmlDocument(xmlString, true, false);
		Element docElement = doc.getDocumentElement();
		// System.out.println("docElement: " + docElement.getNodeName());

		NodeList nodeList = docElement.getElementsByTagName("ResponseSummary");
		// System.out.println("nodes size: " + nodeList.getLength() );

		for (int counter = 0; counter < nodeList.getLength(); counter++) {
			Element nodeElement = (Element) nodeList.item(counter);
			// System.out.println("nodeElement: " +
			// nodeElement.getNodeName() );
			
			 * System.out.println("child size: " +
			 * nodeElement.getChildNodes().getLength() );
			 * 
			 * for ( int i=0; i<nodeElement.getChildNodes().getLength(); i++ ) {
			 * //Node
			 * fromNode=docElement.getElementsByTagName("from").item(0).getFirstChild();
			 * //Element theElement =
			 * (Element)nodeElement.getChildNodes().item(i); Node theElement =
			 * nodeElement.getChildNodes().item(i).getFirstChild();
			 * System.out.println(" child name: " +theElement.getNodeName() + " -- " +
			 * theElement.getNodeValue() + " -- " + theElement.getNodeType() ); }
			 

			Node failElement = nodeElement.getElementsByTagName("FailCount").item(0).getFirstChild();
			String failCountValue = failElement.getNodeValue();
			// System.out.println("failCountValue: " + failCountValue );

			NodeList reqList = docElement.getElementsByTagName("Request");
			for (int i = 0; i < reqList.getLength(); i++) {
				Element reqElement = (Element) reqList.item(i);
				if (!failCountValue.equals("0")) {
					Node msgElement = reqElement.getElementsByTagName("StatusMessage").item(0).getFirstChild();
					String statusMsg = msgElement.getNodeValue();
					// System.out.println("statusMsg: " + statusMsg );
					// response = "false|" + statusMsg;
					response.add(statusMsg);
				} else {
					// NodeList resList =
					// docElement.getElementsByTagName("Results");
					NodeList resList = reqElement.getElementsByTagName("Results");

					for (int j = 0; j < resList.getLength(); j++) {
						Element resElement = (Element) resList.item(j);
						NodeList resultList = docElement.getElementsByTagName("Result");
						for (int k = 0; k < resultList.getLength(); k++) {
							Element result = (Element) resultList.item(k);
							Node verifyNode = result.getElementsByTagName("ATMVerify").item(0).getFirstChild();
							String verifyMsg = verifyNode.getNodeValue();
							// System.out.println("verifyMsg: " + verifyMsg
							// );
							if (verifyMsg.equals(ATMVERIFY_VALIDATED))
								// response = "true|" + verifyMsg;
								;
							else
								// response = "false|" + verifyMsg;
								response.add(verifyMsg);
						}
					}
				}
			}
		}
		
		 * } catch (Exception ex) { ex.printStackTrace(); }
		 
		return response;

	}

	*//**
	 * Generata ATMVerify request in XML format
	 *//*
	private String generateATMVerifyXML(String amount, String abaNum, String acctNum, String acctType) throws Exception {
		String xmlParam = "";
		Document doc = getAACHRequestDocument();
		Element rootElement = doc.getDocumentElement();

		Node auth = rootElement.appendChild(doc.createElement("Authentication"));
		Node username = auth.appendChild(doc.createElement("Username"));
		username.appendChild(doc.createTextNode(AACH_USERNAME));
		Node password = auth.appendChild(doc.createElement("Password"));
		password.appendChild(doc.createTextNode(AACH_PASSWORD));

		Node req = rootElement.appendChild(doc.createElement("Request"));
		Node reqType = req.appendChild(doc.createElement("RequestType"));
		reqType.appendChild(doc.createTextNode("ATMVerify"));
		Node amt = req.appendChild(doc.createElement("Amount"));
		amt.appendChild(doc.createTextNode(amount));
		Node aba = req.appendChild(doc.createElement("ABANumber"));
		aba.appendChild(doc.createTextNode(abaNum));
		Node aN = req.appendChild(doc.createElement("AccountNumber"));
		aN.appendChild(doc.createTextNode(acctNum));
		Node aT = req.appendChild(doc.createElement("AccountType"));
		aT.appendChild(doc.createTextNode(acctType));

		xmlParam = getAACHXmlString(doc);

		
		 * } catch (Exception ex) { ex.printStackTrace(); }
		 
		return xmlParam;
	}
		

	*//**
	 * returns DbList of PayrollActivity objects
	 *//*
	private DbList parseSearchTransactionsResponse(String xmlString, List ptList) throws Exception {

		DbList payrollActivityList = DbList.getInstance();
		Document doc = Tools.getXmlDocument(xmlString, true, false);
		Element docElement = doc.getDocumentElement();
		log.log(Level.INFO, "XML SearchTransactionResponse: \n {0} ", xmlString);
		AACHResponse response = new AACHResponse(docElement);

		log.log(Level.INFO, "SearchTransactionResponse: FailCount {0} Timestamp {1} ", new Object[] {
						String.valueOf(response.failCount), response.timeStamp });
		List reqList = response.requestResultList;
		for (int i = 0; i < reqList.size(); i++) {
			AACHRequestResponse req = (AACHRequestResponse) reqList.get(i);
			int ptIndex = ptList.indexOf(new PayrollTransactionFinder(req.reqID));
			PayrollTransaction payrollTrans = (PayrollTransaction) ptList.get(ptIndex);
			PayrollActivity payrollActivity = getPayrollActivityFromSearchRequest(payrollTrans, req);
			if (payrollActivity != null) {
				payrollActivityList.add(payrollActivity);
				ptList.remove(ptIndex);
			}
		}
		return payrollActivityList;
	}

	*//**
	 * returns DbList of PayrollActivity objects
	 *//*
	private DbList parseCreateCreditTransactionsResponse(String xmlString, List ptList) throws Exception {
		DbList payrollActivityList = DbList.getInstance();
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
			AACHRequestResponse req = (AACHRequestResponse) reqList.get(i);
			PayrollTransaction payrollTransaction = (PayrollTransaction) ptList.get(ptList
							.indexOf(new PayrollTransactionFinder(req.reqID)));
			PayrollActivity payrollActivity = getPayrollActivityForPayrollTransaction(payrollTransaction, req);
			if (payrollActivity != null) {
				payrollActivityList.addElement(payrollActivity);
			}

		}
		return payrollActivityList;
	}*/

}
