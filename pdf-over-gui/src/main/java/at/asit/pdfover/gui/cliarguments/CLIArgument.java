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
package at.asit.pdfover.gui.cliarguments;

import at.asit.pdfover.gui.exceptions.InitializationException;
import at.asit.pdfover.gui.workflow.ConfigOverlayManipulator;
import at.asit.pdfover.gui.workflow.StateMachine;
import at.asit.pdfover.gui.workflow.Status;

/**
 * CLI Argument base class
 */
public abstract class CLIArgument {

	private String helpText = null;

	private String[] commandOptions = null;

	private StateMachine stateMachine;

	/**
	 * @param commandOptions
	 * @param helpText
	 */
	protected CLIArgument(String[] commandOptions, String helpText) {
		this.helpText = helpText;
		this.commandOptions = commandOptions;
	}

	/**
	 * Set the state machine
	 * Used for configuration overlay manipulator and status
	 * @param stateMachine the state machine
	 */
	protected void setStateMachine(StateMachine stateMachine) {
		this.stateMachine = stateMachine;
	}

	/**
	 * Get the configuration overlay manipulator
	 * @return the configuration overlay manipulator
	 */
	protected ConfigOverlayManipulator getConfiguration() {
		return this.stateMachine.getConfigOverlayManipulator();
	}

	/**
	 * Get the status
	 * @return the status
	 */
	protected Status getStatus() {
		return this.stateMachine.getStatus();
	}


	/**
	 * Set help text
	 * 
	 * @param value
	 */
	protected void setHelpText(String value) {
		this.helpText = value;
	}

	/**
	 * Gets help text
	 * 
	 * @return help text
	 */
	public String getHelpText() {
		return this.helpText;
	}

	/**
	 * Set the command option in format: -...
	 * 
	 * Examples: -h
	 * 
	 * @param value
	 */
	protected void setCommandOptions(String[] value) {
		this.commandOptions = value;
	}

	/**
	 * Get the command option
	 * 
	 * Examples: -h
	 * @return the command option
	 */
	public String[] getCommandOptions() {
		return this.commandOptions;
	}

	/**
	 * Invokes the argument to set stuff within the stateMachine
	 * 
	 * It should return the offset within the args array where the last used argument of this CLIArgument was used.
	 * 
	 * Example:
	 * args[] = { "-h", "-b", "LOCAL" }
	 * 
	 * Help Argument call:
	 *     offset = 0
	 *     returns 0
	 *     
	 * BKU Argument call:
	 *     offset = 1
	 *     returns 2
	 * 
	 * @param args
	 * @param argOffset
	 * @param handler 
	 * @return returns the argumentOffset ending the section of this Argument
	 * @throws InitializationException 
	 */
	public abstract int handleArgument(String[] args, int argOffset, ArgumentHandler handler) throws InitializationException;
}
