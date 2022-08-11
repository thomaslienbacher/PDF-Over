/*
 * Copyright 2012 by A-SIT, Secure Information Technology Center Austria
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * http://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package at.asit.pdfover.gui.bku.mobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.asit.pdfover.gui.exceptions.ATrustConnectionException;
import at.asit.pdfover.gui.utils.FileUploadSource;
import at.asit.pdfover.gui.workflow.states.LocalBKUState;
import at.asit.pdfover.gui.workflow.states.MobileBKUState;
import at.asit.pdfover.signator.SLRequest;
import at.asit.pdfover.signer.pdfas.PdfAs4SigningState;

/**
 * A mobile BKU Handler
 */
public abstract class MobileBKUHandler {
	/**
	 * SLF4J Logger instance
	 **/
	static final Logger log = LoggerFactory.getLogger(MobileBKUHandler.class);

	public final MobileBKUState state;

	/**
	 * Constructor
	 * @param state the MobileBKUState
	 */
	public MobileBKUHandler(MobileBKUState state)
	{
		this.state = state;
	}

	/**
	 * Post the SL request
	 * @param mobileBKUUrl mobile BKU URL
	 * @param request SLRequest
	 * @return the response
	 * @throws IOException IO error
	 */
	public String postSLRequest(String mobileBKUUrl, SLRequest request) throws IOException {
		MobileBKUHelper.registerTrustedSocketFactory();
		HttpClient client = MobileBKUHelper.getHttpClient(getStatus());

		PostMethod post = new PostMethod(mobileBKUUrl);
		String sl_request;
		if (request.getSignatureData() != null) {
			sl_request = request.getRequest();
			if (useBase64Request())
			{
				post.addParameter("XMLRequest", sl_request);
			} else {
				StringPart xmlpart = new StringPart(
						"XMLRequest", sl_request, "UTF-8");

				FilePart filepart = new FilePart("fileupload",
						new FileUploadSource(request.getSignatureData()),
						"application/pdf", "UTF-8");

				Part[] parts = { xmlpart, filepart };

				post.setRequestEntity(new MultipartRequestEntity(parts, post
						.getParams()));
			}
		} else {
			sl_request = request.getRequest();
			post.addParameter("XMLRequest", sl_request);
		}
		log.trace("SL Request: " + sl_request);

		state.status.baseURL = MobileBKUHelper.stripQueryString(mobileBKUUrl);

		return executePost(client, post);
	}

	/**
	 * Handle the response to the SL request post
	 * @param responseData response data
	 * @throws Exception Error during handling
	 */
	public abstract void handleSLRequestResponse(String responseData) throws Exception;


	/**
	 * Post the credentials
	 * @return the response
	 * @throws Exception Error during posting
	 */
	public abstract String postCredentials() throws Exception;

	/**
	 * Handle the response to credentials post
	 * @param responseData response data
	 * @throws Exception Error during handling
	 */
	public abstract void handleCredentialsResponse(String responseData) throws Exception;

	/**
	 * Post the TAN
	 * @return the response
	 * @throws Exception Error during posting
	 */
	public abstract String postTAN() throws Exception;

	/**
	 * Handle the response to TAN post
	 * @param responseData response data
	 * @throws Exception Error during handling
	 */
	public abstract void handleTANResponse(String responseData) throws Exception;

	/**
	 * Get the MobileBKUStatus
	 * @return the MobileBKUStatus
	 */
	protected MobileBKUStatus getStatus() {
		return this.state.status;
	}

	/**
	 * Get the SigningState
	 * @return the SigningState
	 */
	protected PdfAs4SigningState getSigningState() {
		return state.getSigningState();
	}

	/**
	 * Whether to use a Base64 request
	 * @return true if base64 request shall be used
	 */
	public abstract boolean useBase64Request();

