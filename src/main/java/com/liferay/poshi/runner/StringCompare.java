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

import difflib.Chunk;
import difflib.Delta;

/**
 * @author Kenji Heigel
 */
public class StringCompare {

	public static void print(Delta delta) {
		Chunk original = delta.getOriginal();
		Chunk revised = delta.getRevised();

		/*
				List<?> originalLines = original.getLines();
				List<?> revisedLines = revised.getLines();

				if (originalLines.size() == revisedLines.size()) {
					boolean same = true;

					for (int i = 0; i < originalLines.size(); i++) {
						if (originalLines.get(i).toString().length() !=
								revisedLines.get(i).toString().length()) {

							same = false;

							break;
						}
					}

					if (false) {
						return;
					}
				}
		*/
		_printCyanChunk(original);
		_printYellowChunk(revised);
	}

	private static void _printCyanChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println("\033[36m" + line.toString() + "\033[0m");
		}
	}

	private static void _printYellowChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println("\033[33m" + line.toString() + "\033[0m");
		}
	}

	private void _printChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println(line.toString());
		}
	}

	private void _printGreenChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println("\033[32m" + line.toString() + "\033[0m");
		}
	}

	private void _printRedChunk(Chunk chunk) {
		for (Object line : chunk.getLines()) {
			System.out.println("\033[31m" + line.toString() + "\033[0m");
		}
	}

}