/*
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.poshi.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author Kenji Heigel
 */
public class Authentication {

	public static final String JIRA_PASSWORD;

	public static final String JIRA_USERNAME;

	static {
		String jiraUsername = "";
		String jiraToken = "";

		try {
			BufferedReader bufferedReader = new BufferedReader(
				new FileReader(new File(".authentication")));

			String readLine = "";

			while ((readLine = bufferedReader.readLine()) != null) {
				if (readLine.startsWith("username")) {
					int index = readLine.indexOf(":");

					jiraUsername = readLine.substring(index);
				}

				if (readLine.startsWith("token")) {
					int index = readLine.indexOf(":");

					jiraToken = readLine.substring(index);
				}
			}
		}
		catch (IOException ioException) {
			throw new RuntimeException(ioException);
		}

		if (jiraUsername.isEmpty() || jiraToken.isEmpty()) {
			System.out.println(
				"Please create a .authentication file in the project " +
					"directory with the content:");

			System.out.println("");
			System.out.println("username:exampleusername");
			System.out.println("token:exampletoken");

			throw new RuntimeException("Invalid JIRA credential file");
		}

		JIRA_USERNAME = jiraUsername;
		JIRA_PASSWORD = jiraToken;
	}

}