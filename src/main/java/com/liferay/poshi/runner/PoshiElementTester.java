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
import com.liferay.poshi.core.util.Dom4JUtil;
import com.liferay.poshi.core.util.FileUtil;
import com.liferay.poshi.core.util.OSDetector;
import difflib.Chunk;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;
import org.apache.tools.ant.DirectoryScanner;
import org.dom4j.Attribute;
import org.dom4j.CDATA;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Text;
import org.dom4j.util.NodeComparator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Kenji Heigel
 */

public class PoshiElementTester {

//	public static String type = "function";
	public static String type = "macro";
//	public static String type = "testcase";

//	static String portalPoshiDir = "/Users/kenji/Projects/github/liferay-portal-ee/7.1.x/portal-web/test/functional/com/liferay/portalweb/";
//	static String portalPoshiDir = "/Users/kenji/Projects/github/com-liferay-commerce";
	static String portalPoshiDir = "/Users/kenji/Projects/github/liferay-portal/master/portal-web/test/functional/com/liferay/portalweb/";
//	static String portalPoshiDir = "/Users/kenji/Projects/github/liferay-portal-ee/7.0.x/portal-web/test/functional/com/liferay/portalweb/";

//	static String portalPoshiDir = "/Users/kenji/Projects/github/liferay-portal/master/portal-web/test/functional/com/liferay/portalweb/";
	static String poshiFileDir = portalPoshiDir;

	public static void main(String[] args) throws Exception {
		String[] poshiFileNames = {"**/*.function"};

		PoshiContext.readFiles(poshiFileNames, portalPoshiDir);

		boolean simulate = false;
		simulate = true;
		boolean generate = false;
//		generate = true;
		boolean analyze = false;
		boolean evaluateTest = false;
//		evaluateTest = true;
		boolean evaluatePerformance = false;
		boolean generateTest = false;

		if (simulate) {
			simulate(poshiFileDir, "." + type);
		}

		if (generate) {
			generate(poshiFileDir);
		}

		if (analyze) {
			analyze(poshiFileDir);
		}

		if (evaluateTest) {
			evaluateTestFile("JSONWebcontent");
		}

		if (evaluatePerformance) {
			evaluatePerformance("RolesandpermissionsUsecase");
		}

		if (generateTest) {
			generatePoshiFile("WebcontentUsecase");
		}
	}

	public static void generate(String baseDir) throws Exception {
		List<String> filePaths = new ArrayList<>();

		if (!FileUtil.exists(baseDir)) {
			System.out.print(baseDir + " does not exist");

			return;
		}

		DirectoryScanner directoryScanner = new DirectoryScanner();

		directoryScanner.setBasedir(baseDir);

		String[] includes = {"**\\*." + type};

		directoryScanner.setIncludes(includes);

		directoryScanner.scan();

		for (String filePath : directoryScanner.getIncludedFiles()) {
			filePath = baseDir + "/" + filePath;

			if (OSDetector.isWindows()) {
				filePath = filePath.replace("/", "\\");
			}

			filePaths.add(filePath);
		}

		for (String filePath : filePaths) {
			String testName = _getTestName(filePath);

			generatePoshiFile(testName);
		}
	}

