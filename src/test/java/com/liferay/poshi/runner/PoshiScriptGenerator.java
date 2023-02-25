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
import com.liferay.poshi.core.elements.PoshiElement;
import com.liferay.poshi.core.elements.PoshiNodeFactory;
import com.liferay.poshi.core.script.PoshiScriptParserException;
import com.liferay.poshi.core.util.Dom4JUtil;
import com.liferay.poshi.core.util.FileUtil;
import com.liferay.poshi.core.util.PropsUtil;
import com.liferay.poshi.runner.util.ExecUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Kenji Heigel
 */
public class PoshiScriptGenerator extends PoshiScriptEvaluator {

	public static final boolean COMMIT = false;

	public static final String POSHI_DIR_NAME =
		PoshiScriptEvaluator.poshiDirName;

	public static final String TICKET = "LRQA-45878";

	@BeforeClass
	public static void setUp() throws Exception {
		Properties properties = new Properties();

		File poshiPropertiesFile = new File(POSHI_DIR_NAME, "poshi.properties");

		if (poshiPropertiesFile.exists()) {
			properties.load(new FileInputStream(poshiPropertiesFile));
		}

		File poshiExtPropertiesFile = new File(
			POSHI_DIR_NAME + "/poshi-ext.properties");

		if (poshiExtPropertiesFile.exists()) {
			properties.load(new FileInputStream(poshiExtPropertiesFile));
		}

		if (properties.get("test.base.dir.name") == null) {
			properties.setProperty("test.base.dir.name", POSHI_DIR_NAME);
		}

		PropsUtil.clear();

		PropsUtil.setProperties(properties);

		PoshiContext.readFiles();
	}

	@Test
	public void generatePoshiScript()
		throws IOException, PoshiScriptParserException, TimeoutException {

		generatePoshiScriptFiles(new HashSet<>(PoshiContext.getFilePaths()));
	}

	@Test
	public void generatePoshiScriptFile() throws PoshiScriptParserException {
		String filePath =
			POSHI_DIR_NAME + "tests/portalsmoke/PortalSmoke.testcase";

		generatePoshiScriptFile(filePath);
	}

	@Test
	public void generatePoshiScriptFunctions()
		throws IOException, PoshiScriptParserException, TimeoutException {

		generatePoshiScriptFiles(getFunctionFilePaths(), "function");
	}

	@Test
	public void generatePoshiScriptMacros()
		throws IOException, PoshiScriptParserException, TimeoutException {

		generatePoshiScriptFiles(getMacroFilePaths(), "macro");
	}

	@Test
	public void generatePoshiScriptTestCases()
		throws IOException, PoshiScriptParserException, TimeoutException {

		generatePoshiScriptFiles(getTestCaseFilePaths(), "testcase");
	}

	@Test
	public void generatePoshiXML() throws PoshiScriptParserException {
		generatePoshiXMLFiles(new HashSet<>(PoshiContext.getFilePaths()));
	}

	@Test
	public void generatePoshiXMLFile() throws PoshiScriptParserException {
		String filePath =
			POSHI_DIR_NAME + "tests/portalsmoke/PortalSmoke.testcase";

		generatePoshiXMLFile(filePath);
	}

	@Test
	public void generatePoshiXMLFunctions() throws PoshiScriptParserException {
		generatePoshiXMLFiles(getFunctionFilePaths());
	}

	@Test
	public void generatePoshiXMLMacros() throws PoshiScriptParserException {
		generatePoshiXMLFiles(getMacroFilePaths());
	}

	@Test
	public void generatePoshiXMLTestcases() throws PoshiScriptParserException {
		generatePoshiXMLFiles(getTestCaseFilePaths());
	}

	@Test
	public void regenerateFile() throws Exception {
		String filePath = "/path/to/poshi/file";

		PoshiElement poshiElement =
			(PoshiElement)PoshiNodeFactory.newPoshiNodeFromFile(
				FileUtil.getURL(new File(filePath)));

		System.out.println(poshiElement.toPoshiScript());

		PoshiScriptParserException.throwExceptions(filePath);
	}

