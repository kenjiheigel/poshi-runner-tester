/**
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

import com.liferay.poshi.core.PoshiContext;
import com.liferay.poshi.core.PoshiValidation;
import com.liferay.poshi.core.util.PropsUtil;

import java.io.File;
import java.io.FileInputStream;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Kenji Heigel
 */
public class PoshiRunnerTest extends PoshiRunnerTestCase {

	public static boolean local = true;

	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();

		// For poshi-dev-tools use, create a poshi-ext.properties file in the
		// root directory with desired properties.
		// For liferay-portal use, set the boolean 'local' to false and generate
		// a poshi-ext.properties by running from portal (where test.class is
		// the test you plan to run):
		// ant -f build-test.xml prepare-selenium -Dtest.class=PortalSmoke

		File poshiPropertiesFile = new File(
			_TEST_BASE_DIR_NAME, "poshi.properties");

		if (poshiPropertiesFile.exists()) {
			properties.load(new FileInputStream(poshiPropertiesFile));
		}

		File poshiExtPropertiesFile = new File(
			_TEST_BASE_DIR_NAME + "/poshi-ext.properties");

		if (poshiExtPropertiesFile.exists()) {
			properties.load(new FileInputStream(poshiExtPropertiesFile));
		}

		PropsUtil.clear();

		PropsUtil.setProperties(properties);

		PoshiContext.readFiles();
	}

	@After
	public void tearDown() {
		try {
			if (poshiRunner != null) {
				poshiRunner.tearDown();
			}
		}
		catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
	}

	@Test
	public void testPoshiTest() throws Exception {
		poshiRunner = new PoshiRunner("LocalFile.PoshiScriptTest#MyTest");

		try {
			poshiRunner.setUp();

			poshiRunner.test();
		}
		catch (Exception exception) {
			exception.printStackTrace();
		}
		finally {
			try {
				poshiRunner.tearDown();
			}
			catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		}
	}

	@Test
	public void testValidation() throws Exception {
		PoshiValidation.validate();
	}

	public PoshiRunner poshiRunner;

	private static final String _TEST_BASE_DIR_NAME;

	static {
		if (local) {
			_TEST_BASE_DIR_NAME =
				"/opt/dev/projects/github/poshi-dev-tools/src/test/resources" +
					"/poshiFiles";
		}
		else {
			_TEST_BASE_DIR_NAME =
				"/opt/dev/projects/github/liferay-portal/portal-web";
		}
	}

}