	public static void evaluateElements(Node node1, Node node2)
		throws Exception {

		NodeComparator nodeComparator = new NodeComparator();

		int compare = 1;

		try {
			compare = nodeComparator.compare(node1, node2);
		}
		catch (Exception e) {
		}

		if (compare == 0) {
			// Elements equal

			return;
		}

		String node1TypeName = node1.getNodeTypeName();
		String node2TypeName = node2.getNodeTypeName();

		if (node1TypeName.equals("Element") &&
			node2TypeName.equals("Element")) {

			Element element1 = (Element)node1;
			Element element2 = (Element)node2;

			List<Node> element1Nodes = Dom4JUtil.toNodeList(element1.content());
			List<Node> element2Nodes = Dom4JUtil.toNodeList(element2.content());

			int size = element1Nodes.size();

			if (size != element2Nodes.size()) {
				System.out.println("\tElements have varying amounts of child elements");

				size = Math.min(size, element2Nodes.size());

				for (int i = 0; i < size; i++) {
					Node element1Child = element1Nodes.get(i);
					Node element2Child = element2Nodes.get(i);

					compare = 1;

					try {
						compare = nodeComparator.compare(element1Child, element2Child);
					}
					catch (Exception e) {
					}

					if (compare == 0) {
						// Elements equal
						continue;
					}

					System.out.println("\t\tNode 1:");
					System.out.println(Dom4JUtil.format(element1Child).trim());
					System.out.println("\t\tNode 2:");
					System.out.println(Dom4JUtil.format(element2Child).trim());

					break;
				}

				return;
			}

			if (size == 0) {
				System.out.println("\tElements do not match");
				System.out.println("\t\telement 1:");
				System.out.println(Dom4JUtil.format(element1).trim());
				System.out.println("\t\telement 2:");
				System.out.println(Dom4JUtil.format(element2).trim());

				return;
			}

			List<Attribute> element1Attributes = Dom4JUtil.toAttributeList(element1.attributes());
			List<Attribute> element2Attributes = Dom4JUtil.toAttributeList(element2.attributes());

			if (element1Attributes.size() != element2Attributes.size()) {
				for (int i = 0; i < Math.min(element1Attributes.size(), element2Attributes.size()); i++) {
					Node element1Attribute = element1Attributes.get(i);
					Node element2Attribute = element2Attributes.get(i);

					compare = 1;

					try {
						compare = nodeComparator.compare(element1Attribute, element2Attribute);
					} catch (Exception e) {
					}

					if (compare == 0) {
						// Elements equal
						continue;
					}

					System.out.println("\tElement attributes do not match");
					System.out.println("\t\tAttribute 1:");
					System.out.println(Dom4JUtil.format(element1Attribute).trim());
					System.out.println("\t\tAttribute 2:");
					System.out.println(Dom4JUtil.format(element2Attribute).trim());
				}
			}

			for (int i = 0; i < size; i++) {
				Node element1Child = element1Nodes.get(i);
				Node element2Child = element2Nodes.get(i);

				evaluateElements(element1Child, element2Child);
			}
		}
		else {
			System.out.println("\tNodes do not match");
			System.out.println("\t\tNode 1:");
			System.out.println(Dom4JUtil.format(node1).trim());
			System.out.println("\t\tNode 2:");
			System.out.println(Dom4JUtil.format(node2).trim());
		}
	}

	public static boolean areElementsEqual(Element element1, Element element2)
		throws Exception {

		NodeComparator nodeComparator = new NodeComparator();

		int compare = 1;

		try {
			compare = nodeComparator.compare(element1, element2);
		}
		catch (Exception e) {
		}

		if (compare == 0) {
			return true;
		}

		return false;
	}

	public static void removeWhiteSpaceTextNodes(Element element) {
		for (Node node : Dom4JUtil.toNodeList(element.content())) {
			if (node instanceof CDATA) {
				continue;
			}

			if (node instanceof Text) {
				String nodeText = node.getText();

				nodeText = nodeText.trim();

				if (nodeText.length() == 0) {
					node.detach();
				}
			}
		}

		for (Element childElement :
		Dom4JUtil.toElementList(element.elements())) {

			removeWhiteSpaceTextNodes(childElement);
		}
	}

	public static String _getTestName(String filePath) {
		int start = filePath.lastIndexOf("/");

		int end = filePath.indexOf("." + type, start);

		return filePath.substring(start + 1, end);
	}

	public static String _getFilePath(String testName) {
		for (String filePath : _getFilePaths()) {
			if (filePath.contains(testName)) {
				return filePath;
			}
		}

		return null;
	}

