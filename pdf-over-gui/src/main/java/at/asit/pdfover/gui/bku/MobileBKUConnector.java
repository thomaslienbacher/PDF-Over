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
package at.asit.pdfover.gui.bku;

import java.util.Base64;

// Imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import at.asit.pdfover.commons.Constants;
import at.asit.pdfover.gui.bku.mobile.ATrustHandler;
import at.asit.pdfover.gui.bku.mobile.ATrustStatus;
import at.asit.pdfover.gui.bku.mobile.MobileBKUHandler;
import at.asit.pdfover.gui.bku.mobile.MobileBKUStatus;
import at.asit.pdfover.gui.workflow.states.MobileBKUState;
import at.asit.pdfover.signator.BkuSlConnector;
import at.asit.pdfover.signator.SLRequest;
import at.asit.pdfover.signator.SLResponse;
import at.asit.pdfover.signator.SignatureException;
import at.asit.pdfover.signer.pdfas.PdfAs4SigningState;
import at.asit.webauthn.PublicKeyCredential;
import at.asit.webauthn.PublicKeyCredentialRequestOptions;
import at.asit.webauthn.WebAuthN;
import at.asit.webauthn.exceptions.WebAuthNOperationFailed;
import at.asit.webauthn.responsefields.AuthenticatorAssertionResponse;

/**
 *
 */
public class MobileBKUConnector implements BkuSlConnector {
	/**
	 * SLF4J Logger instance
	 **/
	private static final Logger log = LoggerFactory.getLogger(MobileBKUConnector.class);

	private MobileBKUState state;

	/**
	 *
	 * @param state
	 */
	public MobileBKUConnector(MobileBKUState state) {
		this.state = state;
	}

