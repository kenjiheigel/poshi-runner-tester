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

import com.liferay.poshi.core.util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CodeBalanceTester {

	public static final String filePath = "/Users/kenji/Projects/github/com-liferay-poshi-runner/poshi-runner/src/test/resources/com/liferay/poshi/runner/dependencies/elements/PoshiScript.macro";


	public static void main(String[] args) throws Exception {
		CodeBalanceTester codeBalanceTester = new CodeBalanceTester(FileUtil.read(filePath));

		System.out.println(codeBalanceTester.isBalanced());
	}

	public CodeBalanceTester(String string) {
		_chars = string.toCharArray();
	}

	public boolean isBalanced() {
		Result result = _isBalanced(null, 0);

		return result.isBalanced();
	}

	private static int _getTokenIndex(char c) {
		for (int i = 0; i < _tokens.length; i++) {
			if (c == _tokens[i]) {
				return i;
			}
		}

		return -1;
	}

	private static boolean _isCloser(int tokenIndex) {
		if ((tokenIndex % 2) == 0) {
			return false;
		}

		return true;
	}

	private static boolean _isOpener(int tokenIndex) {
		if ((tokenIndex % 2) == 0) {
			return true;
		}

		return false;
	}

	private String _getLine(int line) {
		try {
			return Files.readAllLines(Paths.get(filePath)).get(line - 1);
		}
		catch (IOException ioe) {
		}

		return null;
	}

	private String _getLocationString(int index) {
		int line = 1;

		int lastNewLineIndex = -1;

		for (int i = 0; i < index; i++) {
			if (_chars[i] == '\n') {
				line++;

				lastNewLineIndex = i;
			}
		}

		int column = 1;

		for (int i = lastNewLineIndex + 1; i < index; i++) {
			if (_chars[i] == '\t') {
				column += 4;

				continue;
			}

			column++;
		}

		StringBuilder sb = new StringBuilder();

		sb.append("line: ");
		sb.append(line);
		sb.append("\n");
		sb.append(_getLine(line));

		sb.append("\n");

		for (int i = 1; i < column; i++) {
			sb.append(" ");
		}

		sb.append("^");

		return sb.toString();
	}

	private Result _isBalanced(Opener opener, int index) {
		if (opener != null) {
			System.out.println("openerToken: " + opener.getCharacter());
		}

		int tokenIndex = -1;

		while (index < _chars.length) {
			char tokenChar = _chars[index];

			tokenIndex = _getTokenIndex(tokenChar);

			if (tokenIndex == -1) {
				index++;

				continue;
			}

			if (_isCloser(tokenIndex) && (opener != null)) {
				if (tokenIndex == (opener.getOpenerTokenIndex() + 1)) {
					return new Result(
						true, index,
						"Matching token found " + _getLocationString(index));
				}

				return new Result(
					false, index,
					"Mismatched closing token found " + _tokens[tokenIndex] +
						" " + _getLocationString(index));
			}

			if (_isOpener(tokenIndex)) {
				Result result = _isBalanced(
					new Opener(_tokens[tokenIndex], index, tokenIndex),
						index + 1);

				if (!result.isBalanced()) {
					return result;
				}

				return _isBalanced(opener, result.getEndIndex() + 1);
			}

			break;
		}

		if (tokenIndex == -1) {
			if (opener == null) {
				return new Result(true, index, "No more tokens");
			}

			return new Result(
				false, index,
				"Closing token not found opener: " + opener.getCharacter() +
					" " + _getLocationString(opener.getIndex()));
		}

		return new Result(
			false, index,
			"This point should never be reached " + _tokens[tokenIndex]);
	}

	private static char[] _tokens = {'{', '}', '(', ')', '[', ']'};

	private final char[] _chars;

	private class Opener {
		private char _character;
		private int _index;
		private int _openerTokenIndex;

		public Opener (char character, int index, int openerTokenIndex) {
			_character = character;
			_index = index;
			_openerTokenIndex = openerTokenIndex;
		}

		public char getCharacter() {
			return _character;
		}

		public int getIndex() {
			return _index;
		}

		public int getOpenerTokenIndex() {
			return _openerTokenIndex;
		}
	}

	private class Result {

		public Result(boolean balanced, int endIndex, String message) {
			_balanced = balanced;
			_endIndex = endIndex;
			_message = message;

			System.out.println(getMessage());
		}

		public int getEndIndex() {
			return _endIndex;
		}

		public String getMessage() {
			return _message;
		}

		public boolean isBalanced() {
			return _balanced;
		}

		private final boolean _balanced;
		private final int _endIndex;
		private final String _message;

	}

}