	public static void generatePoshiFile(String testName) throws Exception {
		for (String filePath : _getFilePaths()) {
			String fileName = _getTestName(filePath);
			URL url = FileUtil.getURL(new File(filePath));

			if (fileName.equals(testName)) {
				PoshiElement poshiElement = null;

				boolean convert = true;
				boolean delete = false;

				try {
					poshiElement =
						(PoshiElement) PoshiNodeFactory.newPoshiNodeFromFile(url);

					String fileContent = FileUtil.read(filePath);

					Document document = Dom4JUtil.parse(fileContent);

					Element rootElement = document.getRootElement();

					removeWhiteSpaceTextNodes(rootElement);

					String poshiScript = poshiElement.toPoshiScript();

					int index = filePath.lastIndexOf(".");

					PoshiElement newPoshiElement =
						(PoshiElement) PoshiNodeFactory.newPoshiNode(poshiScript, url);

					if (convert) {
						if (areElementsEqual(rootElement, poshiElement)) {
							if (areElementsEqual(rootElement, newPoshiElement)) {
								Files.write(
								Paths.get(filePath),
								poshiElement.toPoshiScript().getBytes());
							}
							else {
								System.out.println("Could not generate poshi script:");
								System.out.println(filePath);

								if (delete) {
									Files.delete(Paths.get(filePath));
								}
							}
						}
						else {
							System.out.println("Could not generate poshi script:");
							System.out.println(filePath);

							if (delete) {
								Files.delete(Paths.get(filePath));
							}
						}
					}
				}
				catch (Exception e) {
					if (delete) {
						Files.delete(Paths.get(filePath));
					}

					continue;
				}
			}
		}
	}

	public static void evaluatePerformance(String testName) throws Exception {
		for (String filePath : _getFilePaths()) {
			String fileName = _getTestName(filePath);

			URL url = FileUtil.getURL(new File(filePath));

			if (fileName.equals(testName)) {
				double startTime = System.nanoTime();

				String fileContent = FileUtil.read(filePath);

				int index = filePath.lastIndexOf(".");

				String fileExtension = filePath.substring(index + 1);

//				System.out.println(System.nanoTime() - startTime);

				PoshiElement poshiElement =
					(PoshiElement) PoshiNodeFactory.newPoshiNodeFromFile(url);

				System.out.println(System.nanoTime() - startTime);

				String poshiScript = poshiElement.toPoshiScript();

				System.out.println(System.nanoTime() - startTime);

				PoshiElement newPoshiElement =
					(PoshiElement) PoshiNodeFactory.newPoshiNode(poshiScript, url);

				System.out.println(System.nanoTime() - startTime);
			}
		}
	}

	public static void evaluateTestFile(String testName) throws Exception {
		for (String filePath : _getFilePaths()) {
			String fileName = _getTestName(filePath);
			URL url = FileUtil.getURL(new File(filePath));

			if (fileName.equals(testName)) {
				String fileContent = FileUtil.read(filePath);

				int index = filePath.lastIndexOf(".");

				String fileExtension = filePath.substring(index + 1);

				Document document = Dom4JUtil.parse(fileContent);

				Element baseElement = document.getRootElement();

				_removeWhiteSpace(baseElement);

				PoshiElement poshiElement =
					(PoshiElement) PoshiNodeFactory.newPoshiNodeFromFile(url);

//				System.out.println("dom4J element:");
//				System.out.println(Dom4JUtil.format(baseElement));
//				System.out.println("poshi element:");
//				System.out.println(Dom4JUtil.format(poshiElement));

				String poshiScript = poshiElement.toPoshiScript();
				System.out.println(poshiScript);

				System.out.println("poshi element from readable:");

				PoshiElement newPoshiElement =
					(PoshiElement) PoshiNodeFactory.newPoshiNode(poshiScript, url);

				if (!areElementsEqual(baseElement, newPoshiElement)) {
					System.out.println("elements are not equal");

					String baseString = Dom4JUtil.format(poshiElement);
					String poshiString = Dom4JUtil.format(newPoshiElement);

//					System.out.println(baseString);
//					System.out.println(poshiString);

					Patch patch = DiffUtils.diff(_stringToLines(baseString), _stringToLines(poshiString));

					for (Delta delta : patch.getDeltas()) {
						_printComparison(delta);
					}
				}

				System.out.println(Dom4JUtil.format(newPoshiElement));
			}
		}
	}

