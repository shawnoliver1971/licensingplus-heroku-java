package core.sfdc;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import core.sfdc.responses.OAuthResponse;
import core.sfdc.responses.QueryResponseWrapper;
import core.sfdc.responses.QueryResponse;

public class SalesforceRestClient {
	
	private static final String API_VERSION = "v37.0";
    private static final String SALESFORCE_ENDPOINT = "/services/data/%s/%s";
    
    private final String CLIENT_SECRET;
	private final String CLIENT_KEY;
	private final String USERNAME;
	private final String PASSWORD;
	private final boolean IS_SANDBOX;	
    
	private String instanceUrl;
	private String sessionId;
	private boolean connected = false;
	
	
	public SalesforceRestClient(String clientSecret, String clientKey, String username, String password, boolean isSandbox) {
		this.CLIENT_KEY = clientKey;
		this.CLIENT_SECRET = clientSecret;
		this.USERNAME = username;
		this.PASSWORD = password;
		this.IS_SANDBOX = isSandbox;		
		connect();
	}
	
	private void connect() {
		SalesforceOAuth oauth = new SalesforceOAuth();
		OAuthResponse oauthResponse = oauth.connect(CLIENT_SECRET, CLIENT_KEY, USERNAME, PASSWORD, IS_SANDBOX, true);
		if (oauthResponse!=null) {
			this.connected = true;
			this.sessionId = oauthResponse.getAccess_token();
			this.instanceUrl = oauthResponse.getInstance_url();	
		} else {
			this.connected = false;
		}
	}
	
	private HttpEntity<String> getRequestEntity(String requestBody, MediaType contentType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		headers.set("Authorization", "Bearer " + this.sessionId);
		HttpEntity<String> requestEntity = null;
		if (requestBody!=null) {
			requestEntity = new HttpEntity<String>(requestBody, headers);
		} else {
			requestEntity = new HttpEntity<String>(headers);
		}
		
		return requestEntity;
	}
	
	private <T> QueryResponseWrapper<T> query(String queryStr, boolean queryAll, Class<T> responseType) throws Exception {
		QueryResponseWrapper<T> responseWrapper = new QueryResponseWrapper<T>();
		if (connected) {
			try {
				RestTemplate restTemplate = new RestTemplate();		
				Map<String, String> queryParameters = new HashMap<String, String>();
				queryParameters.put("q", queryStr);		
				String endpoint = this.instanceUrl + String.format(SALESFORCE_ENDPOINT+"/?q={q}", API_VERSION, "query");	
				ResponseEntity<String> responseEntity = restTemplate.exchange(endpoint, HttpMethod.GET, getRequestEntity(null, MediaType.APPLICATION_FORM_URLENCODED), String.class, queryParameters);	
		        ObjectMapper mapper = new ObjectMapper();
		        JavaType responseJavaType = mapper.getTypeFactory().constructParametrizedType(QueryResponse.class, QueryResponse.class, responseType);
		        String body = responseEntity.getBody();
		        if (body!=null) {
		        	QueryResponse<T> response = mapper.readValue(body, responseJavaType);
					responseWrapper.addRecords(response.getRecords());
					if (queryAll) {
						boolean isDone = response.getDone();
						// add support for query more
						while (!isDone) {
							System.out.println("[SalesforceRestClient] - query more - url " + this.instanceUrl + response.getNextRecordsUrl());
							responseEntity = restTemplate.exchange(this.instanceUrl + response.getNextRecordsUrl(), HttpMethod.GET, getRequestEntity(null, MediaType.APPLICATION_FORM_URLENCODED), String.class);
							body = responseEntity.getBody();
					        response = mapper.readValue(body, responseJavaType);
							isDone = response.getDone();			
							responseWrapper.addRecords(response.getRecords());
						}	
					}
		        }		        
			} catch (HttpClientErrorException httpException) {
	            if(httpException.getStatusCode() == HttpStatus.UNAUTHORIZED) {
	                System.out.println("[SalesforceService] - query - session expired retry connect - \n " + httpException.getResponseBodyAsString());
	                connect();
	                query(queryStr, queryAll, responseType);
	            } else {
	            	System.out.println("[SalesforceRestClient] - query - request failed during calling salesforce - " + httpException.getResponseBodyAsString());
	    			throw httpException;
	            }
	        } catch(Exception e) {
	        	System.out.println("[SalesforceRestClient] - query - exception during calling salesforce - " + e);
    			throw e;
	        }
			
		} else {
			System.out.println("[SalesforceRestClient] - query - no connection to Salesforce");		
			throw new Exception("Failed to connect to salesforce");
		}
		return responseWrapper;
	}
	
	public boolean isConnected() {
		return connected;
	}
	
	public <T> T apexRest(String endpoint, HttpMethod method, String requestBody, Class<T> responseType) throws Exception {
		T response = null;
		if (connected) {
			try {
				RestTemplate restTemplate = new RestTemplate();
				endpoint = this.instanceUrl + "/services/apexrest/" + endpoint;
			    ResponseEntity<T> responseEntity = restTemplate.exchange(endpoint, method, getRequestEntity(requestBody, MediaType.APPLICATION_JSON), responseType);
			    response = responseEntity.getBody();
			} catch (HttpClientErrorException httpException) {
	            if(httpException.getStatusCode() == HttpStatus.UNAUTHORIZED) {
	                System.out.println("[SalesforceService] - apexrest - session expired retry connect - \n " + httpException.getResponseBodyAsString());
	                connect();
	                apexRest(endpoint, method, requestBody, responseType);
	            } else {
	            	System.out.println("[SalesforceRestClient] - apexRest - request failed during calling salesforce - " + httpException.getResponseBodyAsString());
	    			throw httpException;
	            }
	        } catch(Exception e) {
	        	System.out.println("[SalesforceRestClient] - apexRest - exception during calling salesforce - " + e);
    			throw e;
	        } 		
		} else {
			System.out.println("[SalesforceRestClient] - apexRest - no connection to Salesforce");
			throw new Exception("Failed to connect to salesforce");
		}
		
	    return response;
	}
	
	public <T> QueryResponseWrapper<T> query(String queryStr, Class<T> responseType) throws Exception {
		return query(queryStr, false, responseType);
	}
	
	public <T> QueryResponseWrapper<T> queryAll(String queryStr, Class<T> responseType) throws Exception {
		return query(queryStr, true, responseType);
	}
}
