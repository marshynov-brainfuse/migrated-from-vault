package com.eggtheory.brainfuse.server.payroll.paypal;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.eggtheory.brainfuse.bizobjects.user.PayrollActivity;
import com.eggtheory.brainfuse.bizobjects.user.PayrollTransaction;
import com.eggtheory.brainfuse.server.payroll.PayrollProvider;
import com.eggtheory.brainfuse.utils.URLComposer;

public class PaypalPayrollProvider implements PayrollProvider {

	static Logger log = null;

	static {
		log = Logger
				.getLogger("com.eggtheory.brainfuse.server.payroll.PaypalPayrollProvider");
	}

	static int timeSheetSubmitter = com.eggtheory.brainfuse.bizobjects.account.UserData.TIME_SHEET_SUBMITTER;
	
	public static final String NVP_METHOD_PAY = "method.ap.pay";
	public static final String NVP_METHOD_GET_DETAIL = "method.ap.pay.details";
	
	private PayrollTransaction pt;

	public PaypalPayrollProvider(PayrollTransaction pt) {
		this.pt = pt;
	}
	
	class NVPResponse {
		
		public static final String PAYPAL_TRANSACTION_ACK_SUCCESS = "Success";
		
		public static final String PAYPAL_TRANSACTION_ACK_FAILURE = "Failure";
		
		private Date transactionDate;
		
		private String ack;
		
		private String transactionKey = "";
		
		private String status = "";
		
		private String errorId = "";
		
		private String errorMessage = "";
		
		public NVPResponse(String response) throws ParseException {
			StringTokenizer st = new StringTokenizer(response, "&");
			while(st.hasMoreTokens()) {
				String token = st.nextToken();
				String key = token.substring(0, token.indexOf("="));
				String value = token.substring(token.indexOf("=") + 1);
				if (key.contains("timestamp")) {
					String timeStr = value.replace("%3A", ":").replace("T", " ");
					timeStr = timeStr.substring(0, timeStr.lastIndexOf(":")) + timeStr.substring(timeStr.lastIndexOf(":") + 1);
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
					Date date = (Date)sdf.parse(timeStr);
					transactionDate = date;
				} else if (key.contains("ack")) {
					ack = value;
				} else if (key.contains("payKey")) {
					transactionKey = value;
				} else if (key.toUpperCase().contains("STATUS")) {
					status = value;
				} else if (key.startsWith("error") && key.contains("errorId")) {
					errorId = value;
				} else if (key.startsWith("error") && key.contains("message")) {
					errorMessage = value.replace("+", " ");
				}
			}
			
		}

		public String getAck() {
			return ack;
		}

		public Date getTransactionTime() {
			return transactionDate;
		}

		public String getTransactionKey() {
			return transactionKey;
		}

		public String getStatus() {
			return status;
		}	
		
		public String getErrorId() {
			return errorId;
		}
		
		public String getErrorMessage() {
			return errorMessage;
		}
		
	}

	@Override
	public PayrollActivity createTransaction()throws Exception {

		PayrollActivity pa = null;
		pa = getTransactionsDetails();

		if (pa == null) {
			log.log(Level.INFO, "Create Transaction.");
			String paypalNvpRequest = generateNvpPayRequest("PAY", "Brainfuse Payment", 
					pt.getPayrollAmount(), pt.getTutorPayroll().getCheckingAcctNum());
			
			log.log(Level.INFO, "Send Payment request {0}",paypalNvpRequest);
			String endpoint = getEndpoint(NVP_METHOD_PAY);
			
			AdaptiveDataObject ado = new AdaptiveDataObject(endpoint,
					paypalNvpRequest);
			ado.execute();
			String responseStr = ado.get("responseBody");
			
			log.log(Level.INFO, "Get Paypal payment response {0}",responseStr);
			NVPResponse response = new NVPResponse(responseStr);
			
			if(NVPResponse.PAYPAL_TRANSACTION_ACK_SUCCESS.equals(response.getAck())) {
				pa = new PayrollActivity(pt.getTutorPayroll().getTutorPayrollID(),
						PayrollActivity.REQUEST_PAYPAL_TRANSACTION,
						PayrollActivity.REQUEST_STATUS_SUCCESS, 
						response.getAck(), 
						response.getTransactionTime(), 
						response.getTransactionKey(),
						PayrollActivity.getTransactionStatusValue(response.getStatus()),
						PayrollActivity.COMMENTS_PAYPAL_TRANSACTION, timeSheetSubmitter);
				pa.setTimeSheetList(pt.getTimeSheetList());
				pa.setNewFlag(true);
			} else {
				pa = new PayrollActivity(pt.getTutorPayroll()
						.getTutorPayrollID(),
						PayrollActivity.REQUEST_PAYPAL_TRANSACTION,
						PayrollActivity.REQUEST_STATUS_FAIL, 
						response.getAck(), 
						response.getTransactionTime(), 
						response.getErrorId(),
						PayrollActivity.getTransactionStatusValue("ERROR"),
						response.getErrorMessage(), timeSheetSubmitter);
				pa.setTimeSheetList(pt.getTimeSheetList());
				pa.setNewFlag(true);
			}
		}

		return pa;
	}