	private static List<String> _getFilePaths() {
		String baseDir = "/Users/kenji/Projects/github/liferay-portal/master/portal-web/test/functional/com/liferay/portalweb/";
//		String baseDir = "/Users/kenji/Projects/github/liferay-qa-websites-ee/support/testFunctional";

		List<String> filePaths = new ArrayList<>();

		if (!FileUtil.exists(baseDir)) {
			System.out.print("does not exist");
		}

		DirectoryScanner directoryScanner = new DirectoryScanner();

		directoryScanner.setBasedir(baseDir);

		String[] includes = {"**\\*." + type};

		directoryScanner.setIncludes(includes);

		directoryScanner.scan();

		for (String filePath : directoryScanner.getIncludedFiles()) {
			filePath = baseDir + "/" + filePath;

			if (OSDetector.isWindows()) {
				filePath = filePath.replace("/", "\\");
			}

			filePaths.add(filePath);
		}

		return filePaths;
	}

	public static void analyze(String baseDir) throws Exception {
		for (String filePath : _getFilePaths()) {
			String fileContent = FileUtil.read(filePath);
			URL url = FileUtil.getURL(new File(filePath));

			Document document = Dom4JUtil.parse(fileContent);

			Element baseElement = document.getRootElement();

			removeWhiteSpaceTextNodes(baseElement);

			String fileName = _getTestName(filePath);

			try {
				PoshiElement poshiElement =
				 (PoshiElement)PoshiNodeFactory.newPoshiNodeFromFile(url);

				System.out.println(fileName);

				if (!areElementsEqual(baseElement, poshiElement)) {
					evaluateElements(baseElement, poshiElement);

					continue;
				}

				String poshiScript = poshiElement.toPoshiScript();

				PoshiElement newPoshiElement =
					(PoshiElement) PoshiNodeFactory.newPoshiNode(poshiScript, url);

				evaluateElements(baseElement, newPoshiElement);
			}
			catch (RuntimeException re) {
			}
		}
	}

	public static void processElements (Element baseElement, Element failingElement) throws Exception {
		if (!areElementsEqual(baseElement, failingElement)) {
			List<Element> baseChildChildElements = baseElement.elements();
			List<Element> failingChildChildElements = failingElement.elements();

			if (baseChildChildElements.size() == failingChildChildElements.size()) {
				for (int j = 0; j < baseChildChildElements.size(); j++) {
					Element baseChildChildElement = baseChildChildElements.get(j);
					Element failingChildChildElement = failingChildChildElements.get(j);

					if (!areElementsEqual(baseChildChildElement, failingChildChildElement)) {
						System.out.println(Dom4JUtil.format(baseChildChildElement));
						System.out.println(Dom4JUtil.format(failingChildChildElement));
					}
				}
			}
		}
	}

