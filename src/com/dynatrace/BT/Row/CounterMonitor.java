/**
  ***************************************
  * Business Transaction Row Counter
  ***************************************
  * Author: Daniel Pohanka (Dynatrace)
  * Version: 2.1.0
  * Created: 3/29/2016
  *
  * This plugin counts the number of rows displayed in a Business Transaction table or Chart table.
  * For information, please visit https://github.com/Dynatrace/Dynatrace-Business-Transaction-Row-Counter-Plugin
  **/

package com.dynatrace.BT.Row;

import com.dynatrace.diagnostics.pdk.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.*;


import javax.net.ssl.*;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.*;
import javax.xml.xpath.XPathVariableResolver;
import javax.xml.namespace.QName;

import org.apache.http.client.ClientProtocolException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;


public class CounterMonitor implements Monitor {

	private static final Logger log = Logger.getLogger(CounterMonitor.class.getName());

	// measure constants
	private static final String METRIC_GROUP = "Row Counter";
	private static final String MSR_ROW = "Rows";

	//variables
	private Collection<MonitorMeasure>  measures  = null;
	private double rowCount = 0;
	private double uniqueRows;
	private double uniqueRowNum = 0;
	//private double storeCount;
	
	private URLConnection connection;
	private URL overviewUrl;
	
	private String urlprotocol;
	private int urlport;
	private String dynaTraceURL;
	private String username;
	private String password;
	private String dashboardOption;
	private String dashboardName;
	
	private MonitorMeasure dynamicMeasure;
	private NodeList xpathNodeList;	
	private String split;
	

