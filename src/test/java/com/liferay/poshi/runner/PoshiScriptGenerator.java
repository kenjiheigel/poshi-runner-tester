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
import com.liferay.poshi.runner.util.ExecUtil;

import java.io.File;
import java.io.IOException;

import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Paths;

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

	public static final boolean commit = false;
	public static final String poshiDirName = PoshiScriptEvaluator.poshiDirName;
	public static final String ticket = "LRQA-45878";

	@BeforeClass
	public static void setUp() throws Exception {
		String[] poshiFileNames = {"**/*.function"};

		PoshiContext.readFiles(poshiFileNames, poshiDirName);
	}

	@Test
	public void generatePoshiScriptFile() throws PoshiScriptParserException {
		String filePath =
			poshiDirName + "tests/portalsmoke/PortalSmoke.testcase";

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
	public void generatePoshiXMLFile() throws PoshiScriptParserException {
		String filePath =
			poshiDirName + "tests/portalsmoke/PortalSmoke.testcase";

		generatePoshiXMLFile(filePath);
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

				Files.write(
					Paths.get(filePath),
					poshiElement.toPoshiScript(
					).getBytes());
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

	protected void generatePoshiScriptFiles(
			Set<String> filePaths, String fileType)
		throws IOException, PoshiScriptParserException, TimeoutException {

		for (String filepath : filePaths) {
			generatePoshiScriptFile(filepath);
		}

		if (commit) {
			ExecUtil.executeCommands(
				false, new File(poshiDirName), 30000,
				"git commit -am \"" + ticket + " Translate *." + fileType +
					" files to Poshi Script\"");
		}
	}

	protected void generatePoshiXMLFile(String filePath)
		throws PoshiScriptParserException {

		try {
			URL url = FileUtil.getURL(new File(filePath));

			PoshiElement poshiElement =
				(PoshiElement)PoshiNodeFactory.newPoshiNodeFromFile(url);

			Files.write(
				Paths.get(filePath),
				Dom4JUtil.format(
					poshiElement
				).getBytes());
		}
		catch (IOException ioException) {
			ioException.printStackTrace();
		}
	}

	static {
		PoshiScriptEvaluator.init();
	}

}