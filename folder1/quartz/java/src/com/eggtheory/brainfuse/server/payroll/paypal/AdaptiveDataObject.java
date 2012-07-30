package com.eggtheory.brainfuse.server.payroll.paypal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.net.ssl.HttpsURLConnection;

/**
 * This class is a HashMap that controls the Adaptive APIs execution. Any property
 * can be set or retrieved using the HashMap 'put' and 'get' methods. It contains
 * all of the properties that are used during the request plus any new properties that
 * the user chose to set in the calling classes and all of the request and
 * response properties after execution.
 * 
 * @author roallan
 *
 */
public class AdaptiveDataObject extends HashMap<String, String> {

	static final long serialVersionUID = System.currentTimeMillis();
	
	private HashMap<String, String> protHeaders = null;
	
	public AdaptiveDataObject(String endPoint, String requestBody) {
		put("endPoint", endPoint);
		put("requestBody", requestBody);
	}
	
	/**
	 * Execute the request on the current set of properties.
	 * 
	 * @throws Exception
	 */
	public void execute() throws Exception {
		
		try {
			URL url = new URL(get("endPoint"));
			HttpsURLConnection uconn = (HttpsURLConnection) url
					.openConnection();
			
			uconn.setDoOutput(true);
			HashMap<String, String> headerMap = getHeadersForProtocol();
			Iterator<String> it = headerMap.keySet().iterator();
			while (it.hasNext()) {
				String key = it.next();
				String value = headerMap.get(key);
				uconn.setRequestProperty(key, value);
			}

			OutputStreamWriter wr = new OutputStreamWriter(uconn
					.getOutputStream());
			String requestBody =  get("requestBody");
			System.err.println("REQUEST BODY: " + requestBody);
			wr.write(requestBody);
			wr.flush();
			wr.close();
			handleNvpResponse(uconn);
			
			if(get("payKey") != null)  {
				put("redirectUrl",DtsUtil.getString("host.adaptive.redirect") + DtsUtil.getString("label.approve_paykey") + get("payKey"));
			}
			
			if(get("preapprovalKey") != null)  {
				put("redirectUrl",DtsUtil.getString("host.adaptive.redirect") + DtsUtil.getString("label.preapproval_key") + get("preapprovalKey"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * get the correct set of headers for the given protocol
	 * 
	 * @return HashMap<String, String>
	 */
	public HashMap<String, String> getHeadersForProtocol() {
		
		if(protHeaders != null) {
			return protHeaders;
		}
		
		protHeaders = new HashMap<String, String>();
		String requestType = DtsUtil.getString("X-PAYPAL-REQUEST-DATA-FORMAT");
		String responseType = DtsUtil.getString("X-PAYPAL-RESPONSE-DATA-FORMAT");
		put("X-PAYPAL-REQUEST-DATA-FORMAT", requestType);
		put("X-PAYPAL-RESPONSE-DATA-FORMAT", responseType);
		
		if(requestType.equals("SOAP11") == false) {
			protHeaders.put("X-PAYPAL-REQUEST-DATA-FORMAT", requestType);
			protHeaders.put("X-PAYPAL-RESPONSE-DATA-FORMAT", responseType);
		}
		
		Iterator<String> it = DtsUtil.getHeaders().keySet().iterator();
		while (it.hasNext()) {
			String key = it.next();
			String value = DtsUtil.getHeaders().get(key);
			if (key.startsWith("header.common.")) {
				String headerKey = key.substring("header.common.".length());
				//DtsUtil.TRACE("INFO", getClass(), "Header Key: " + headerKey);
				protHeaders.put(headerKey,
						value);
			}
			if (key.startsWith("header.nvp.") && requestType.equals("NV")) {
				protHeaders.put(key.substring("header.nvp.".length()),
						value);
			}
			if (key.startsWith("header.soap.") && requestType.equals("SOAP11")) {
				protHeaders.put(key.substring("header.soap.".length()),
						value);
			}
			if (key.startsWith("header.xml.") && requestType.equals("XML")) {
				protHeaders.put(key.substring("header.xml.".length()),
						value);
			}
			if (key.startsWith("header.json.") && requestType.equals("JSON")) {
				protHeaders.put(key.substring("header.soap.".length()),
						value);
			}
		}
		putAll(protHeaders);
		return protHeaders;
	}
	
	
	private void handleResp(URLConnection uconn) throws Exception {
		StringBuffer responseBody = new StringBuffer();
		// Get the response
		InputStream is = uconn.getInputStream();
		BufferedReader rd = new BufferedReader(new InputStreamReader(is));

		String line;
		while ((line = rd.readLine()) != null) {
			responseBody.append(line);
		}
		rd.close();;
		System.err.println("Resp body: " + responseBody.toString());

	}

	/*
	 * handle the response from the execution of the request
	 */
	private void handleNvpResponse(URLConnection uconn) throws Exception {
	
			// Get the response
			StringBuffer responseBody = new StringBuffer();
			BufferedReader rd = new BufferedReader(new InputStreamReader(uconn
					.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null) {
				responseBody.append(line);
				StringTokenizer st = new StringTokenizer(line, DtsConsts.AMP);
				while (st.hasMoreTokens()) {
					String tok = st.nextToken();
					String[] nvp = tok.split(DtsConsts.EQ);
					if (nvp[0] != null && nvp[1] != null) {
						put(nvp[0], nvp[1]);
					}
				}
			}
			rd.close();
			put("responseBody", responseBody.toString());
	}
}
