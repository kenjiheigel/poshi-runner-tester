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

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Status;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.LinkIssuesInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import com.liferay.poshi.core.util.FileUtil;
import com.liferay.poshi.core.util.RegexUtil;
import com.liferay.poshi.core.util.StringUtil;

import io.atlassian.util.concurrent.Promise;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * @author Kenji Heigel
 */
public class ChangelogGenerator {

	public static final String PORTAL_DIR =
		"/opt/dev/projects/github/liferay-portal";

	public static final String RELEASE_TICKET = "POSHI-448";

	public static void main(String[] args)
		throws GitAPIException, IOException, URISyntaxException {

		File gitWorkDir = new File(PORTAL_DIR);

		String poshiDirPath = "modules/test/poshi";

		String bndPath = poshiDirPath + "/poshi-runner/bnd.bnd";

		String changelogPath = poshiDirPath + "/CHANGELOG.markdown";

		File changelogFile = new File(gitWorkDir, changelogPath);

		Git git = Git.open(gitWorkDir);

		Repository repository = git.getRepository();

		ObjectId newReleaseSHA = repository.resolve(repository.getBranch());

		LogCommand bndLogCommand = git.log();

		bndLogCommand.add(newReleaseSHA);
		bndLogCommand.addPath(bndPath);
		bndLogCommand.setMaxCount(50);

		Iterable<RevCommit> bndCommits = bndLogCommand.call();

		ObjectId lastReleaseSHA = null;

		int i = 1;
		String releaseVersion = "";

		for (RevCommit commit : bndCommits) {
			String content = _getFileContentAtCommit(git, commit, bndPath);

			if (i == 2) {
				Matcher matcher = _bundleVersionPattern.matcher(content);

				if (matcher.find()) {
					releaseVersion = matcher.group(1);
				}
			}

			if (content.contains(_getLastReleasedVersion(changelogFile))) {
				lastReleaseSHA = commit.getId();

				break;
			}

			i++;
		}

		LogCommand poshiDirLogCommand = git.log();

		poshiDirLogCommand.addPath(poshiDirPath);
		poshiDirLogCommand.addPath("modules/sdk/gradle-plugins-poshi-runner");
		poshiDirLogCommand.addRange(lastReleaseSHA, newReleaseSHA);

		Iterable<RevCommit> commits = poshiDirLogCommand.call();

		Set<String> tickets = new TreeSet<>();

		for (RevCommit commit : commits) {
			String commitMessage = commit.getFullMessage();

			commitMessage = commitMessage.trim();

			Matcher matcher = _ticketPattern.matcher(commitMessage);

			if (matcher.find()) {
				String ticketID = matcher.group();

				tickets.add(ticketID);
			}
		}

		JiraRestClientFactory jiraRestClientFactory =
			new AsynchronousJiraRestClientFactory();

		URI uri = new URI("https://issues.liferay.com");

		JiraRestClient jiraRestClient =
			jiraRestClientFactory.createWithBasicHttpAuthentication(
				uri, Authentication.JIRA_USERNAME,
				Authentication.JIRA_PASSWORD);

		String ticketListString = tickets.toString();

		ticketListString = StringUtil.replace(ticketListString, "[", "(");
		ticketListString = StringUtil.replace(ticketListString, "]", ")");

		ticketListString = URLEncoder.encode(ticketListString, "UTF-8");

		System.out.println(
			"https://issues.liferay.com/issues/?jql=key%20in" +
				ticketListString);

		IssueRestClient issueRestClient = jiraRestClient.getIssueClient();

		Issue releaseIssue = _getIssue(issueRestClient, RELEASE_TICKET);

		Status releaseIssueStatus = releaseIssue.getStatus();

		String releaseIssueStatusName = releaseIssueStatus.getName();

		if (releaseIssueStatusName.equals("Closed")) {
			throw new RuntimeException(
				"https://issues.liferay.com/browse/" + RELEASE_TICKET +
					" is closed. Verify correct ticket.");
		}

		List<String> missingComponentTickets = new ArrayList<>();
		Map<String, List<Issue>> ticketGroups = new TreeMap<>();

		for (String ticketID : tickets) {
			Issue issue = _getIssue(issueRestClient, ticketID);

			LinkIssuesInput linkIssuesInput = new LinkIssuesInput(
				RELEASE_TICKET, ticketID, "Relationship");

			issueRestClient.linkIssue(linkIssuesInput);

			if (ticketID.startsWith("LRCI") || ticketID.startsWith("LRQA")) {
				boolean missingLabel = true;

				for (String label : issue.getLabels()) {
					if (label.startsWith("poshi_")) {
						label = _upperCaseEachWord(
							StringUtil.replace(label, "_", " "));

						label = StringUtil.replace(label, "Poshi ", "");

						if (label.equals("Pql")) {
							label = StringUtil.upperCase(label);
						}

						missingLabel = false;

						if (!ticketGroups.containsKey(label)) {
							ticketGroups.put(
								label, new ArrayList<>(Arrays.asList(issue)));

							break;
						}

						List<Issue> ticketList = ticketGroups.get(label);

						ticketList.add(issue);

						break;
					}
				}

				if (missingLabel) {
					System.out.println(
						"Missing poshi label: " + _getTicketURL(ticketID));

					if (!ticketGroups.containsKey("Other")) {
						ticketGroups.put(
							"Other", new ArrayList<>(Arrays.asList(issue)));

						continue;
					}

					List<Issue> issues = ticketGroups.get("Other");

					issues.add(issue);
				}
			}
			else if (ticketID.startsWith("POSHI")) {
				Iterable<BasicComponent> iterable = issue.getComponents();

				Iterator<BasicComponent> iterator = iterable.iterator();

				if ((iterable == null) || !iterator.hasNext()) {
					System.out.println(
						"Missing component: " + _getTicketURL(ticketID));

					missingComponentTickets.add(ticketID);
				}

				for (BasicComponent basicComponent : issue.getComponents()) {
					String componentName = basicComponent.getName();

					if (componentName.equals("IDE") ||
						componentName.equals("Release")) {

						break;
					}

					if (!ticketGroups.containsKey(componentName)) {
						ticketGroups.put(
							componentName,
							new ArrayList<>(Arrays.asList(issue)));

						break;
					}

					List<Issue> issues = ticketGroups.get(componentName);

					issues.add(issue);
				}
			}
			else {
				if (!ticketGroups.containsKey("Other")) {
					ticketGroups.put(
						"Other", new ArrayList<>(Arrays.asList(issue)));

					continue;
				}

				List<Issue> ticketList = ticketGroups.get("Other");

				ticketList.add(issue);
			}
		}

		if (!missingComponentTickets.isEmpty()) {
			StringBuilder sb = new StringBuilder();

			sb.append("Please add the proper 'Component' to the following ");
			sb.append("tickets and rerun this class:");

			for (String missingComponentTicket : missingComponentTickets) {
				sb.append("\n");
				sb.append(_getTicketURL(missingComponentTicket));
			}

			throw new RuntimeException(sb.toString());
		}

		_updateChangelogFile(changelogFile, ticketGroups, releaseVersion);

		_updateJIRAIssueBodyText(ticketGroups, RELEASE_TICKET, issueRestClient);

		_copyHTMLTextToClipboard(
			_getSlackPost(ticketGroups, releaseVersion), "plaintext");

		jiraRestClient.close();
	}

