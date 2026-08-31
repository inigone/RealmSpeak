package com.robin.magic_realm.RealmSpeak.update;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.robin.magic_realm.components.utility.Constants;

public class AppUpdater {

	private static final String VERSION_FILE = "version.txt";

	/** Returns the directory containing RealmSpeakFull.jar (the install dir). */
	public static Path getInstallDir() {
		try {
			return Path.of(AppUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
		} catch (Exception e) {
			return Path.of(".").toAbsolutePath().normalize();
		}
	}

	/**
	 * Returns the full version tag currently installed.
	 * Reads version.txt written by the release build; falls back to Constants.REALM_SPEAK_VERSION
	 * dots-stripped (e.g. "1264") if the file is missing.
	 */
	public static String readCurrentTag() {
		Path versionFile = getInstallDir().resolve(VERSION_FILE);
		if (Files.exists(versionFile)) {
			try {
				String tag = Files.readString(versionFile, StandardCharsets.UTF_8).strip();
				if (!tag.isEmpty()) return tag;
			} catch (IOException ignored) {
			}
		}
		return Constants.REALM_SPEAK_VERSION.replace(".", "");
	}

	/**
	 * Downloads url to destFile, calling progressCallback with percent complete (0–100).
	 * progressCallback receives -1 if content-length is unknown.
	 */
	public static void download(String urlString, Path destFile, Consumer<Integer> progressCallback) throws IOException {
		// Follow redirects manually (GitHub release assets redirect to S3)
		String location = urlString;
		HttpURLConnection conn = null;
		for (int redirects = 0; redirects < 5; redirects++) {
			conn = (HttpURLConnection) new URL(location).openConnection();
			conn.setConnectTimeout(15_000);
			conn.setReadTimeout(300_000);
			conn.setRequestProperty("User-Agent", "RealmSpeak-Updater");
			conn.setInstanceFollowRedirects(false);
			int status = conn.getResponseCode();
			if (status == HttpURLConnection.HTTP_MOVED_TEMP
					|| status == HttpURLConnection.HTTP_MOVED_PERM
					|| status == 307 || status == 308) {
				location = conn.getHeaderField("Location");
				conn.disconnect();
				continue;
			}
			if (status != 200) throw new IOException("Download failed: HTTP " + status);
			break;
		}
		long contentLength = conn.getContentLengthLong();
		try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(destFile)) {
			byte[] buf = new byte[65536];
			long totalRead = 0;
			int n;
			while ((n = in.read(buf)) != -1) {
				out.write(buf, 0, n);
				totalRead += n;
				if (progressCallback != null) {
					progressCallback.accept(contentLength > 0 ? (int) (totalRead * 100 / contentLength) : -1);
				}
			}
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	/**
	 * Extracts all entries from zipFile directly into destDir, overwriting existing files.
	 * Entries with path components outside destDir (zip-slip) are skipped.
	 */
	public static void extractZip(Path zipFile, Path destDir) throws IOException {
		Path canonical = destDir.toRealPath();
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					zis.closeEntry();
					continue;
				}
				Path target = destDir.resolve(entry.getName()).normalize();
				// Zip-slip guard
				if (!target.startsWith(canonical)) {
					zis.closeEntry();
					continue;
				}
				Files.createDirectories(target.getParent());
				Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
				zis.closeEntry();
			}
		}
	}

	/**
	 * Platform-aware install + restart. Call this instead of extractZip + launchRestartAndExit.
	 *
	 * Mac/Linux: extract directly over the install dir (no file locking), then restart.
	 * Windows: extract to a temp staging dir first, then let the restart script do the
	 * swap after the JVM exits (Windows locks JARs while the process is running).
	 *
	 * Deletes zipFile on success. Calls System.exit(0) before returning.
	 */
	public static void installAndRestart(Path zipFile, Path installDir) throws IOException {
		boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
		if (isWindows) {
			Path staging = Files.createTempDirectory("rs_update_staging_");
			extractZip(zipFile, staging);
			Files.deleteIfExists(zipFile);
			launchWindowsScript(installDir, staging);
		} else {
			extractZip(zipFile, installDir);
			Files.deleteIfExists(zipFile);
			launchUnixScript(installDir);
		}
		System.exit(0);
	}

	private static void launchUnixScript(Path installDir) throws IOException {
		Path script = Files.createTempFile("rs_update_", ".sh");
		String cp = "mail.jar:activation.jar:RealmSpeakFull.jar";
		Files.writeString(script,
				"#!/bin/bash\n"
				+ "sleep 3\n"
				+ "cd \"" + installDir.toAbsolutePath() + "\"\n"
				+ "java -Xms768m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 "
				+ "-cp \"" + cp + "\" com.robin.magic_realm.RealmSpeak.RealmSpeakFrame &\n",
				StandardCharsets.UTF_8);
		script.toFile().setExecutable(true);
		new ProcessBuilder("/bin/bash", script.toAbsolutePath().toString()).inheritIO().start();
	}

	private static void launchWindowsScript(Path installDir, Path staging) throws IOException {
		Path script = Files.createTempFile("rs_update_", ".bat");
		String install = installDir.toAbsolutePath().toString();
		String stage   = staging.toAbsolutePath().toString();
		String cp = "mail.jar;activation.jar;RealmSpeakFull.jar";
		// robocopy is available on Vista+; /E=subdirs, /IS/IT/IM=overwrite always
		Files.writeString(script,
				"@echo off\r\n"
				+ "timeout /t 3 /nobreak > nul\r\n"
				+ "robocopy \"" + stage + "\" \"" + install + "\" /E /IS /IT /IM > nul\r\n"
				+ "rd /s /q \"" + stage + "\"\r\n"
				+ "cd /d \"" + install + "\"\r\n"
				+ "start javaw -Xms768m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=100 "
				+ "-cp \"" + cp + "\" com.robin.magic_realm.RealmSpeak.RealmSpeakFrame\r\n",
				StandardCharsets.UTF_8);
		new ProcessBuilder("cmd.exe", "/c", script.toAbsolutePath().toString()).inheritIO().start();
	}
}
