package com.robin.magic_realm.RealmSpeak.update;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub Releases for updates.
 *
 * Tag format expected: "<baseVersion>-<qualifier>.<build>" (e.g. "1264-inigone.2")
 * or just "<baseVersion>" for upstream tags (e.g. "1264").
 *
 * Version comparison: higher baseVersion wins; for equal baseVersion, higher build wins;
 * tags with a qualifier beat bare baseVersion tags (any IL build > base with no qualifier).
 */
public class GitHubUpdateSource implements UpdateSource {

	private static final String API_BASE = "https://api.github.com";
	private static final Pattern TAG_PATTERN = Pattern.compile("(\\d+)(?:-([^.]+)\\.(\\d+))?");

	private final String owner;
	private final String repo;
	private final String sourceName;
	private final String assetPattern; // regex matching the zip asset name

	/**
	 * @param owner       GitHub repo owner (e.g. "inigone")
	 * @param repo        GitHub repo name (e.g. "RealmSpeak")
	 * @param sourceName  label for UI (e.g. "inigone")
	 * @param assetPattern regex for the zip asset to download (e.g. "RealmSpeak.*\\.zip")
	 */
	public GitHubUpdateSource(String owner, String repo, String sourceName, String assetPattern) {
		this.owner = owner;
		this.repo = repo;
		this.sourceName = sourceName;
		this.assetPattern = assetPattern;
	}

	@Override
	public String getSourceName() {
		return sourceName;
	}

	@Override
	public Optional<UpdateInfo> checkForUpdate(String currentTag) throws IOException {
		String json = fetchLatestReleaseJson();
		String latestTag = extractString(json, "tag_name");
		if (latestTag == null) {
			throw new IOException("Could not parse tag_name from GitHub response");
		}
		if (!isNewer(latestTag, currentTag)) {
			return Optional.empty();
		}
		String downloadUrl = findZipAssetUrl(json);
		if (downloadUrl == null) {
			throw new IOException("No matching zip asset found in release " + latestTag);
		}
		String pageUrl = extractString(json, "html_url");
		String body = extractString(json, "body");
		return Optional.of(new UpdateInfo(latestTag, downloadUrl, pageUrl, body == null ? "" : body));
	}

	private String fetchLatestReleaseJson() throws IOException {
		URL url = new URL(API_BASE + "/repos/" + owner + "/" + repo + "/releases/latest");
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(10_000);
		conn.setReadTimeout(15_000);
		conn.setRequestProperty("Accept", "application/vnd.github+json");
		conn.setRequestProperty("User-Agent", "RealmSpeak-Updater");
		conn.setRequestMethod("GET");
		int status = conn.getResponseCode();
		if (status == 404) throw new IOException("No releases found for " + owner + "/" + repo);
		if (status != 200) throw new IOException("GitHub API returned HTTP " + status);
		try (InputStream in = conn.getInputStream();
			 ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
			byte[] chunk = new byte[8192];
			int n;
			while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
			return buf.toString(StandardCharsets.UTF_8);
		} finally {
			conn.disconnect();
		}
	}

	/** True if latestTag is strictly newer than currentTag (or currentTag is null/empty). */
	boolean isNewer(String latestTag, String currentTag) {
		if (currentTag == null || currentTag.isBlank()) return true;
		int[] latest = parseTag(latestTag);
		int[] current = parseTag(currentTag);
		if (latest[0] != current[0]) return latest[0] > current[0]; // base version differs
		// Same base version: compare build numbers (-1 means no qualifier = base tag)
		return latest[1] > current[1];
	}

	/**
	 * Returns [baseVersion, buildNumber] where buildNumber is -1 for bare base tags
	 * (no qualifier) and 0+ for qualified tags.
	 * Strips a leading non-digit prefix (e.g. "RealmSpeak") before parsing,
	 * so "RealmSpeak1263-inigone.3" and "1263-inigone.3" both work.
	 */
	private int[] parseTag(String tag) {
		String normalized = tag.trim().replaceFirst("^\\D+", "");
		Matcher m = TAG_PATTERN.matcher(normalized);
		if (!m.find()) return new int[]{0, -1};
		int base = Integer.parseInt(m.group(1));
		if (m.group(3) == null) return new int[]{base, -1}; // bare tag: "1264"
		int build = Integer.parseInt(m.group(3));
		return new int[]{base, build};
	}

	/**
	 * Extracts the string value for the first occurrence of "key" in JSON.
	 * Uses a character scanner rather than regex to handle arbitrarily large values safely.
	 */
	private String extractString(String json, String key) {
		String needle = "\"" + key + "\"";
		int keyPos = json.indexOf(needle);
		if (keyPos < 0) return null;
		int colon = json.indexOf(':', keyPos + needle.length());
		if (colon < 0) return null;
		int i = colon + 1;
		while (i < json.length() && json.charAt(i) != '"' && json.charAt(i) != 'n') i++;
		if (i >= json.length() || json.charAt(i) == 'n') return null; // null value
		return scanJsonString(json, i + 1); // skip opening quote
	}

	/** Find the browser_download_url for the first asset whose name matches assetPattern. */
	private String findZipAssetUrl(String json) {
		int assetsStart = json.indexOf("\"assets\"");
		if (assetsStart < 0) return null;

		int pos = assetsStart;
		while (pos < json.length()) {
			int nameKey = json.indexOf("\"name\"", pos);
			if (nameKey < 0) break;
			int nameColon = json.indexOf(':', nameKey + 6);
			if (nameColon < 0) break;
			int nameQuote = json.indexOf('"', nameColon + 1);
			if (nameQuote < 0) break;
			String name = scanJsonString(json, nameQuote + 1);
			int afterName = nameQuote + 1 + name.length() + 1; // rough advance past this value

			int urlKey = json.indexOf("\"browser_download_url\"", afterName);
			if (urlKey < 0) break;
			int urlColon = json.indexOf(':', urlKey + 22);
			if (urlColon < 0) break;
			int urlQuote = json.indexOf('"', urlColon + 1);
			if (urlQuote < 0) break;
			String url = scanJsonString(json, urlQuote + 1);

			if (name.matches(assetPattern)) return url;
			pos = urlQuote + 1;
		}
		return null;
	}

	/** Scans a JSON string value starting just after the opening quote; returns the decoded value. */
	private String scanJsonString(String json, int start) {
		StringBuilder sb = new StringBuilder();
		int i = start;
		while (i < json.length()) {
			char c = json.charAt(i);
			if (c == '"') break;
			if (c == '\\' && i + 1 < json.length()) {
				i++;
				char esc = json.charAt(i);
				switch (esc) {
					case '"':  sb.append('"');  break;
					case '\\': sb.append('\\'); break;
					case '/':  sb.append('/');  break;
					case 'n':  sb.append('\n'); break;
					case 'r':  sb.append('\r'); break;
					case 't':  sb.append('\t'); break;
					case 'u':
						if (i + 4 < json.length()) {
							try { sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16)); }
							catch (NumberFormatException ignored) { sb.append('?'); }
							i += 4;
						}
						break;
					default: sb.append(esc);
				}
			} else {
				sb.append(c);
			}
			i++;
		}
		return sb.toString();
	}
}