	public static class HtmlTransferable implements Transferable {

		public HtmlTransferable(String htmlText, String plainText) {
			_htmlText = htmlText;
			_plainText = plainText;
		}

		@Override
		public Object getTransferData(DataFlavor flavor)
			throws UnsupportedFlavorException {

			String transferData = _plainText;

			if (flavor == DataFlavor.stringFlavor) {
				transferData = _plainText;
			}
			else if (flavor == DataFlavor.allHtmlFlavor) {
				transferData = _htmlText;
			}

			if (String.class.equals(flavor.getRepresentationClass())) {
				return transferData;
			}

			throw new UnsupportedFlavorException(flavor);
		}

		public DataFlavor[] getTransferDataFlavors() {
			return _htmlDataFlavors.toArray(new DataFlavor[0]);
		}

		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return _htmlDataFlavors.contains(flavor);
		}

		private static final List<DataFlavor> _htmlDataFlavors =
			new ArrayList<DataFlavor>() {
				{
					add(DataFlavor.stringFlavor);
					add(DataFlavor.allHtmlFlavor);
				}
			};

		private String _htmlText;
		private String _plainText;

	}

	private static void _copyHTMLTextToClipboard(
		String htmlText, String plainText) {

		HtmlTransferable htmlTransferable = new HtmlTransferable(
			htmlText, plainText);

		Toolkit toolkit = Toolkit.getDefaultToolkit();

		Clipboard clipboard = toolkit.getSystemClipboard();

		clipboard.setContents(htmlTransferable, null);

		System.out.println(
			"\nThe following formatted HTML text has been copied to the " +
				"clipboard:");
		System.out.println(htmlText);
	}

	private static String _getChangelogText(
		Map<String, List<Issue>> ticketGroups, String releaseVersion) {

		StringBuilder sb = new StringBuilder();

		sb.append("# Poshi Runner Change Log\n");
		sb.append("\n## " + releaseVersion + "\n");

		for (Map.Entry<String, List<Issue>> entry : ticketGroups.entrySet()) {
			String label = entry.getKey();

			sb.append("\n### " + label + "\n\n");

			for (Issue issue : entry.getValue()) {
				sb.append(
					"* " + _getTicketMarkdownURL(issue.getKey()) + " - " +
						issue.getSummary() + "\n");
			}

		}

		return sb.toString();
	}

	private static String _getFileContentAtCommit(
			Git git, RevCommit commit, String path)
		throws IOException {

		Repository repository = git.getRepository();

		try (TreeWalk treeWalk = TreeWalk.forPath(
				repository, path, commit.getTree())) {

			ObjectLoader objectLoader = repository.open(
				treeWalk.getObjectId(0));

			byte[] bytes = objectLoader.getBytes();

			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	private static Issue _getIssue(
		IssueRestClient issueRestClient, String ticketID) {

		Promise<Issue> promise = issueRestClient.getIssue(ticketID);

		return promise.claim();
	}

	private static String _getLastReleasedVersion(File changelogFile)
		throws IOException {

		if (_lastReleasedVersion != null) {
			return _lastReleasedVersion;
		}

		BufferedReader b = new BufferedReader(new FileReader(changelogFile));

		String readLine = "";

		while ((readLine = b.readLine()) != null) {
			if (readLine.startsWith("##")) {
				String releaseVersion = RegexUtil.getGroup(
					readLine, "##[\\s]*(.*)", 1);

				releaseVersion = releaseVersion.trim();

				String patchVersion = RegexUtil.getGroup(
					releaseVersion, "[\\d]+\\.[\\d]+\\.([\\d]+)", 1);

				Integer patchVersionInteger = 0;

				try {
					patchVersionInteger = Integer.parseInt(patchVersion);
				}
				catch (NumberFormatException numberFormatException) {
					throw new RuntimeException(numberFormatException);
				}

				patchVersionInteger++;

				String newPatchVersion = patchVersionInteger.toString();

				releaseVersion = releaseVersion.replace(
					patchVersion, newPatchVersion);

				_lastReleasedVersion = releaseVersion;

				return _lastReleasedVersion;
			}
		}

		throw new RuntimeException("Could not find last released version");
	}

	private static String _getSlackPost(
		Map<String, List<Issue>> ticketGroups, String releaseVersion) {

		StringBuilder sb = new StringBuilder();

		sb.append("<b>New Poshi Release(<a href=\"");

		sb.append(_getTicketURL(RELEASE_TICKET));

		sb.append("\">");

		sb.append(releaseVersion);

		sb.append("</a>)(<a href=\"");
		sb.append("https://github.com/liferay/liferay-portal/blob/master/");
		sb.append("modules/test/poshi/CHANGELOG.markdown");
		sb.append("\">Changelog</a>)</b><br/><br/>");

		for (Map.Entry<String, List<Issue>> entry : ticketGroups.entrySet()) {
			String label = entry.getKey();

			sb.append("<b><em>" + label + "</em></b><br/><ul>");

			for (Issue issue : entry.getValue()) {
				sb.append("<li><a href=\"");
				sb.append(_getTicketURL(issue.getKey()));
				sb.append("\">");
				sb.append(issue.getKey());
				sb.append("</a>");
				sb.append(" - ");
				sb.append(issue.getSummary());
				sb.append("</li>");
			}

			sb.append("</ul>");
		}

		return sb.toString();
	}

	private static String _getTicketMarkdownURL(String ticketID) {
		StringBuilder sb = new StringBuilder();

		sb.append("[");

		sb.append(ticketID);

		sb.append("]");
		sb.append("(");

		sb.append(_getTicketURL(ticketID));

		sb.append(")");

		return sb.toString();
	}

	private static String _getTicketURL(String ticketID) {
		return "https://issues.liferay.com/browse/" + ticketID;
	}

	private static void _updateChangelogFile(
			File changelogFile, Map<String, List<Issue>> ticketGroups,
			String releaseVersion)
		throws IOException {

		String changeLogText = FileUtil.read(changelogFile);

		changeLogText = changeLogText.replaceFirst(
			"# Poshi Runner Change Log\n",
			_getChangelogText(ticketGroups, releaseVersion));

		Files.write(changelogFile.toPath(), changeLogText.getBytes());
	}

	private static void _updateJIRAIssueBodyText(
		Map<String, List<Issue>> ticketGroups, String releaseTicketID,
		IssueRestClient issueRestClient) {

		StringBuilder sb = new StringBuilder();

		for (Map.Entry<String, List<Issue>> entry : ticketGroups.entrySet()) {
			String label = entry.getKey();

			sb.append("_" + label + "_\n");

			for (Issue issue : entry.getValue()) {
				sb.append(
					"* " + issue.getKey() + " - " + issue.getSummary() + "\n");
			}

			sb.append("\n");
		}

		IssueInputBuilder issueInput = new IssueInputBuilder();

		issueInput.setDescription(sb.toString());

		issueRestClient.updateIssue(releaseTicketID, issueInput.build());
	}

	private static String _upperCaseEachWord(String s) {
		char[] chars = s.toCharArray();

		chars[0] = Character.toUpperCase(chars[0]);

		for (int x = 1; x < chars.length; x++) {
			if (chars[x - 1] == ' ') {
				chars[x] = Character.toUpperCase(chars[x]);
			}
		}

		return new String(chars);
	}

	private static final Pattern _bundleVersionPattern = Pattern.compile(
		"Bundle-Version:[\\s]*(.*)");
	private static String _lastReleasedVersion = null;
	private static final Pattern _ticketPattern = Pattern.compile(
		"(LPS|LRQA|LRCI|POSHI)-[0-9]+");

}