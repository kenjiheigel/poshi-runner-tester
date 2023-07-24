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

import com.google.gson.internal.LinkedTreeMap;

import com.liferay.poshi.core.util.FileUtil;
import com.liferay.poshi.core.util.RegexUtil;
import com.liferay.poshi.core.util.StringUtil;

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.IssueLinksApi;
import io.swagger.client.api.IssuesApi;
import io.swagger.client.model.IssueBean;
import io.swagger.client.model.IssueLinkType;
import io.swagger.client.model.LinkIssueRequestJsonBean;
import io.swagger.client.model.LinkedIssue;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Arrays;
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

	public static final File PORTAL_DIR = new File(
		"/opt/dev/projects/github/liferay-portal");

	public static final String RELEASE_TICKET = "";

	public static final List<String> ignorableTickets = Arrays.asList();

	public static void main(String[] args)
		throws ApiException, GitAPIException, IOException {

		if (RELEASE_TICKET.isEmpty()) {
			throw new RuntimeException(
				"Please set RELEASE_TICKET to a valid JIRA ticket ID");
		}

		Git git = Git.open(PORTAL_DIR);

		Repository repository = git.getRepository();

		ObjectId currentSHA = repository.resolve(repository.getBranch());

		LogCommand bndLogCommand = git.log();

		String bndPath = _POSHI_DIR_PATH + "/poshi-runner/bnd.bnd";

		bndLogCommand.add(currentSHA);
		bndLogCommand.addPath(bndPath);
		bndLogCommand.setMaxCount(50);

		Iterable<RevCommit> bndCommits = bndLogCommand.call();

		ObjectId lastReleaseSHA = null;
		ObjectId releaseSHA = null;

		int i = 1;
		String lastReleasedVersion = _getLastReleasedVersion();
		String releaseVersion = _getReleaseVersion();

		for (RevCommit commit : bndCommits) {
			String content = _getFileContentAtCommit(git, commit, bndPath);

			if (i == 2) {
				Matcher matcher = _bundleVersionPattern.matcher(content);

				if (matcher.find()) {
					String nextReleaseVersion = _getNextVersion(releaseVersion);

					if (nextReleaseVersion.equals(matcher.group(1))) {
						releaseSHA = commit.getId();
					}
				}
			}

			if (content.contains(lastReleasedVersion)) {
				lastReleaseSHA = commit.getId();

				break;
			}

			i++;
		}

		LogCommand poshiDirLogCommand = git.log();

		poshiDirLogCommand.addPath(_POSHI_DIR_PATH);
		poshiDirLogCommand.addPath("modules/sdk/gradle-plugins-poshi-runner");
		poshiDirLogCommand.addRange(lastReleaseSHA, releaseSHA);

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

		for (String ignorableTicket : ignorableTickets) {
			tickets.remove(ignorableTicket);
		}

		String ticketListString = tickets.toString();

		ticketListString = StringUtil.replace(ticketListString, "[", "(");
		ticketListString = StringUtil.replace(ticketListString, "]", ")");

		ticketListString = URLEncoder.encode(ticketListString, "UTF-8");

		System.out.println(
			"https://liferay.atlassian.net/issues/?jql=key%20in" +
				ticketListString);

		ApiClient apiClient = new ApiClient();

		apiClient.setBasePath("https://liferay.atlassian.net/");
		apiClient.setDebugging(false);
		apiClient.setPassword(Authentication.JIRA_PASSWORD);
		apiClient.setUsername(Authentication.JIRA_USERNAME);

		IssuesApi issuesApi = new IssuesApi(apiClient);

		IssueBean releaseIssueBean = _getIssueBean(issuesApi, RELEASE_TICKET);

		Map<String, Object> releaseIssueBeanFields =
			releaseIssueBean.getFields();

		LinkedTreeMap releaseIssueBeanFieldsStatus = _getJSONMap(
			releaseIssueBeanFields.get("status"));

		String releaseIssueStatusName =
			(String)releaseIssueBeanFieldsStatus.get("name");

		if (releaseIssueStatusName.equals("Closed")) {
			throw new RuntimeException(
				"https://liferay.atlassian.net/browse/" + RELEASE_TICKET +
					" is closed. Verify correct ticket.");
		}

		List<String> missingComponentTickets = new ArrayList<>();
		Map<String, List<IssueBean>> ticketGroups = new TreeMap<>();

		for (String ticketID : tickets) {
			IssueBean issueBean = _getIssueBean(issuesApi, ticketID);

			_linkIssues(apiClient, RELEASE_TICKET, ticketID, "Relationship");

			Map<String, Object> issueBeanFields = issueBean.getFields();

			if (ticketID.startsWith("LRCI") || ticketID.startsWith("LRQA")) {
				boolean missingLabel = true;

				for (String label :
						(List<String>)issueBeanFields.get("labels")) {

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
								label,
								new ArrayList<>(Arrays.asList(issueBean)));

							break;
						}

						List<IssueBean> ticketList = ticketGroups.get(label);

						ticketList.add(issueBean);

						break;
					}
				}

				if (missingLabel) {
					System.out.println(
						"Missing poshi label: " + _getTicketURL(ticketID));

					if (!ticketGroups.containsKey("Other")) {
						ticketGroups.put(
							"Other", new ArrayList<>(Arrays.asList(issueBean)));

						continue;
					}

					List<IssueBean> issues = ticketGroups.get("Other");

					issues.add(issueBean);
				}
			}
			else if (ticketID.startsWith("POSHI")) {
				List<Object> components = (List<Object>)issueBeanFields.get(
					"components");

				if (components.isEmpty()) {
					System.out.println(
						"Missing component: " + _getTicketURL(ticketID));

					missingComponentTickets.add(ticketID);
				}

				for (Object component : components) {
					Map<String, Object> componentMap = _getJSONMap(component);

					String componentName = (String)componentMap.get("name");

					if (componentName.equals("IDE") ||
						componentName.equals("Release")) {

						break;
					}

					if (!ticketGroups.containsKey(componentName)) {
						ticketGroups.put(
							componentName,
							new ArrayList<>(Arrays.asList(issueBean)));

						break;
					}

					List<IssueBean> issues = ticketGroups.get(componentName);

					issues.add(issueBean);
				}
			}
			else {
				if (!ticketGroups.containsKey("Other")) {
					ticketGroups.put(
						"Other", new ArrayList<>(Arrays.asList(issueBean)));

					continue;
				}

				List<IssueBean> ticketList = ticketGroups.get("Other");

				ticketList.add(issueBean);
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

		_updateChangelogFile(ticketGroups, releaseVersion);

		_updateJIRAIssueBodyText(ticketGroups, RELEASE_TICKET, apiClient);

		_copyHTMLTextToClipboard(
			_getSlackPost(ticketGroups, releaseVersion), "plaintext");
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
		Map<String, List<IssueBean>> ticketGroups, String releaseVersion) {

		StringBuilder sb = new StringBuilder();

		sb.append("# Poshi Runner Change Log\n");
		sb.append("\n## " + releaseVersion + "\n");

		for (Map.Entry<String, List<IssueBean>> entry :
				ticketGroups.entrySet()) {

			String label = entry.getKey();

			sb.append("\n### " + label + "\n\n");

			for (IssueBean issueBean : entry.getValue()) {
				Map<String, Object> issueBeanFields = issueBean.getFields();

				sb.append(
					"* " + _getTicketMarkdownURL(issueBean.getKey()) + " - " +
						issueBeanFields.get("summary") + "\n");
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

	private static IssueBean _getIssueBean(IssuesApi issuesApi, String ticketID)
		throws ApiException {

		return issuesApi.getIssue(ticketID, null, null, null, null, null);
	}

	private static LinkedTreeMap<String, Object> _getJSONMap(Object object) {
		if (object instanceof LinkedTreeMap) {
			return (LinkedTreeMap)object;
		}

		throw new RuntimeException("Not a JSON map");
	}

	private static String _getLastReleasedVersion() throws IOException {
		File changelogFile = new File(PORTAL_DIR, _CHANGELOG_FILE_PATH);

		if (_lastReleasedVersion != null) {
			return _lastReleasedVersion;
		}

		BufferedReader b = new BufferedReader(new FileReader(changelogFile));

		String readLine = "";

		while ((readLine = b.readLine()) != null) {
			if (readLine.startsWith("##")) {
				String releaseVersion = RegexUtil.getGroup(
					readLine, "##[\\s]*(.*)", 1);

				_lastReleasedVersion = _getNextVersion(releaseVersion);

				return _lastReleasedVersion;
			}
		}

		throw new RuntimeException("Could not find last released version");
	}

	private static String _getNextVersion(String version) {
		version = version.trim();

		String patchVersion = RegexUtil.getGroup(
			version, "[\\d]+\\.[\\d]+\\.([\\d]+)", 1);

		Integer patchVersionInteger = 0;

		try {
			patchVersionInteger = Integer.parseInt(patchVersion);
		}
		catch (NumberFormatException numberFormatException) {
			throw new RuntimeException(numberFormatException);
		}

		patchVersionInteger++;

		String newPatchVersion = patchVersionInteger.toString();

		return version.replace(patchVersion, newPatchVersion);
	}

	private static String _getReleaseVersion() throws IOException {
		File file = new File(
			PORTAL_DIR,
			"modules/sdk/gradle-plugins-poshi-runner/src/main/java/com" +
				"/liferay/gradle/plugins/poshi/runner" +
					"/PoshiRunnerExtension.java");

		BufferedReader b = new BufferedReader(new FileReader(file));

		String readLine = "";

		while ((readLine = b.readLine()) != null) {
			if (readLine.startsWith("\tprivate Object _version")) {
				return RegexUtil.getGroup(readLine, ".*\"(.*)\"", 1);
			}
		}

		throw new RuntimeException("Unable to get release version");
	}

	private static String _getSlackPost(
		Map<String, List<IssueBean>> ticketGroups, String releaseVersion) {

		StringBuilder sb = new StringBuilder();

		sb.append("<b>New Poshi Release(<a href=\"");

		sb.append(_getTicketURL(RELEASE_TICKET));

		sb.append("\">");

		sb.append(releaseVersion);

		sb.append("</a>)(<a href=\"");
		sb.append("https://github.com/liferay/liferay-portal/blob/master/");
		sb.append("modules/test/poshi/CHANGELOG.markdown");
		sb.append("\">Changelog</a>)</b><br/><br/>");

		for (Map.Entry<String, List<IssueBean>> entry :
				ticketGroups.entrySet()) {

			String label = entry.getKey();

			sb.append("<b><em>" + label + "</em></b><br/><ul>");

			for (IssueBean issueBean : entry.getValue()) {
				Map<String, Object> issueBeanFields = issueBean.getFields();

				sb.append("<li><a href=\"");
				sb.append(_getTicketURL(issueBean.getKey()));
				sb.append("\">");
				sb.append(issueBean.getKey());
				sb.append("</a>");
				sb.append(" - ");
				sb.append(issueBeanFields.get("summary"));
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
		return "https://liferay.atlassian.net/browse/" + ticketID;
	}

	private static void _linkIssues(
			ApiClient apiClient, String inwardIssueKey, String outwardIssueKey,
			String linkTypeName)
		throws ApiException {

		if (inwardIssueKey.equals(outwardIssueKey)) {
			return;
		}

		IssueLinksApi issueLinksApi = new IssueLinksApi(apiClient);

		LinkIssueRequestJsonBean linkIssueRequestJsonBean =
			new LinkIssueRequestJsonBean();

		LinkedIssue inwardIssue = new LinkedIssue();

		linkIssueRequestJsonBean.setInwardIssue(
			inwardIssue.key(inwardIssueKey));

		LinkedIssue outwardIssue = new LinkedIssue();

		linkIssueRequestJsonBean.setOutwardIssue(
			outwardIssue.key(outwardIssueKey));

		IssueLinkType issueLinkType = new IssueLinkType();

		linkIssueRequestJsonBean.setType(issueLinkType.name(linkTypeName));

		issueLinksApi.linkIssues(linkIssueRequestJsonBean);
	}

	private static void _updateChangelogFile(
			Map<String, List<IssueBean>> ticketGroups, String releaseVersion)
		throws IOException {

		File changelogFile = new File(PORTAL_DIR, _CHANGELOG_FILE_PATH);

		String changeLogText = FileUtil.read(changelogFile);

		changeLogText = changeLogText.replaceFirst(
			"# Poshi Runner Change Log\n",
			_getChangelogText(ticketGroups, releaseVersion));

		Files.write(changelogFile.toPath(), changeLogText.getBytes());
	}

	private static void _updateJIRAIssueBodyText(
			Map<String, List<IssueBean>> ticketGroups, String releaseTicketID,
			ApiClient apiClient)
		throws ApiException {

		StringBuilder sb = new StringBuilder();

		for (Map.Entry<String, List<IssueBean>> entry :
				ticketGroups.entrySet()) {

			String label = entry.getKey();

			sb.append("_" + label + "_\n");

			for (IssueBean issueBean : entry.getValue()) {
				Map<String, Object> issueBeanFields = issueBean.getFields();

				sb.append(
					"* " + issueBean.getKey() + " - " +
						issueBeanFields.get("summary") + "\n");
			}

			sb.append("\n");
		}

		IssuesApi issuesApi = new IssuesApi(apiClient);

		IssueBean issueBean = _getIssueBean(issuesApi, releaseTicketID);

		// TO DO

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

	private static final String _CHANGELOG_FILE_PATH =
		"modules/test/poshi/CHANGELOG.markdown";

	private static final String _POSHI_DIR_PATH = "modules/test/poshi";

	private static final Pattern _bundleVersionPattern = Pattern.compile(
		"Bundle-Version:[\\s]*(.*)");
	private static String _lastReleasedVersion = null;
	private static final Pattern _ticketPattern = Pattern.compile(
		"(LPS|LRQA|LRCI|POSHI)-[0-9]+");

}