package com.liferay.poshi.runner;

import com.liferay.poshi.core.elements.ExecutePoshiElement;
import com.liferay.poshi.core.elements.PoshiElement;
import com.liferay.poshi.core.elements.PoshiNodeFactory;
import com.liferay.poshi.core.util.Dom4JUtil;
import com.liferay.poshi.core.util.FileUtil;
import com.liferay.poshi.core.util.OSDetector;
import com.liferay.poshi.core.util.RegexUtil;
import com.liferay.poshi.runner.util.ExecUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.dom4j.Document;
import org.dom4j.Element;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * @author Kenji Heigel
 */
public class Sandbox {
	public static String testName = "PortalSmoke";
	public static String baseDir = "/Users/kenji/Projects/github/liferay-portal/master/portal-web/test/functional/com/liferay/portalweb/";
	public static String type = "testcase";

	public static void main(String[] args) throws Exception {
		Process process = ExecUtil.executeCommands("rsync -av ~/Desktop/debug.txt ~/Downloads/debug.txt");

//		Process process = ExecUtil.executeCommands("git status");

		System.out.println("test1");

//		InputStream inputStream = process.getInputStream();

//		Reader reader = new InputStreamReader(inputStream, "UTF-8");
//
//		int i = 0;
//
//		while ((i = reader.read()) != -1) {
//			char c = (char) i;
//
//			System.out.println(c);
//		}

		String response = ExecUtil.readInputStream(process.getInputStream(), true);

		System.out.println(response.length());

		System.out.println(response);
	}

	public static String _getTestName(String filePath) {
		int start = filePath.lastIndexOf("/");

		int end = filePath.indexOf("." + type, start);

		return filePath.substring(start + 1, end);
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

	public static boolean isBalanced(String string) {
		Stack<Character> stack = new Stack<>();

		for (char c : string.toCharArray()) {
			if (!stack.isEmpty()) {
				Character topCodeBoundary = stack.peek();

				if (c == _codeBoundariesMap.get(topCodeBoundary)) {
					stack.pop();

					continue;
				}

				if (topCodeBoundary == '\\') {
					stack.pop();

					continue;
				}
			}

			if (c == '\\') {
				stack.push(c);

				continue;
			}

			if (_codeBoundariesMap.containsKey(c)) {
				stack.push(c);

				continue;
			}

			if (_codeBoundariesMap.containsValue(c)) {
				return false;
			}
		}

		return stack.isEmpty();
	}

	private static final Map<Character, Character> _codeBoundariesMap =
		new HashMap<>();

	static {
		_codeBoundariesMap.put('\\', '\"');
		_codeBoundariesMap.put('\"', '\"');
		_codeBoundariesMap.put('(', ')');
		_codeBoundariesMap.put('{', '}');
		_codeBoundariesMap.put('[', ']');
	}
}