	@Test
	public void regeneratePoshiScript() throws PoshiScriptParserException {
		regeneratePoshiScript(new HashSet<>(PoshiContext.getFilePaths()));
	}

	protected void generatePoshiScriptFile(String filePath)
		throws PoshiScriptParserException {

		try {
			URL url = FileUtil.getURL(new File(filePath));

			PoshiElement poshiElement =
				(PoshiElement)PoshiNodeFactory.newPoshiNodeFromFile(url);

			String fileContent = FileUtil.read(filePath);

			Document document = Dom4JUtil.parse(fileContent);

			Element rootElement = document.getRootElement();

			Dom4JUtil.removeWhiteSpaceTextNodes(rootElement);

			String poshiScript = poshiElement.toPoshiScript();

			PoshiElement newPoshiElement =
				(PoshiElement)PoshiNodeFactory.newPoshiNode(poshiScript, url);

			if (areElementsEqual(rootElement, poshiElement) &&
				areElementsEqual(rootElement, newPoshiElement)) {

				Files.write(Paths.get(filePath), poshiScript.getBytes());
			}
			else {
				System.out.println("Could not generate poshi script:");
				System.out.println(filePath);
			}
		}
		catch (DocumentException documentException) {
			documentException.printStackTrace();
		}
		catch (IOException ioException) {
			ioException.printStackTrace();
		}
	}

	protected void generatePoshiScriptFiles(Set<String> filePaths)
		throws IOException, PoshiScriptParserException, TimeoutException {

		generatePoshiScriptFiles(filePaths, null);
	}

	protected void generatePoshiScriptFiles(
			Set<String> filePaths, String fileType)
		throws IOException, PoshiScriptParserException, TimeoutException {

		for (String filepath : filePaths) {
			if (filepath.endsWith(".path")) {
				continue;
			}

			generatePoshiScriptFile(filepath);
		}

		if (COMMIT) {
			ExecUtil.executeCommands(
				false, new File(POSHI_DIR_NAME), 30000,
				"git commit -am \"" + TICKET + " Translate *." + fileType +
					" files to Poshi Script\"");
		}
	}

	protected void generatePoshiXMLFile(String filePath)
		throws PoshiScriptParserException {

		if (filePath.endsWith(".path")) {
			return;
		}

		try {
			PoshiElement poshiElement =
				(PoshiElement)PoshiNodeFactory.newPoshiNodeFromFile(
					FileUtil.getURL(new File(filePath)));

			String poshiElementString = Dom4JUtil.format(poshiElement);

			Files.write(Paths.get(filePath), poshiElementString.getBytes());
		}
		catch (IOException ioException) {
			ioException.printStackTrace();
		}
	}

	protected void generatePoshiXMLFiles(Set<String> filePaths)
		throws PoshiScriptParserException {

		for (String filepath : filePaths) {
			generatePoshiXMLFile(filepath);
		}
	}

	protected void regeneratePoshiScript(Set<String> filePaths)
		throws PoshiScriptParserException {

		for (String filepath : filePaths) {
			regeneratePoshiScript(filepath);
		}
	}

	protected void regeneratePoshiScript(String filePath)
		throws PoshiScriptParserException {

		if (filePath.endsWith(".path")) {
			return;
		}

		try {
			PoshiElement poshiElement =
				(PoshiElement)PoshiNodeFactory.newPoshiNodeFromFile(
					FileUtil.getURL(new File(filePath)));

			String poshiScript = poshiElement.toPoshiScript();

			Files.write(Paths.get(filePath), poshiScript.getBytes());
		}
		catch (IOException ioException) {
			ioException.printStackTrace();
		}
	}

	static {
		PoshiScriptEvaluator.init();
	}

}