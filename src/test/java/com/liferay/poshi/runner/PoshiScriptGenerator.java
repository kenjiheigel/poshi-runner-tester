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

	public static final String poshiDirName = PoshiScriptEvaluator.poshiDirName;
	public static final String ticket = "LRQA-45878";

	@BeforeClass
	public static void setUp() throws Exception {
		String[] poshiFileNames = {"**/*.function"};

		PoshiContext.readFiles(poshiFileNames, poshiDirName);

		PoshiContext.readFiles(
			poshiFileNames,
			"/Users/kenji/Projects/github/liferay-qa-websites-ee/shared");
	}

	@Test
	public void generateFunctionsPoshiScript()
		throws IOException, PoshiScriptParserException, TimeoutException {

		for (String functionFilePath : getFunctionFilePaths()) {
			generatePoshiScriptFile(functionFilePath);
		}

		ExecUtil.executeCommands(
			false, new File(poshiDirName), 30000,
			"git commit -am \"" + ticket +
				" Translate *.function files to Poshi Script\"");
	}

	@Test
	public void generateMacrosPoshiScript()
		throws IOException, PoshiScriptParserException, TimeoutException {

		for (String macroFilePath : getMacroFilePaths()) {
			generatePoshiScriptFile(macroFilePath);
		}

		ExecUtil.executeCommands(
			false, new File(poshiDirName), 30000,
			"git commit -am \"" + ticket +
				" Translate *.macro files to Poshi Script\"");
	}

	@Test
	public void generatePoshiScriptFile() throws IOException, TimeoutException {

		//		String filePath = poshiDirName + "tests/enduser/wem/navigationmenus/NavigationMenus.testcase";

		//
		//		generatePoshiScriptFile(filePath);
	}

	@Test
	public void generateTestCasesPoshiScript()
		throws IOException, PoshiScriptParserException, TimeoutException {

		for (String testCaseFilePath : getTestCaseFilePaths()) {
			generatePoshiScriptFile(testCaseFilePath);
		}

		ExecUtil.executeCommands(
			false, new File(poshiDirName), 30000,
			"git commit -am \"" + ticket +
				" Translate *.testcase files to Poshi Script\"");
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

	static {
		PoshiScriptEvaluator.init();
	}

}