	@Override
	public PayrollActivity getTransactionsDetails() throws Exception {
		String transactionID = pt.getTransactionID();
		String paypalNvpTransactionDetailRequest = generateNvpTransactionDetailRequest(transactionID);
		log.log(Level.INFO, "Send getTransactionsDetail request {0}",paypalNvpTransactionDetailRequest);
		
		String endpoint = getEndpoint(NVP_METHOD_GET_DETAIL);
		
		AdaptiveDataObject ado = new AdaptiveDataObject(endpoint,
				paypalNvpTransactionDetailRequest);
		ado.execute();
		String responseStr = ado.get("responseBody");
		log.log(Level.INFO, "GetTransactionsDetail response {0}",responseStr);
		
		NVPResponse response = new NVPResponse(ado.get("responseBody"));
		
		PayrollActivity payrollActivity = null;
		
		if (NVPResponse.PAYPAL_TRANSACTION_ACK_SUCCESS.equals(response.getAck())) {
			payrollActivity = new PayrollActivity(pt.getTutorPayroll()
					.getTutorPayrollID(),
					PayrollActivity.REQUEST_TYPE_SEARCH_TRANSACTION,
					PayrollActivity.REQUEST_STATUS_SUCCESS, 
					response.getAck(), 
					response.getTransactionTime(), 
					response.getTransactionKey(),
					PayrollActivity.getTransactionStatusValue(response.getStatus()),
					PayrollActivity.COMMENTS_PAYPAL_TRANSACTION, timeSheetSubmitter);
			payrollActivity.setTimeSheetList(pt.getTimeSheetList());
		}
		return payrollActivity;
	}
	
	private String generateNvpPayRequest(String actionType, String memo, double amount, String email) {
		URLComposer url = new URLComposer("");
		url.addParam("requestEnvelope.errorLanguage", "en_US");
		url.addParam("requestEnvelope.details", "ReturnAll");
		url.addParam("actionType", actionType);
		url.addParam("cancelUrl", "http://www.brainfuse.com/");		
		url.addParam("currencyCode", "USD");
		url.addParam("ipnNotificationUrl", "http://www.brainfuse.com/");
		url.addParam("memo", memo);
		url.addParam("receiverList.receiver(0).amount", amount + "");
		url.addParam("receiverList.receiver(0).email", email);
		url.addParam("returnUrl", "http://www.paypal.com");
		return url.toString().substring(1);
	}
	
	private String generateNvpTransactionDetailRequest(String payKey) {
		URLComposer url = new URLComposer("");
		url.addParam("payKey", payKey);
		url.addParam("requestEnvelope.detailLevel", "ReturnAll");
		url.addParam("requestEnvelope.errorLanguage", "en_US");
		url.addParam("currencyCode", "USD");
		return url.toString().substring(1);
	}
	
	private String getEndpoint(String method) {
		String endpoint = DtsUtil.getString("host.adaptive")
				+ DtsUtil.getString("adaptive.payments")
				+ DtsUtil.getString(method);
		return endpoint;
	}

}
