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
package at.asit.pdfover.gui.workflow;

// Imports
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.graphics.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.asit.pdfover.gui.Constants;
import at.asit.pdfover.gui.exceptions.InvalidEmblemFile;
import at.asit.pdfover.gui.exceptions.InvalidNumberException;
import at.asit.pdfover.gui.exceptions.InvalidPortException;
import at.asit.pdfover.gui.utils.LocaleSerializer;
import at.asit.pdfover.gui.utils.Messages;
import at.asit.pdfover.signator.BKUs;
import at.asit.pdfover.signator.SignaturePosition;

/**
 * Implementation of the configuration provider and manipulator
 */
public class ConfigProviderImpl implements ConfigProvider, ConfigManipulator,
		ConfigOverlayManipulator, PersistentConfigProvider {
	/**
	 * SLF4J Logger instance
	 **/
	private static final Logger log = LoggerFactory
			.getLogger(ConfigProviderImpl.class);

	/**
	 * An empty property entry
	 */
	public static final String STRING_EMPTY = ""; //$NON-NLS-1$

	private String configurationFile = Constants.DEFAULT_CONFIG_FILENAME;

	private ConfigurationContainer configuration;

	private ConfigurationContainer configurationOverlay;

	/**
	 * Constructor
	 */
	public ConfigProviderImpl() {
		this.configuration = new ConfigurationContainerImpl();
		this.configurationOverlay = new ConfigurationContainerImpl();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.asit.pdfover.gui.workflow.ConfigProvider#loadConfiguration(java.io
	 * .InputStream)
	 */
	@Override
	public void loadConfiguration(InputStream configSource) throws IOException {

		Properties config = new Properties();

		config.load(configSource);

		// Set Emblem
		this.setDefaultEmblem(config
				.getProperty(Constants.CFG_EMBLEM));

		// Set Mobile Phone Number
		this.setDefaultMobileNumber(config
				.getProperty(Constants.CFG_MOBILE_NUMBER));

		// Set signature note
		this.setSignatureNote(config
				.getProperty(Constants.CFG_SIGNATURE_NOTE));

		// Set Proxy Host
		this.setProxyHost(config
				.getProperty(Constants.CFG_PROXY_HOST));

		// Set Output Folder
		this.setDefaultOutputFolder(config
				.getProperty(Constants.CFG_OUTPUT_FOLDER));

		String localString = config.getProperty(Constants.CFG_LOCALE);
		
		Locale targetLocale = LocaleSerializer.parseFromString(localString);
		if(targetLocale != null) {
			this.setLocale(targetLocale);
		}
		
		String signlocalString = config.getProperty(Constants.CFG_SIGN_LOCALE);
		
		Locale signtargetLocale = LocaleSerializer.parseFromString(signlocalString);
		if(signtargetLocale != null) {
			this.setSignLocale(signtargetLocale);
		}
 		
		String bkuUrl = config
				.getProperty(Constants.CFG_MOBILE_BKU_URL);
		
		if (bkuUrl != null && !bkuUrl.isEmpty()) {
			this.configuration.setMobileBKUURL(bkuUrl);
		}

		// Set Proxy Port
		String proxyPortString = config
				.getProperty(Constants.CFG_PROXY_PORT);

		if (proxyPortString != null && !proxyPortString.trim().isEmpty()) {
			int port = Integer.parseInt(proxyPortString);

			if (port > 0 && port <= 0xFFFF) {
				this.setProxyPort(port);
			} else {
				log.warn("Proxy port is out of range!: " + port); //$NON-NLS-1$
			}
		}

		// Set Default BKU
		String bkuString = config.getProperty(Constants.CFG_BKU);

		BKUs defaultBKU = BKUs.NONE;

		try {
			defaultBKU = BKUs.valueOf(bkuString);
		} catch (IllegalArgumentException ex) {
			log.error("Invalid BKU config value " + bkuString + " using none!"); //$NON-NLS-1$ //$NON-NLS-2$
			defaultBKU = BKUs.NONE;
		} catch (NullPointerException ex) {
			log.error("Invalid BKU config value " + bkuString + " using none!"); //$NON-NLS-1$ //$NON-NLS-2$
			defaultBKU = BKUs.NONE;
		}

		this.setDefaultBKU(defaultBKU);

		// Set Signature placeholder transparency
		int transparency = Constants.DEFAULT_SIGNATURE_PLACEHOLDER_TRANSPARENCY;
		try {
			transparency = Integer
					.parseInt(config
							.getProperty(Constants.CFG_SIGNATURE_PLACEHOLDER_TRANSPARENCY));
		} catch (NumberFormatException e) {
			log.debug("Couldn't parse placeholder transparency", e); //$NON-NLS-1$
			// ignore parsing exception
		}
		this.setPlaceholderTransparency(transparency);

		// Set MainWindow size
		int width = Constants.DEFAULT_MAINWINDOW_WIDTH;
		int height = Constants.DEFAULT_MAINWINDOW_HEIGHT;
		String size = config.getProperty(Constants.CFG_MAINWINDOW_SIZE);
		parse: {
			if (size == null)
				break parse;
			int pos = size.indexOf(',');
			if (pos <= 0)
				break parse;

			try {
				width = Integer.parseInt(size.substring(0, pos).trim());
				height = Integer.parseInt(size.substring(pos + 1).trim());
			} catch (NumberFormatException e) {
				log.debug("Couldn't parse main window size", e); //$NON-NLS-1$
				// ignore parsing exception
			}
		}
		this.configuration.setMainWindowSize(new Point(width, height));

		// Set Signature Position
		String signaturePosition = config
				.getProperty(Constants.CFG_SIGNATURE_POSITION);

		SignaturePosition position = null;

		if (signaturePosition != null && !signaturePosition.trim().isEmpty()) {

			signaturePosition = signaturePosition.trim().toLowerCase();

			Pattern pattern = Pattern.compile(SIGN_POS_REGEX);

			Matcher matcher = pattern.matcher(signaturePosition);

			if (matcher.matches()) {
				if (matcher.groupCount() == 8) {
					if (matcher.group(1) != null) {
						// we have format: x=..;y=..;p=...
						try {
							// group 2 = x value
							float x = Float.parseFloat(matcher.group(2));

							// group 3 = y value
							float y = Float.parseFloat(matcher.group(3));

							// group 4 = p value
							int p = Integer.parseInt(matcher.group(3));

							position = new SignaturePosition(x, y, p);
						} catch (NumberFormatException ex) {
							log.error(
									"Signature Position read from config failed: Not a valid number", ex); //$NON-NLS-1$
						}
					} else if (matcher.group(5) != null) {
						// we have format auto
						position = new SignaturePosition();
					} else if (matcher.group(6) != null) {
						// we have format x=...;y=...;
						// group 7 = x value
						float x = Float.parseFloat(matcher.group(7));

						// group 8 = y value
						float y = Float.parseFloat(matcher.group(8));

						position = new SignaturePosition(x, y);
					}
				} else {
					log.error("Signature Position read from config failed: wrong group Count!"); //$NON-NLS-1$
				}
			} else {
				log.error("Signature Position read from config failed: not matching string"); //$NON-NLS-1$
			}

		}

		this.setDefaultSignaturePosition(position);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.asit.pdfover.gui.workflow.ConfigManipulator#saveCurrentConfiguration()
	 */
	@Override
	public void saveCurrentConfiguration() throws IOException {
		String filename = this.getConfigurationFile();

		File configFile = new File(this.getConfigurationDirectory()
				+ File.separator + filename);

		Properties props = new Properties();
		props.clear();

		props.setProperty(Constants.CFG_BKU, this.getDefaultBKUPersistent().toString());
		props.setProperty(Constants.CFG_PROXY_HOST, this.getProxyHostPersistent());
		props.setProperty(Constants.CFG_PROXY_PORT,
				Integer.toString(this.getProxyPortPersistent()));
		props.setProperty(Constants.CFG_EMBLEM, this.getDefaultEmblemPersistent());
		props.setProperty(Constants.CFG_SIGNATURE_NOTE, this.getSignatureNote());
		props.setProperty(Constants.CFG_MOBILE_NUMBER, this.getDefaultMobileNumberPersistent());
		props.setProperty(Constants.CFG_OUTPUT_FOLDER, this.getDefaultOutputFolderPersistent());
		props.setProperty(Constants.CFG_SIGNATURE_PLACEHOLDER_TRANSPARENCY,
				Integer.toString(this.getPlaceholderTransparency()));

		Point size = this.configuration.getMainWindowSize();
		props.setProperty(Constants.CFG_MAINWINDOW_SIZE, size.x + "," + size.y); //$NON-NLS-1$

		Locale configLocale = this.getConfigLocale();
		if(configLocale != null) {
			props.setProperty(Constants.CFG_LOCALE, LocaleSerializer.getParsableString(configLocale));
		}

		Locale signLocale = this.getSignLocale();
		if(signLocale != null) {
			props.setProperty(Constants.CFG_SIGN_LOCALE, LocaleSerializer.getParsableString(signLocale));
		}

		SignaturePosition pos = this.getDefaultSignaturePosition();

		if (pos == null) {
			props.setProperty(Constants.CFG_SIGNATURE_POSITION, ""); //$NON-NLS-1$
		} else if (pos.useAutoPositioning()) {
			props.setProperty(Constants.CFG_SIGNATURE_POSITION, "auto"); //$NON-NLS-1$
		} else {
			props.setProperty(Constants.CFG_SIGNATURE_POSITION,
					String.format((Locale) null, "x=%f;y=%f;p=%d", //$NON-NLS-1$
							pos.getX(), pos.getY(), pos.getPage()));
		}

		FileOutputStream outputstream = new FileOutputStream(configFile, false);

		props.store(outputstream, "Configuration file was generated!"); //$NON-NLS-1$

		log.info("Configuration file saved to " + configFile.getAbsolutePath()); //$NON-NLS-1$
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.asit.pdfover.gui.workflow.ConfigProvider#getConfigurationDirectory()
	 */
	@Override
	public String getConfigurationDirectory() {
		return Constants.CONFIG_DIRECTORY;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.asit.pdfover.gui.workflow.ConfigManipulator#setConfigurationFile(java
	 * .lang.String)
	 */
	@Override
	public void setConfigurationFile(String configurationFile) {
		this.configurationFile = configurationFile;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getConfigurationFile()
	 */
	@Override
	public String getConfigurationFile() {
		return this.configurationFile;
	}

	/**
	 * Sets the default bku type
	 * 
	 * @param bku
	 *            the bku type
	 */
	@Override
	public void setDefaultBKU(BKUs bku) {
		this.configuration.setDefaultBKU(bku);
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigOverlayManipulator#setDefaultBKUOverlay(at.asit.pdfover.signator.BKUs)
	 */
	@Override
	public void setDefaultBKUOverlay(BKUs bku) {
		this.configurationOverlay.setDefaultBKU(bku);
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getDefaultBKU()
	 */
	@Override
	public BKUs getDefaultBKU() {
		BKUs bku = this.configurationOverlay.getDefaultBKU();
		if (bku == BKUs.NONE)
			bku = this.configuration.getDefaultBKU();
		return bku;
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.PersistentConfigProvider#getDefaultBKUPersistent()
	 */
	@Override
	public BKUs getDefaultBKUPersistent() {
		return this.configuration.getDefaultBKU();
	}

	/**
	 * Sets the default signature position
	 * 
	 * @param signaturePosition
	 *            the default signature position
	 */
	@Override
	public void setDefaultSignaturePosition(SignaturePosition signaturePosition) {
		this.configuration.setDefaultSignaturePosition(signaturePosition);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.asit.pdfover.gui.workflow.ConfigProvider#getDefaultSignaturePosition()
	 */
	@Override
	public SignaturePosition getDefaultSignaturePosition() {
		return this.configuration.getDefaultSignaturePosition();
	}

	/**
	 * Sets the signature placeholder transparency
	 * 
	 * @param transparency
	 *            the signature placeholder transparency
	 */
	@Override
	public void setPlaceholderTransparency(int transparency) {
		this.configuration.setPlaceholderTransparency(transparency);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.asit.pdfover.gui.workflow.ConfigProvider#getPlaceholderTransparency()
	 */
	@Override
	public int getPlaceholderTransparency() {
		return this.configuration.getPlaceholderTransparency();
	}

	/**
	 * Sets the default mobile number
	 * 
	 * @param number
	 *            the default mobile number
	 */
	@Override
	public void setDefaultMobileNumber(String number) {
		try {
			if (number == null || number.trim().isEmpty()) {
				this.configuration.setMobileNumber(STRING_EMPTY);
			} else {
				this.configuration.setMobileNumber(number);
			}
		} catch (InvalidNumberException e) {
			log.error("Error setting mobile number", e); //$NON-NLS-1$
			try {
				this.configuration.setMobileNumber(STRING_EMPTY);
			} catch (InvalidNumberException e1) {
				// Ignore
			}
		}
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigOverlayManipulator#setDefaultMobileNumberOverlay(java.lang.String)
	 */
	@Override
	public void setDefaultMobileNumberOverlay(String number) {
		try {
			if (number == null || number.trim().isEmpty()) {
				this.configurationOverlay.setMobileNumber(STRING_EMPTY);
			} else {
				this.configurationOverlay.setMobileNumber(number);
			}
		} catch (InvalidNumberException e) {
			log.error("Error setting mobile number", e); //$NON-NLS-1$
			try {
				this.configurationOverlay.setMobileNumber(STRING_EMPTY);
			} catch (InvalidNumberException e1) {
				// Ignore
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getDefaultMobileNumber()
	 */
	@Override
	public String getDefaultMobileNumber() {
		String number = this.configurationOverlay.getMobileNumber();
		if (number == null)
			number = this.configuration.getMobileNumber();
		return number;
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.PersistentConfigProvider#getDefaultMobileNumberPersistent()
	 */
	@Override
	public String getDefaultMobileNumberPersistent() {
		return this.configuration.getMobileNumber();
	}

	/**
	 * Sets the default mobile password
	 * 
	 * @param password
	 *            the default password
	 */
	@Override
	public void setDefaultMobilePassword(String password) {
		if (password == null || password.trim().isEmpty()) {
			this.configuration.setMobilePassword(STRING_EMPTY);
		} else {
			this.configuration.setMobilePassword(password);
		}
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigOverlayManipulator#setDefaultMobilePasswordOverlay(java.lang.String)
	 */
	@Override
	public void setDefaultMobilePasswordOverlay(String password) {
		if (password == null || password.trim().isEmpty()) {
			this.configurationOverlay.setMobilePassword(STRING_EMPTY);
		} else {
			this.configurationOverlay.setMobilePassword(password);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getDefaultPassword()
	 */
	@Override
	public String getDefaultMobilePassword() {
		String password = this.configurationOverlay.getMobilePassword();
		if (password == null)
			password = this.configuration.getMobilePassword();
		return password;
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.PersistentConfigProvider#getDefaultMobilePasswordPersistent()
	 */
	@Override
	public String getDefaultMobilePasswordPersistent() {
		return this.configuration.getMobilePassword();
	}

	/**
	 * Sets the default emblem
	 * 
	 * @param emblem
	 *            the default emblem
	 */
	@Override
	public void setDefaultEmblem(String emblem) {
		try {
			if (emblem == null || emblem.trim().isEmpty()) {
				this.configuration.setEmblem(STRING_EMPTY);
			} else {
				this.configuration.setEmblem(emblem);
			}
		} catch (InvalidEmblemFile e) {
			log.error("Error setting emblem file", e); //$NON-NLS-1$
			try {
				this.configuration.setEmblem(STRING_EMPTY);
			} catch (InvalidEmblemFile e1) {
				// Ignore
			}
		}
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigOverlayManipulator#setDefaultEmblemOverlay(java.lang.String)
	 */
	@Override
	public void setDefaultEmblemOverlay(String emblem) {
		try {
			if (emblem == null || emblem.trim().isEmpty()) {
				this.configurationOverlay.setEmblem(STRING_EMPTY);
			} else {
				this.configurationOverlay.setEmblem(emblem);
			}
		} catch (InvalidEmblemFile e) {
			log.error("Error setting emblem file", e); //$NON-NLS-1$
			try {
				this.configurationOverlay.setEmblem(STRING_EMPTY);
			} catch (InvalidEmblemFile e1) {
				// Ignore
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getDefaultEmblem()
	 */
	@Override
	public String getDefaultEmblem() {
		String emblem = this.configurationOverlay.getEmblem();
		if (emblem == null)
			emblem = this.configuration.getEmblem();
		return emblem;
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.PersistentConfigProvider#getDefaultEmblemPersistent()
	 */
	@Override
	public String getDefaultEmblemPersistent() {
		return this.configuration.getEmblem();
	}

	/**
	 * Sets the proxy host
	 * 
	 * @param host
	 *            the proxy host
	 */
	@Override
	public void setProxyHost(String host) {
		if (host == null || host.trim().isEmpty()) {
			this.configuration.setProxyHost(STRING_EMPTY);
		} else {
			this.configuration.setProxyHost(host);
		}
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigOverlayManipulator#setProxyHostOverlay(java.lang.String)
	 */
	@Override
	public void setProxyHostOverlay(String host) {
		if (host == null || host.trim().isEmpty()) {
			this.configurationOverlay.setProxyHost(STRING_EMPTY);
		} else {
			this.configurationOverlay.setProxyHost(host);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getProxyHost()
	 */
	@Override
	public String getProxyHost() {
		String host = this.configurationOverlay.getProxyHost();
		if (host == null)
			host = this.configuration.getProxyHost();
		return host;
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.PersistentConfigProvider#getProxyHostPersistent()
	 */
	@Override
	public String getProxyHostPersistent() {
		return this.configuration.getProxyHost();
	}

	/**
	 * Sets the proxy port
	 * 
	 * @param port
	 *            the proxy port
	 */
	@Override
	public void setProxyPort(int port) {
		try {
			this.configuration.setProxyPort(port);
		} catch (InvalidPortException e) {
			log.error("Error setting proxy port" , e); //$NON-NLS-1$
			// ignore
		}
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigOverlayManipulator#setProxyPortOverlay(int)
	 */
	@Override
	public void setProxyPortOverlay(int port) {
		try {
			this.configurationOverlay.setProxyPort(port);
		} catch (InvalidPortException e) {
			log.error("Error setting proxy port" , e); //$NON-NLS-1$
			// ignore
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getProxyPort()
	 */
	@Override
	public int getProxyPort() {
		int port = this.configurationOverlay.getProxyPort();
		if (port == -1)
			port = this.configuration.getProxyPort();
		return this.configuration.getProxyPort();
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.PersistentConfigProvider#getProxyPortPersistent()
	 */
	@Override
	public int getProxyPortPersistent() {
		return this.configuration.getProxyPort();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.asit.pdfover.gui.workflow.ConfigManipulator#setDefaultOutputFolder
	 * (java.lang.String)
	 */
	@Override
	public void setDefaultOutputFolder(String outputFolder) {
		if (outputFolder == null || outputFolder.trim().isEmpty()) {
			this.configuration.setOutputFolder(STRING_EMPTY);
		} else {
			this.configuration.setOutputFolder(outputFolder);
		}
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigOverlayManipulator#setDefaultOutputFolderOverlay(java.lang.String)
	 */
	@Override
	public void setDefaultOutputFolderOverlay(String outputFolder) {
		if (outputFolder == null || outputFolder.trim().isEmpty()) {
			this.configurationOverlay.setOutputFolder(STRING_EMPTY);
		} else {
			this.configurationOverlay.setOutputFolder(outputFolder);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getDefaultOutputFolder()
	 */
	@Override
	public String getDefaultOutputFolder() {
		String outputFolder = this.configurationOverlay.getOutputFolder();
		if (outputFolder == null)
			outputFolder = this.configuration.getOutputFolder();
		return outputFolder;
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.PersistentConfigProvider#getDefaultOutputFolderPersistent()
	 */
	@Override
	public String getDefaultOutputFolderPersistent() {
		return this.configuration.getOutputFolder();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getMobileBKUURL()
	 */
	@Override
	public String getMobileBKUURL() {
		return this.configuration.getMobileBKUURL();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * at.asit.pdfover.gui.workflow.ConfigManipulator#setSignatureNote(java.
	 * lang.String)
	 */
	@Override
	public void setSignatureNote(String note) {
		if (note == null || note.trim().isEmpty()) {
			this.configuration.setSignatureNote(STRING_EMPTY);
		} else {
			this.configuration.setSignatureNote(note);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getSignatureNote()
	 */
	@Override
	public String getSignatureNote() {
		return this.configuration.getSignatureNote();
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigManipulator#setLocale(java.util.Locale)
	 */
	@Override
	public void setLocale(Locale locale) {
		if(locale == null) {
			this.configuration.setLocale(Messages.getDefaultLocale());
		} else {
			this.configuration.setLocale(locale);
			Locale.setDefault(locale);
			Messages.setLocale(locale);
		}
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getConfigLocale()
	 */
	@Override
	public Locale getConfigLocale() {
		return this.configuration.getLocale();
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigManipulator#setSignLocale(java.util.Locale)
	 */
	@Override
	public void setSignLocale(Locale locale) {
		if(locale == null) {
			this.configuration.setSignLocale(Messages.getDefaultLocale());
		} else {
			this.configuration.setSignLocale(locale);
		}
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getSignLocale()
	 */
	@Override
	public Locale getSignLocale() {
		return this.configuration.getSignLocale();
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigManipulator#setMainWindowSize(org.eclipse.swt.graphics.Point)
	 */
	@Override
	public void setMainWindowSize(Point size) {
		this.configuration.setMainWindowSize(size);
	}

	/* (non-Javadoc)
	 * @see at.asit.pdfover.gui.workflow.ConfigProvider#getMainWindowSize()
	 */
	@Override
	public Point getMainWindowSize() {
		return this.configuration.getMainWindowSize();
	}
}
