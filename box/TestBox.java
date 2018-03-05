package com.box;

import java.io.IOException;

import org.apache.http.HttpHeaders;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class TestBox {

	public static final String EVENT_URL = "https://api.box.com/2.0/events";
	//public static final String authorizationCode = "LNneZovD9lFxKRb3kpwGsNhCvd5i8ojv";
	public static final String AUTHORIZATION = "Authorization";
	public static final String BEARER = "Bearer ";
	public static final String RECONNECT = "reconnect";

	/**
	 * This is the basic LongPoll implementation of the BOX UserEvents api
	 * @param args - AuthorizationCode
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static void main(String[] args)throws ClientProtocolException, IOException {
		
		String authorizationCode = null;
		if(args.length >0)
			authorizationCode= args[0];
		else
		{
			System.err.println("Usage: TestBox <<authorizationCode>>");
			return;
		}
		CloseableHttpClient client = HttpClients.custom().build();

		while (true) {
			// Get Last known Stream Position - based on the Authorization Code
			Long nextStreamPosition = getStreamPosition(client, authorizationCode);
			if(nextStreamPosition == 0)
			{
				System.err.println("Stream position 0");
				return;
			}

			// GET long poll URL
			String longPollURL = getLongPollUrl(client, authorizationCode);
			if(longPollURL == null)
			{
				System.err.println("Long poll URL is null");
				return;
			}
			

			// Invoke event API - using LongPoll URL and last known stream position
			String listenToEventURL = longPollURL + "&stream_position=" + nextStreamPosition;
			System.out.println("realtime url:" + listenToEventURL);
			System.out.println("long polling");
			HttpUriRequest listenToStreamRequest = RequestBuilder.get().setUri(listenToEventURL)
					.setHeader(AUTHORIZATION, BEARER + authorizationCode).build();
			CloseableHttpResponse listenToStreamResponse = client.execute(listenToStreamRequest);
			String listenToStreamResponseJSON = EntityUtils.toString(listenToStreamResponse.getEntity());
			if(listenToStreamResponseJSON == null || listenToStreamResponseJSON.length()==0)
			{
				System.err.println("listenToStreamResponseJSON is null or blank");
				return;
			}
			
			JSONObject listenToStreamObj = new JSONObject(listenToStreamResponseJSON);
			String messageStr = listenToStreamObj.getString("message");
			System.out.println(messageStr);

			// Donot fetch events - if connection lost - reconnect
			if ("new_change".equals(messageStr)) {
				// Fetching Events Details
				fetchEvents(client, nextStreamPosition, authorizationCode);
			}

			// close client and response
			listenToStreamResponse.close();

		}
	}
	
	
	/**
	 * Get Stream position
	 * @param client
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private static long getStreamPosition(CloseableHttpClient client, String authorizationCode)throws ClientProtocolException, IOException
	{
		HttpUriRequest eventRequest = RequestBuilder.get().setUri(EVENT_URL + "?stream_position=now")
				.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.setHeader(AUTHORIZATION, BEARER + authorizationCode).build();
		CloseableHttpResponse eventResponse = client.execute(eventRequest);

		String eventResponseJson = EntityUtils.toString(eventResponse.getEntity());
		if(eventResponseJson == null || eventResponseJson.length()==0)
		{
			System.err.println("eventResponseJson is null or blank");
			return 0;
		}
		//System.out.println(eventResponseJson);
		JSONObject eventResponseJSONObj = new JSONObject(eventResponseJson);

		eventResponse.close();

		return eventResponseJSONObj.getLong("next_stream_position");
		

	}
	
	
	/**
	 * Get long URL
	 * @param client
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private static String getLongPollUrl(CloseableHttpClient client, String authorizationCode)throws ClientProtocolException, IOException
	{
		HttpUriRequest longPollRequest = RequestBuilder.options().setUri(EVENT_URL)
				.setHeader(AUTHORIZATION, BEARER + authorizationCode).build();
		CloseableHttpResponse longPollResponse = client.execute(longPollRequest);

		String longPollJson = EntityUtils.toString(longPollResponse.getEntity());
		if(longPollJson == null || longPollJson.length()==0)
		{
			System.err.println("longPollJson is null or blank");
			return null;
		}
		JSONObject longPollJSONObj = new JSONObject(longPollJson);
		JSONArray longPollArray = longPollJSONObj.getJSONArray("entries");
		
		String longPollUri = null;
		for (int i = 0; i < longPollArray.length(); i++) {
			longPollUri = longPollArray.getJSONObject(i).getString("url");
		}
		
		longPollResponse.close();
		return longPollUri;
	}
	
	/**
	 * Find the event details
	 * @param client
	 * @param nextStreamPosition
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private static void fetchEvents(CloseableHttpClient client, long nextStreamPosition, String authorizationCode) throws ClientProtocolException, IOException
	{
		System.out.println("fetching events");
		HttpUriRequest eventDetailsRequest = RequestBuilder.get()
				.setUri(EVENT_URL + "?stream_position=" + nextStreamPosition)
				.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.setHeader(AUTHORIZATION, BEARER + authorizationCode).build();
		CloseableHttpResponse eventDetailsResponse = client.execute(eventDetailsRequest);

		String eventDetailsResponseJson = EntityUtils.toString(eventDetailsResponse.getEntity());
		if(eventDetailsResponseJson == null || eventDetailsResponseJson.length()==0)
		{
			System.err.println("eventDetailsResponseJson is null or blank - continue");
			return;
		}
		
		//System.out.println(eventDetailsResponseJson);
		JSONObject eventDetailsObj = new JSONObject(eventDetailsResponseJson);

		JSONArray eventDetailsArray = eventDetailsObj.getJSONArray("entries");
		for (int i = 0; i < eventDetailsArray.length(); i++) {
			String eventId = eventDetailsArray.getJSONObject(i).getString("event_id");
			String event_type = eventDetailsArray.getJSONObject(i).getString("event_type");
			System.out.println(eventId + "|" + event_type);
		}
		
		eventDetailsResponse.close();
		

	}

}