	@Override
	public Status setup(MonitorEnvironment env) throws Exception {
		
		log.fine("*****BEGIN PLUGIN LOGGING*****");
		log.fine("Entering setup method");
		log.fine("Entering variables from plugin.xml");
		
		//set variables
		urlprotocol = env.getConfigString("protocol");
		urlport = env.getConfigLong("httpPort").intValue();
		
		username = env.getConfigString("username");
		password = env.getConfigPassword("password");
		
		dashboardOption = env.getConfigString("dashboardOption");
		split = env.getConfigString("countChoice");
		
		//Input Check
		if (username.equals("") || password.equals("")){
			log.severe("username and password are required");
				return new Status(Status.StatusCode.ErrorInternal);
		}
		
		//Create Report Url
		if (env.getConfigString("dashboardName").equals("") || env.getConfigString("dashboardName").equals(null)){
			log.severe("Dashboard Name entry is required");
				return new Status(Status.StatusCode.ErrorInternal);
		}
		else
			dynaTraceURL = "/rest/management/reports/create/" + env.getConfigString("dashboardName") + "?type=XML&format=XML+Export";

		String timeframe = env.getConfigString("timeframeFilter").replaceAll(" ",":").toUpperCase();
		dynaTraceURL = dynaTraceURL +  "&filter=tf:OffsetTimeframe?" + timeframe;
		
		//add filter to Report URL
		if (env.getConfigBoolean("filterBoolean")==true){
			if (env.getConfigBoolean("systemProfileBoolean")==true){
				String systemProfileFilter;
				if (!(systemProfileFilter = env.getConfigString("systemProfileFilter")).equals("")){	
						dynaTraceURL = dynaTraceURL + "&source=live:" + systemProfileFilter;}
				else
					log.warning("System Profile Filter entry is empty. Plugin will continue without system profile filter");
			}
			// if(env.getConfigBoolean("agentFilterBoolean")==true){
				// String agentGroupFilter;
				// String agentPatternFilter;
				// if (env.getConfigString("agentFilter").equals("Agent Group") && !(agentGroupFilter = env.getConfigString("agentGroupFilter")).equals("")){
				// dynaTraceURL = dynaTraceURL + "&filter=ag:AgentGroups?" + agentGroupFilter;
				// }
				// else if (env.getConfigString("agentFilter").equals("Agent Pattern") && !(agentPatternFilter = env.getConfigString("agentPatternFilter")).equals("")){
					// dynaTraceURL = dynaTraceURL + "&filter=ag:AgentsByPattern?" + agentPatternFilter + "@" + env.getConfigString("agentPatternMatchType");
				// }
			// }
			if(env.getConfigBoolean("btBoolean")==true){
				String btFilter;
				if (!(btFilter = env.getConfigString("btFilter")).equals("")){	
					dynaTraceURL = dynaTraceURL + "&filter=bt:" + btFilter;}
				else
					log.warning("Business Transaction Filter entry is empty. Plugin will continue without Business Transaction filter");
			}
		}
		
		dynaTraceURL = dynaTraceURL.replaceAll(" ", "%20");
		
		//Logging
		log.fine("URL Protocol: " + urlprotocol);
		log.fine("URL Port: " + urlport);
		log.fine("dT URL: " + dynaTraceURL);
		log.fine("dashboardOption: " + dashboardOption);
		log.fine("Username: " + username);
		log.fine("Exiting setup method");
		
		return new Status(Status.StatusCode.Success);
	}

	
	@Override
	public Status execute(MonitorEnvironment env) throws MalformedURLException{
		
		log.fine("Entering execute method");
		
		log.fine("Entering URL Setup");
		overviewUrl = new URL(urlprotocol, env.getHost().getAddress(), urlport, dynaTraceURL);		
		
		log.fine("Executing URL: " + overviewUrl.toString());
		
		try {
			
			//login to dynatrace server
			log.fine("Entering username/password setup");
			String userpass = username + ":" + password;
			String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());
		
			disableCertificateValidation();
				
			//URL to grab XML file
			log.fine("Entering XML file grab");
			connection = overviewUrl.openConnection();
			connection.setRequestProperty("Authorization", basicAuth);
			connection.setConnectTimeout(50000);

			InputStream responseIS = connection.getInputStream();	
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = xmlFactory.newDocumentBuilder();
			Document xmlDoc = docBuilder.parse(responseIS);
			XPathFactory xpathFact = XPathFactory.newInstance();
			XPath xpath = xpathFact.newXPath();

			//split statement to select code to execute based on user input for count
			log.fine("Entering splitting switch statement");
			int splitSwitch = 0;
			if (split.equals("count rows"))
				splitSwitch = 1;
			if (split.equals("count unique rows"))
				splitSwitch = 2;
			if (split.equals("count instances"))
				splitSwitch = 3;
			log.finer("splitSwitch: " + splitSwitch);
			

			//checks for splitting options
			switch (splitSwitch)
			{
				
				case 1: //"Count Rows"
					if (dashboardOption.equals("Business Transaction")) //if counting from Business Transaction
						rowCount = (Double) xpath.evaluate("count(/dashboardreport/data/businesstransactionsdashlet/transactions/transaction)",xmlDoc,XPathConstants.NUMBER);
						
					else if (dashboardOption.equals("Chart")) //if counting from Chart
						rowCount = (Double) xpath.evaluate("count(/dashboardreport/data/chartdashlet/measures/measure[not(contains(@measure, 'split by'))])",xmlDoc,XPathConstants.NUMBER);

					
					log.fine("Entering collectStaticMetrics method");
					
					//assign measure
					if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_ROW)) != null) {
						for (MonitorMeasure measure : measures)
							measure.setValue(rowCount);
					}
				break;
				
				
				case 2: //"Count Unique Rows"
					
					log.fine("Entering split by Row");
					Set<String> uniqueRowSet = new HashSet<String>();  //used to store unique collector names
										
					
					if (dashboardOption.equals("Business Transaction")) //if counting Business Transaction
						xpathNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/businesstransactionsdashlet/transactions/transaction", xmlDoc, XPathConstants.NODESET);
						
					else if (dashboardOption.equals("Chart"))	//if counting from Chart
						xpathNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/chartdashlet/measures/measure[not(contains(@measure, 'split by'))]", xmlDoc, XPathConstants.NODESET);
											
					log.fine("Size of xpathNodeList: " + xpathNodeList.getLength());
					
					if (xpathNodeList.getLength() < 1)
					{
						log.fine("xpathNodeList is less than 1");
						if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_ROW)) != null) {
							for (MonitorMeasure measure : measures)
								measure.setValue(rowCount);
						}
					}
					
					//count number of unique rows
					else
					{
						for (int i = 0; i < xpathNodeList.getLength(); ++i){
							log.finer("i: " + i);
							
							if (dashboardOption.equals("Business Transaction")) //if counting from Business Transaction
								{
									log.finer("Entering split by Row - Business Transaction");
									if (xpathNodeList.item(i).getAttributes().getNamedItem("group") != null){
										String tempString = xpathNodeList.item(i).getAttributes().getNamedItem("group").toString();
										log.finer("tempString: " + tempString);
										String[] tempArray = tempString.replaceAll("\"","").replaceAll("group=","").split(";");
										log.finer("tempArray: " + tempArray[0]);
										uniqueRowSet.add(tempArray[0]);
									}
									else
										log.warning("Row splitting value is null and thus will be ignored from evaluation.");
								}
							 
							else if (dashboardOption.equals("Chart"))  //if counting from Chart
								{
									log.finer("Entering split by Row - Chart");
									if (xpathNodeList.item(i).getAttributes().getNamedItem("measure") != null){
										String tempString = xpathNodeList.item(i).getAttributes().getNamedItem("measure").toString();
										log.finer("tempString: " + tempString);
										String[] tempArray = tempString.replaceAll("\"","").replaceAll("measure=","").split(",");
										log.finer("tempArray: " + tempArray[0]);
										uniqueRowSet.add(tempArray[0]);
									}
									else
										log.warning("Row splitting value is null and thus will be ignored from evaluation.");
								}							
						}
					}

					rowCount = uniqueRowSet.size();	
					log.fine("Number of unique rows: " + uniqueRowSet.size());
					
					if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_ROW)) != null) 
						for (MonitorMeasure measure : measures)
							measure.setValue(rowCount);
				
				break;
				
				
				case 3: //"Count instances per unique row"
				
					log.fine("Entering split by Row");
					
					//used to store rows with unique measures and their values
					Set<String> uniqueRowSetCase3 = new HashSet<String>();
					
					if (dashboardOption.equals("Business Transaction")) //if counting from Business Transaction
						xpathNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/businesstransactionsdashlet/transactions/transaction", xmlDoc, XPathConstants.NODESET);
						
					else if (dashboardOption.equals("Chart"))	//if counting from Chart
						xpathNodeList = (NodeList) xpath.evaluate("/dashboardreport/data/chartdashlet/measures/measure[not(contains(@measure, 'split by'))]", xmlDoc, XPathConstants.NODESET);
						
					log.fine("Number of Unique xpathNodeList: " + xpathNodeList.getLength());
					
					//if no available metrics return 0
					if (xpathNodeList.getLength() < 1)
					{
						log.fine("xpathNodeList is less than 1");
						if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_ROW)) != null) {
							for (MonitorMeasure measure : measures)
								measure.setValue(rowCount);
						}
					}
					
					//count number of unique rows
					else
					{
						for (int i = 0; i < xpathNodeList.getLength(); ++i){
							log.finer("i: " + i);
						
							if (dashboardOption.equals("Business Transaction")) //if counting Business Transaction
								{
									log.finer("Entering split by Row - Business Transaction");
									if (xpathNodeList.item(i).getAttributes().getNamedItem("group") != null){
										String tempString = xpathNodeList.item(i).getAttributes().getNamedItem("group").toString();
										log.finer("tempString: " + tempString);
										String[] tempArray = tempString.replaceAll("\"","").replaceAll("group=","").split(";");
										log.finer("tempArray: " + tempArray[0]);
										uniqueRowSetCase3.add(tempArray[0]);
									}
									else
										log.warning("Row splitting value is null and thus will be ignored from evaluation.");	
								}
						
							else if (dashboardOption.equals("Chart"))
								{
									log.finer("Entering split by Row - Chart");
									if (xpathNodeList.item(i).getAttributes().getNamedItem("measure") != null){
										String tempString = xpathNodeList.item(i).getAttributes().getNamedItem("measure").toString();
										log.finer("tempString: " + tempString);
										String[] tempArray = tempString.replaceAll("\"","").replaceAll("measure=","").split(",");
										log.finer("tempArray: " + tempArray[0]);
										uniqueRowSetCase3.add(tempArray[0]);
									}
									else
										log.warning("Row splitting value is null and thus will be ignored from evaluation.");						
								}
						}
					}	
					
					log.fine("Number of Unique Rows: " + uniqueRowSetCase3.size());
					
					String[] tempStringArray = uniqueRowSetCase3.toArray(new String[0]);
					
					log.fine("Entering split by all Rows");
					
					//loop through array of unique rows measure sets
					for (int j = 0; j < uniqueRowSetCase3.size(); ++j){
						log.finer("Splitting for Row: " + tempStringArray[j]);
						String tempString = tempStringArray[j];
						log.finer("Splitting for tempString: " + tempString);
						dynamicMetric(env, xpath, tempString, xmlDoc);
					}	
				break;
		}	
			
		
		} catch (ClientProtocolException e) {
			log.severe("ClientProtocolException: " + e);
			return new Status(Status.StatusCode.ErrorInternal);

		} catch (IOException e) {
			log.severe("IOException: " + e);
			return new Status(Status.StatusCode.ErrorInternal);

		} catch (Exception e){
			log.severe("Exception: " + e);
			return new Status(Status.StatusCode.ErrorInternal);
		}
		
		log.fine("Exiting execute method");
		log.info("Plugin executed successfully for URL " + overviewUrl);
		log.fine("*****END PLUGIN LOGGING*****");
		
		return new Status(Status.StatusCode.Success);
	}
	
	
	
	private void dynamicMetric(MonitorEnvironment env, XPath xpath, String tempStringMeasure, Document xmlDoc) throws XPathExpressionException {
		
		log.finer("Entering dynamicMetrics method");
		
		//assign srign as a variable to use in xPath evaluate statement
		MapVariableResolver vr = new MapVariableResolver() ;
		vr.setVariable("myVar", tempStringMeasure);
		xpath.setXPathVariableResolver(vr);
		log.finer("myVar: " + vr.resolveVariable( new QName ("myVar")));
								
		//dynamic measure	
		if (dashboardOption.equals("Business Transaction")) //if counting from Business Transaction		
				uniqueRowNum = (Double) xpath.evaluate("count(/dashboardreport/data/businesstransactionsdashlet/transactions/transaction[contains(@group, $myVar)])", xmlDoc, XPathConstants.NUMBER);
						
		else if (dashboardOption.equals("Chart")) //if counting from Chart	
				uniqueRowNum = (Double) xpath.evaluate("count(/dashboardreport/data/chartdashlet/measures/measure[contains(@measure, $myVar)])", xmlDoc, XPathConstants.NUMBER);
		
		log.finer("uniqueRowNum: " + uniqueRowNum);
		log.finer("measures1: " + measures);
		
		{
			if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_ROW)) != null) {
				for (MonitorMeasure measure : measures){
					log.finer("measures2: " + uniqueRowNum);
					dynamicMeasure = env.createDynamicMeasure(measure, "unique measure", tempStringMeasure);
					dynamicMeasure.setValue(uniqueRowNum);
				}								
			}
		}		
		log.finer("Exiting dynamicMetricsCollector method");		
	}


	/**
	 * Shuts the Plugin down and frees resources. This method is called in the
	 * following cases:
	 * <ul>
	 * <li>the <tt>setup</tt> method failed</li>
	 * <li>the Plugin configuration has changed</li>
	 * <li>the execution duration of the Plugin exceeded the schedule timeout</li>
	 * <li>the schedule associated with this Plugin was removed</li>
	 * </ul>
	 *
	 * <p>
	 * The Plugin methods <tt>setup</tt>, <tt>execute</tt> and
	 * <tt>teardown</tt> are called on different threads, but they are called
	 * sequentially. This means that the execution of these methods does not
	 * overlap, they are executed one after the other.
	 *
	 * <p>
	 * Examples:
	 * <ul>
	 * <li><tt>setup</tt> (failed) -&gt; <tt>teardown</tt></li>
	 * <li><tt>execute</tt> starts, configuration changes, <tt>execute</tt>
	 * ends -&gt; <tt>teardown</tt><br>
	 * on next schedule interval: <tt>setup</tt> -&gt; <tt>execute</tt> ...</li>
	 * <li><tt>execute</tt> starts, execution duration timeout,
	 * <tt>execute</tt> stops -&gt; <tt>teardown</tt></li>
	 * <li><tt>execute</tt> starts, <tt>execute</tt> ends, schedule is
	 * removed -&gt; <tt>teardown</tt></li>
	 * </ul>
	 * Failed means that either an unhandled exception is thrown or the status
	 * returned by the method contains a non-success code.
	 *
	 *
	 * <p>
	 * All by the Plugin allocated resources should be freed in this method.
	 * Examples are opened sockets or files.
	 *
	 * @see Monitor#setup(MonitorEnvironment)
	 */	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		// TODO
	}
	
	public static void disableCertificateValidation() {
		
		log.fine("Entering disableCertificateValidation method");  
		
		// Create a trust manager that does not validate certificate chains
		  TrustManager[] trustAllCerts = new TrustManager[] { 
		    new X509TrustManager() {
		      public X509Certificate[] getAcceptedIssuers() { 
		        return new X509Certificate[0]; 
		      }
		      public void checkClientTrusted(X509Certificate[] certs, String authType) {}
		      public void checkServerTrusted(X509Certificate[] certs, String authType) {}
		  }};

		  // Ignore differences between given hostname and certificate hostname
		  HostnameVerifier hv = new HostnameVerifier() {
		    public boolean verify(String hostname, SSLSession session) { return true; }
		  };

		  // Install the all-trusting trust manager
		  try {
		    SSLContext sc = SSLContext.getInstance("SSL");
		    sc.init(null, trustAllCerts, new SecureRandom());
		    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		    HttpsURLConnection.setDefaultHostnameVerifier(hv);
		  } catch (Exception e) {}
		  
		  log.fine("Leaving disableCertificateValidation method");
	}
}