	/** (non-Javadoc)
	 * @see at.asit.pdfover.signator.BkuSlConnector#handleSLRequest(java.lang.String)
	 */
	@Override
	public SLResponse handleSLRequest(SLRequest request) throws SignatureException {
		PdfAs4SigningState signingState = this.state.getSigningState();
		signingState.signatureRequest = request;

		MobileBKUHandler handler = this.state.handler;

		do {
			// Post SL Request
			try {
				String responseData = handler.postSLRequest(Constants.MOBILE_BKU_URL, request);

				// Now we have received some data lets check it:
				log.trace("Response from mobile BKU: " + responseData);

				handler.handleSLRequestResponse(responseData);
			} catch (Exception ex) {
				log.error("Error in PostSLRequestThread", ex);
				this.state.threadException = ex;
				this.state.displayError(ex);
				throw new SignatureException(ex);
			}

			do {
				// Check if credentials are available, get them from user if not
				this.state.checkCredentials();

				if ("cancel".equals(this.state.status.errorMessage))
					throw new SignatureException(new IllegalStateException());

				// Post credentials
				try {
					String responseData = handler.postCredentials();

					if (responseData.contains("undecided.aspx?sid=")) {
						// handle polling
						this.state.showOpenAppMessageWithSMSandCancel();

						if (((ATrustStatus) this.state.status).isSMSTan) {
							ATrustHandler aHandler = (ATrustHandler) handler;
							String response = aHandler.postSMSRequest();
							aHandler.handleCredentialsResponse(response);
						} else if (consumeCancelError()) {
							throw new SignatureException(new IllegalStateException());
						}
					} else {

					    // Now we have received some data lets check it:
						log.trace("Response from mobile BKU: " + responseData);
						handler.handleCredentialsResponse(responseData);
					}

				} catch (Exception ex) {
					log.error("Error in PostCredentialsThread", ex);
					this.state.threadException = new IllegalStateException();
					throw new SignatureException(new IllegalStateException());
				}
			} while(this.state.status.errorMessage != null);

			// Check if response is already available
			if (signingState.signatureResponse != null) {
				SLResponse response = signingState.signatureResponse;
				signingState.signatureResponse = null;
				return response;
			}

			do {
				MobileBKUStatus status = this.state.status;
				boolean enterTAN = true;
				String responseData = null;
				if (status instanceof ATrustStatus) {
					ATrustStatus aStatus = (ATrustStatus) status;
					ATrustHandler aHandler = (ATrustHandler) handler;
					if (aStatus.fido2OptionAvailable && (aStatus.fido2FormOptions == null)) {
						try {
							handler.handleCredentialsResponse(aHandler.postFIDO2Request());
						} catch (Exception ex) {
							log.error("Error in PostCredentialsThread", ex);
							this.state.threadException = ex;
							throw new SignatureException(ex);
						}
					}
					if (aStatus.fido2FormOptions != null) {
						log.info("Fido2 credentials GET!");
						if (WebAuthN.isAvailable())
						{
							log.info("Authenticating with WebAuthn!");
							enterTAN = false;
							try {
								PublicKeyCredential<AuthenticatorAssertionResponse> credential = 
									PublicKeyCredentialRequestOptions.FromJSONString(aStatus.fido2FormOptions.get(aStatus.fido2OptionsKey)).get("https://service.a-trust.at");
								
								Base64.Encoder base64 = Base64.getEncoder();
								JsonObject aTrustCredential = new JsonObject();
								aTrustCredential.addProperty("id", credential.id);
								aTrustCredential.addProperty("rawId", base64.encodeToString(credential.rawId));
								aTrustCredential.addProperty("type", credential.type);
								aTrustCredential.add("extensions", new JsonObject()); // TODO fix getClientExtensionResults() in library

								JsonObject aTrustCredentialResponse = new JsonObject();
								aTrustCredential.add("response", aTrustCredentialResponse);
								aTrustCredentialResponse.addProperty("authenticatorData", base64.encodeToString(credential.response.authenticatorData));
								aTrustCredentialResponse.addProperty("clientDataJson", base64.encodeToString(credential.response.clientDataJSON));
								aTrustCredentialResponse.addProperty("signature", base64.encodeToString(credential.response.signature));
								if (credential.response.userHandle != null)
									aTrustCredentialResponse.addProperty("userHandle", base64.encodeToString(credential.response.userHandle));
								else
									aTrustCredentialResponse.add("userHandle", JsonNull.INSTANCE);

								aStatus.fido2FormOptions.put(aStatus.fido2ResultKey, aTrustCredential.toString());
								handler.handleTANResponse(aHandler.postFIDO2Result()); // TODO dedicated response
							} catch (WebAuthNOperationFailed e) {
								log.error("WebAuthN failed", e);
							} catch (Exception e) {
								log.error("generic failure", e);
							}
						}
					}
					if (aStatus.qrCodeURL != null) {
						this.state.showQR();
						if ("cancel".equals(this.state.status.errorMessage))
							throw new SignatureException(new IllegalStateException());
						if (aStatus.qrCodeURL == null) {
							try {
								String response = aHandler.postSMSRequest();
								log.trace("Response from mobile BKU: " + response);
								handler.handleCredentialsResponse(response);
							} catch (Exception ex) {
								log.error("Error in PostCredentialsThread", ex);
								this.state.threadException = new IllegalStateException();
								throw new SignatureException(new IllegalStateException());
							}
						} else {
							enterTAN = false;
						}
					}
					if (enterTAN && !aStatus.tanField) {
						try {

							this.state.showFingerPrintInformation();
							if ("cancel".equals(this.state.status.errorMessage))
								throw new SignatureException(new IllegalStateException());
						} catch (Exception ex) {
							log.error("Error in PostCredentialsThread", ex);
							this.state.threadException = new IllegalStateException();
							//this.state.displayError(ex);
							throw new SignatureException(new IllegalStateException());
						}

						if (this.state.getSMSStatus()) {
							String response;
							try {
								response = aHandler.postSMSRequest();
								handler.handleCredentialsResponse(response);
							} catch (Exception e) {
								log.error("Error in PostCredentialsThread", e);
								this.state.threadException = e;
								this.state.displayError(e);
								throw new SignatureException(e);
							}
						}
						else {
							enterTAN = false;
						}
					}
				}

				if (enterTAN) {
					// Get TAN
					this.state.checkTAN();

					if ("cancel".equals(this.state.status.errorMessage))
						throw new SignatureException(new IllegalStateException());

					// Post TAN
					try {
						responseData = handler.postTAN();
						log.trace("Response from mobile BKU: " + responseData);

						// Now we have received some data lets check it:
						handler.handleTANResponse(responseData);
					} catch (Exception ex) {
						log.error("Error in PostTanThread", ex);
						this.state.threadException = ex;
						this.state.displayError(ex);
						throw new SignatureException(ex);
					}
				}
			} while (this.state.status.errorMessage != null);
			if (this.state.status.tanTries == -1)
				throw new SignatureException(new IllegalStateException());
		} while (this.state.status.tanTries == -2);

		return signingState.signatureResponse;
	}

	private boolean consumeCancelError() {

		if (this.state.status instanceof ATrustStatus) {
			if ("cancel".equals(this.state.status.errorMessage)) {
					this.state.status.errorMessage = null;
					return true;
			}
		}
		return false;
	}

}