	public static boolean simulate(String baseDir, String fileType) throws Exception {
		List<String> filePaths = new ArrayList<>();

		if (!FileUtil.exists(baseDir)) {
			System.out.print("does not exist");

			return false;
		}

		DirectoryScanner directoryScanner = new DirectoryScanner();

		directoryScanner.setBasedir(baseDir);

		String[] includes = {"**\\*" + fileType};

		directoryScanner.setIncludes(includes);

		directoryScanner.scan();

		for (String filePath : directoryScanner.getIncludedFiles()) {
			filePath = baseDir + "/" + filePath;

			if (OSDetector.isWindows()) {
				filePath = filePath.replace("/", "\\");
			}

			filePaths.add(filePath);
		}

		int commandElementFailure = 0;
		int commandElementReadableToXMLFailure = 0;
		int setupFailure = 0;
		int teardownFailure = 0;
		int varPropFailure = 0;
		int commandFailure = 0;
		int successful = 0;
		int testsFromSuccessfulFiles = 0;
		int totalTests = 0;
		int uniqueFiles = 0;
		int uniqueFailuresTestCount = 0;

		List<String> convertSuccess = new ArrayList<>();
		List<String> successfulFiles = new ArrayList<>();

		double startTime = System.nanoTime();
		double lastTime = startTime;

		for (String filePath : filePaths) {
			PoshiElement definitionElement = null;

			URL url = FileUtil.getURL(new File(filePath));

			try {
				definitionElement = (PoshiElement) PoshiNodeFactory.newPoshiNodeFromFile(url);
			}
			catch (Exception e) {
				System.out.println("Not translateable: " + filePath);

				e.printStackTrace();
			}

			String fileContent = FileUtil.read(filePath);

//			fileContent = removeWhiteSpace(fileContent);

			int start = filePath.lastIndexOf("/");
			int end = filePath.indexOf(fileType, start);

			String fileName = filePath.substring(start + 1, end);

			List<Element> baseCommandElements = new ArrayList<>();
			List<Element> baseOtherElements = new ArrayList<>();
			Element baseSetupElement = null;
			Element baseTeardownElement = null;

			if (fileContent.contains("<definition")) {
				Document document = Dom4JUtil.parse(fileContent);

				Element rootElement = document.getRootElement();

				removeWhiteSpaceTextNodes(rootElement);

				for (Iterator i = rootElement.elementIterator(); i.hasNext(); ) {
					Element element = (Element) i.next();

					if (element.getName().equals("command")) {
						baseCommandElements.add(element);
						totalTests++;

						continue;
					}

					if (element.getName().equals("set-up")) {
						baseSetupElement = element;

						continue;
					}

					if (element.getName().equals("tear-down")) {
						baseTeardownElement = element;

						continue;
					}

					baseOtherElements.add(element);
				}

				if (definitionElement == null) {
					continue;
				}

				if (areElementsEqual(rootElement, definitionElement)) {
					String poshiScript = "";

					try {
						poshiScript = definitionElement.toPoshiScript();
					}
					catch (NullPointerException npe) {
					}

					try {
						PoshiElement newPoshiElement = (PoshiElement) PoshiNodeFactory.newPoshiNode(poshiScript, url);

						if (areElementsEqual(rootElement, newPoshiElement)) {
							successfulFiles.add(fileName);

							testsFromSuccessfulFiles = testsFromSuccessfulFiles + baseCommandElements.size();
						}
						else {
							Patch patch = DiffUtils.diff(_stringToLines(Dom4JUtil.format(rootElement)), _stringToLines(Dom4JUtil.format(newPoshiElement)));

							System.out.println(fileName);

							for (Delta delta : patch.getDeltas()) {
								_printComparison(delta);
							}

							continue;
						}
					}
					catch (RuntimeException re) {
						System.out.println(filePath + " not translateable.");

						re.printStackTrace();

						continue;
					}
				}
			}

			List<PoshiElement> poshiOtherElements = new ArrayList<>();


			try {
				poshiOtherElements.addAll(definitionElement.elements("property"));
				poshiOtherElements.addAll(definitionElement.elements("var"));
			}
			catch (Exception e) {
			}

			if (poshiOtherElements.size() != baseOtherElements.size()) {
				continue;
			}

			boolean exit = false;

			for (int i = 0; i < poshiOtherElements.size(); i++) {
				PoshiElement poshiOtherElement = poshiOtherElements.get(i);

				Element baseOtherElement = baseOtherElements.get(i);

				if (!areElementsEqual(poshiOtherElement, baseOtherElement)) {
					exit = true;

//					System.out.println(i);

//					System.out.println("Tests with variable issues: " + filePath);

					break;
				}
			}

			if (exit) {
				varPropFailure = varPropFailure + baseCommandElements.size();

				continue;
			}

			if (baseSetupElement != null) {
				List<PoshiElement> poshiSetupElements = definitionElement.elements(
				"set-up");

				PoshiElement poshiSetupElement = poshiSetupElements.get(0);

				if (areElementsEqual(baseSetupElement, poshiSetupElement)) {
					String readableSetup = poshiSetupElement.toPoshiScript();

					try {
						PoshiElement newPoshiSetupElement = (PoshiElement) PoshiNodeFactory.newPoshiNode(definitionElement, readableSetup);

						if (!areElementsEqual(baseSetupElement, newPoshiSetupElement)) {
							System.out.println(fileName + " setup element issue, " + baseCommandElements.size() + " tests affected");
//						System.out.println(Dom4JUtil.format(newPoshiSetupElement));
//						System.out.println(readableSetup);
//						System.out.println(Dom4JUtil.format(baseSetupElement));

							setupFailure = setupFailure + baseCommandElements.size();
						}
					}
					catch (RuntimeException re) {

					}
				}
				else {
					System.out.println(fileName + " setup element issue, " + baseCommandElements.size() + " tests affected");
//					System.out.println(Dom4JUtil.format(poshiSetupElement));
//					System.out.println(Dom4JUtil.format(baseSetupElement));

					setupFailure = setupFailure + baseCommandElements.size();
				}
			}

			if (baseTeardownElement != null) {
				List<PoshiElement> poshiTearDownElements = definitionElement.elements(
				"tear-down");

				PoshiElement poshiTeardownElement = poshiTearDownElements.get(0);

				if (areElementsEqual(baseTeardownElement, poshiTeardownElement)) {
					String readableTeardown = poshiTeardownElement.toPoshiScript();

					PoshiElement newPoshiTeardownElement = (PoshiElement) PoshiNodeFactory.newPoshiNode(definitionElement, readableTeardown);

					if (!areElementsEqual(baseTeardownElement, newPoshiTeardownElement)) {
						System.out.println(fileName + " teardown element issue, " + baseCommandElements.size() + " tests affected");
//						System.out.println(Dom4JUtil.format(newPoshiTeardownElement));
//						System.out.println(readableTeardown);
//						System.out.println(Dom4JUtil.format(baseTeardownElement));

						teardownFailure = teardownFailure + baseCommandElements.size();

					}
				}
				else {
					System.out.println(fileName + " teardown element issue, " + baseCommandElements.size() + " tests affected");
//					System.out.println(Dom4JUtil.format(poshiTeardownElement));
//					System.out.println(Dom4JUtil.format(baseTeardownElement));

					teardownFailure = teardownFailure + baseCommandElements.size();

				}
			}

			List<PoshiElement> poshiCommandElements = definitionElement.elements("command");

			int commandElementFailuresPerFile = 0;

			for (int i = 0; i < poshiCommandElements.size(); i++) {
				PoshiElement poshiCommandElement = poshiCommandElements.get(i);

				Element baseCommandElement = baseCommandElements.get(i);

				if (!areElementsEqual(baseCommandElement, poshiCommandElement)) {
					String baseString = Dom4JUtil.format(baseCommandElement);
					String poshiString = Dom4JUtil.format(poshiCommandElement);

					Patch patch = DiffUtils.diff(_stringToLines(baseString), _stringToLines(poshiString));

					System.out.println(fileName + "#" + baseCommandElement.attributeValue("name"));

					for (Delta delta : patch.getDeltas()) {
						_printComparison(delta);

//						System.out.println(delta.getOriginal().toString());
//						System.out.println(delta.getRevised().toString());
					}

//					System.out.println(StringUtils.difference(baseString, poshiString));

//					System.out.println(Dom4JUtil.format(baseCommandElement));
//					System.out.println(Dom4JUtil.format(poshiCommandElement));

					commandElementFailure++;
					commandElementFailuresPerFile++;

					continue;
				}

				try {
					String readable = poshiCommandElement.toPoshiScript();

					PoshiElement newPoshiElement = (PoshiElement) PoshiNodeFactory.newPoshiNode(definitionElement, readable);

					if (areElementsEqual(baseCommandElement, newPoshiElement)) {
						successful++;

						String commandName = baseCommandElement.attributeValue("name");

						convertSuccess.add(fileName + "#" + commandName);
					}
					else {
//						System.out.println(Dom4JUtil.format(baseCommandElement));
//						System.out.println(Dom4JUtil.format(newPoshiElement));

						String baseString = Dom4JUtil.format(baseCommandElement);
						String newPoshiString = Dom4JUtil.format(newPoshiElement);

						Patch patch = DiffUtils.diff(_stringToLines(baseString), _stringToLines(newPoshiString));

						System.out.println(fileName + "#" + baseCommandElement.attributeValue("name"));

						for (Delta delta : patch.getDeltas()) {
							_printComparison(delta);

//							System.out.println(delta.getOriginal().toString());
//							System.out.println(delta.getRevised().toString());
						}

//						System.out.println(StringUtils.difference(baseString, newPoshiString));

						commandElementReadableToXMLFailure++;
						commandElementFailuresPerFile++;
					}
				}
				catch (RuntimeException re) {
					System.out.println(fileName);
					re.printStackTrace();
				}
			}

			if (commandElementFailuresPerFile > 0) {
				uniqueFiles++;
				uniqueFailuresTestCount = uniqueFailuresTestCount + baseCommandElements.size();

				System.out.println(fileName + " command element issue, " + commandElementFailuresPerFile + " failing commands, " + baseCommandElements.size() + " tests affected");
			}

			double currentTime = System.nanoTime();

//			System.out.println(fileName + " test time: \n" + (currentTime - lastTime) / 1000000000.0);
//			System.out.println("\tTotal elapsed time: " + (currentTime - startTime) / 1000000000.0);

			lastTime = currentTime;
		}

//		for (String testName : convertSuccess) {
//			System.out.println(testName);
//		}

		System.out.println("Total elapsed time: " + (System.nanoTime() - startTime) / 1000000000.0);

		for (String successfulFile : successfulFiles) {
			System.out.println(successfulFile);
		}

		System.out.println(successful + " / " + totalTests + " (" + (successful * 100 / totalTests) + "%) test commands were successfully translated");
		System.out.println(successfulFiles.size() + " / " + filePaths.size() + " (" + (successfulFiles.size() * 100 / filePaths.size()) + "%) test files were successfully translated accounting for  " + testsFromSuccessfulFiles + " / " + totalTests + " (" + (testsFromSuccessfulFiles * 100 / totalTests) + "%)" + " test commands");
		System.out.println(setupFailure + " / " + totalTests + " test commands fail due to setup failures");
		System.out.println(teardownFailure + " / " + totalTests + " test commands fail due to teardown failures");
		System.out.println("Test commands that are not properly stored in the element object: " + commandElementFailure);
		System.out.println("Test commands that fail two way conversion: " + commandElementReadableToXMLFailure);
		System.out.println(uniqueFiles + " test files with at least one problematic command element, accounting for " + uniqueFailuresTestCount + " untranslated tests");

		if (successful == totalTests) {
			return true;
		}

		return false;
	}