	/**
	 * Execute a post to the mobile BKU, following redirects
	 * @param client the HttpClient
	 * @param post the PostMethod
	 * @return the response
	 * @throws IOException IO error
	 */
	protected String executePost(HttpClient client, PostMethod post) throws IOException {
		if (log.isDebugEnabled()) {
			String req;
			if (post.getRequestEntity().getContentLength() < 1024) {
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				post.getRequestEntity().writeRequest(os);
				req = os.toString();
				if (req.contains("passwort="))
					req = req.replaceAll("passwort=[^&]*", "passwort=******");
				if (req.contains(":pwd="))
					req = req.replaceAll(":pwd=[^&]*", ":pwd=******");
				os.close();
			} else {
				req = post.getRequestEntity().getContentLength() + " bytes";
			}
			log.debug("Posting to " + post.getURI() + ": " + req);
		}
		int returnCode = client.executeMethod(post);

		String redirectLocation = null;
		GetMethod get = null;


		String responseData = null;

		String server = null;

		// Follow redirects
		do {
			// check return code
			if (returnCode == HttpStatus.SC_MOVED_TEMPORARILY ||
				returnCode == HttpStatus.SC_MOVED_PERMANENTLY) {

				Header locationHeader = post.getResponseHeader("location");
				if (locationHeader != null) {
					redirectLocation = locationHeader.getValue();
				} else {
					throw new IOException(
							"Got HTTP 302 but no location to follow!");
				}
			} else if (returnCode == HttpStatus.SC_OK) {
				if (get != null) {
					responseData = get.getResponseBodyAsString();
					Header serverHeader = get.getResponseHeader(
							LocalBKUState.BKU_RESPONSE_HEADER_SERVER);
					if (serverHeader != null)
						server = serverHeader.getValue();
				} else {
					responseData = post.getResponseBodyAsString();

					Header serverHeader = post.getResponseHeader(
							LocalBKUState.BKU_RESPONSE_HEADER_SERVER);
					if (serverHeader != null)
						server = serverHeader.getValue();
				}
				redirectLocation = null;
				String p = "<meta [^>]*http-equiv=\"refresh\" [^>]*content=\"([^\"]*)\"";
				Pattern pat = Pattern.compile(p);
				Matcher m = pat.matcher(responseData);
				if (m.find()) {
					String content = m.group(1);
					int start = content.indexOf("URL=");
					if (start != -1) {
						start += 9;
						redirectLocation  = content.substring(start, content.length() - 5);
					}
				}
			} else {
				throw new HttpException(
						HttpStatus.getStatusText(returnCode));
			}

			if (redirectLocation != null) {
				redirectLocation = MobileBKUHelper.getQualifiedURL(redirectLocation, new URL(post.getURI().toString()));
				redirectLocation = getStatus().ensureSessionID(redirectLocation);
				log.debug("Redirected to " + redirectLocation);
				get = new GetMethod(redirectLocation);
				get.setFollowRedirects(true);
				returnCode = client.executeMethod(get);
			}
		} while (redirectLocation != null);

		getStatus().server = server;
		if (server != null)
			log.debug("Server: " + server);

		return responseData;
	}

	/**
	 * Execute a get from the mobile BKU, following redirects
	 * @param client the HttpClient
	 * @param get the GetMethod
	 * @return the response
	 * @throws IOException IO error
	 */
	protected String executeGet(HttpClient client, GetMethod get) throws IOException {
		log.debug("Getting " + get.getURI());

		int returnCode = client.executeMethod(get);

		String redirectLocation = null;

		GetMethod get2 = null;

		String responseData = null;

		String server = null;

		// Follow redirects
		do {
			// check return code
			if (returnCode == HttpStatus.SC_MOVED_TEMPORARILY ||
				returnCode == HttpStatus.SC_MOVED_PERMANENTLY) {

				Header locationHeader = get.getResponseHeader("location");
				if (locationHeader != null) {
					redirectLocation = locationHeader.getValue();
				} else {
					throw new IOException(
							"Got HTTP 302 but no location to follow!");
				}
			} else if (returnCode == HttpStatus.SC_OK) {
				if (get2 != null) {
					responseData = get2.getResponseBodyAsString();
					Header serverHeader = get2.getResponseHeader(
							LocalBKUState.BKU_RESPONSE_HEADER_SERVER);
					if (serverHeader != null)
						server = serverHeader.getValue();
				} else {
					responseData = get.getResponseBodyAsString();

					Header serverHeader = get.getResponseHeader(
							LocalBKUState.BKU_RESPONSE_HEADER_SERVER);
					if (serverHeader != null)
						server = serverHeader.getValue();
				}
				redirectLocation = null;
				String p = "<meta [^>]*http-equiv=\"refresh\" [^>]*content=\"([^\"]*)\"";
				Pattern pat = Pattern.compile(p);
				Matcher m = pat.matcher(responseData);
				if (m.find()) {
					String content = m.group(1);
					int start = content.indexOf("URL=");
					if (start != -1) {
						start += 9;
						redirectLocation  = content.substring(start, content.length() - 5);
					}
				}
			} else {
				throw new HttpException(
						HttpStatus.getStatusText(returnCode));
			}

			if (redirectLocation != null) {
				redirectLocation = MobileBKUHelper.getQualifiedURL(redirectLocation, new URL(get.getURI().toString()));
				redirectLocation = getStatus().ensureSessionID(redirectLocation);
				log.debug("Redirected to " + redirectLocation);
				get2 = new GetMethod(redirectLocation);
				get2.setFollowRedirects(true);
				returnCode = client.executeMethod(get2);
			}
		} while (redirectLocation != null);

		getStatus().server = server;
		if (server != null)
			log.debug("Server: " + server);

		return responseData;
	}

	/**
	 * @param responseData
	 */
	public abstract boolean handlePolling() throws ATrustConnectionException;
}
