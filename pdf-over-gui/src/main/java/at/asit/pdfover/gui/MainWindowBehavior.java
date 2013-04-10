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
package at.asit.pdfover.gui;

import java.util.EnumMap;
import java.util.Map;

import at.asit.pdfover.gui.MainWindow.Buttons;

/**
 * Behavior manipulation for Main Window
 */
public class MainWindowBehavior {
	protected Map<Buttons, Boolean> buttonsEnabled;
	protected Map<Buttons, Boolean> buttonsActive;
	protected boolean mainBarVisible;

	/**
	 * 
	 */
	public MainWindowBehavior() {
		this.buttonsActive = new EnumMap<MainWindow.Buttons, Boolean>(MainWindow.Buttons.class);
		this.buttonsEnabled = new EnumMap<MainWindow.Buttons, Boolean>(	MainWindow.Buttons.class);
		reset();
	}

	public void setActive(Buttons button, boolean active) {
		this.buttonsActive.put(button, active);
	}

	public boolean getActive(Buttons button) {
		return this.buttonsActive.get(button);
	}

	public void setEnabled(Buttons button, boolean enabled) {
		this.buttonsEnabled.put(button, enabled);
	}

	public boolean getEnabled(Buttons button) {
		return this.buttonsEnabled.get(button);
	}

	/**
	 * Resets all behavior to a default state
	 * All buttons are inactive
	 * All buttons are disabled
	 * Main bar is visible
	 */
	public void reset() {
		for (Buttons button : Buttons.values()) {
			setActive(button, false);
			setEnabled(button, false);
		}
		setMainBarVisible(true);
	}

	public void setMainBarVisible(boolean visible) {
		this.mainBarVisible = visible;
	}

	public boolean getMainBarVisible() {
		return this.mainBarVisible;
	}
}