	private static void _printComparison(Delta delta) {
		Chunk original = delta.getOriginal();
		Chunk revised = delta.getRevised();

		List<?> originalLines = original.getLines();
		List<?> revisedLines = revised.getLines();

		if (originalLines.size() == revisedLines.size()) {
			boolean same = true;

			for (int i = 0; i < originalLines.size(); i++) {
				if (originalLines.get(i).toString().length() != revisedLines.get(i).toString().length()) {
					same = false;

					break;
				}
			}

			if (same) {
				return;
			}
		}

		_printCyanChunk(original);
		_printYellowChunk(revised);
	}

	private static void _printRedChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println("\033[31m" + line.toString() + "\033[0m");
		}
	}

	private static void _printCyanChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println("\033[36m" + line.toString() + "\033[0m");
		}
	}

	private static void _printGreenChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println("\033[32m" + line.toString() + "\033[0m");
		}
	}

	private static void _printYellowChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println("\033[33m" + line.toString() + "\033[0m");
		}
	}

	private static void _printChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println(line.toString());
		}
	}

	private static File _getFile(String filePath) {
		return new File(filePath);
	}

	private static void _removeWhiteSpace(Element element) {
		for (Node node : Dom4JUtil.toNodeList(element.content())) {
			if (node instanceof CDATA) {
				continue;
			}

			if (node instanceof Text) {
				String nodeText = node.getText();

				nodeText = nodeText.trim();

				if (nodeText.length() == 0) {
					node.detach();
				}
			}
		}

		for (Element childElement :
		Dom4JUtil.toElementList(element.elements())) {

			_removeWhiteSpace(childElement);
		}
	}

	private static List<String> _stringToLines(String s) {
		BufferedReader br = null;
		String line = "";
		List<String> lines = new LinkedList<String>();

		try {
			br = new BufferedReader(new StringReader(s));
			while ((line = br.readLine()) != null) {
				lines.add(line);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			if (br != null) {
				try {
					br.close();
				}
				catch (IOException e) {
				}
			}
		}
		return lines;
	}
}