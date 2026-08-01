package com.robin.magic_realm.components.utility;

import java.io.*;

import javax.swing.JOptionPane;

public class DebugUtility {
	public static final String LAUNCH_GAME = "LAUNCH_GAME__";
	
	public static boolean DEBUG_ON = false;
	public static boolean CHEAT_ON = false;
	public static boolean MONSTER_REPO_LOCK = false; // prevents monsters from repositioning
	public static boolean MONSTER_FLIP = false; // forces monsters to flip every time
	public static boolean NO_SUMMON = false; // prevents denizen summoning
	public static boolean IGNORE_CHARS = false; // ignore the custom character set
	public static boolean SMALL_FRAME = false;
	public static boolean DISABLE_ERROR_LOGGING = false;
	public static boolean DISABLE_FAMILIAR = false; // allows the game to work WITHOUT the cat
	public static boolean SUMMON_MULTIPLE = false; // allows chits to summon many times
	/**
	 * Master switch for ALL developer diagnostics - every diag() console trace and the modal
	 * EOCTMS diagnostic dialogs alike.  Driven by the "Enable Diagnostics" host option
	 * (Constants.OPT_ENABLE_DIAGNOSTICS), which HostPrefWrapper.findHostPrefs() syncs in here as
	 * soon as host prefs exist.  Launch with the DIAGNOSTICS argument to force it on regardless -
	 * useful for tracing startup, before any game (and therefore any host pref) exists.
	 */
	public static boolean DIAGNOSTICS = false;
	private static boolean diagnosticsForced = false;
	
	private static PrintStream errorStream = null;
	private static File errorFile = null;
	
	public static void setupArgs(String[] args) {
		if (args != null) {
			for (int i=0;i<args.length;i++) {
				if ("DEBUG".equals(args[i].toUpperCase())) {
					DEBUG_ON = true;
					System.out.println("DEBUG is ON");
				}
				else if ("CHEAT".equals(args[i].toUpperCase())) {
					CHEAT_ON = true;
					System.out.println("CHEAT is ON");
				}
				else if ("MONSTER_LOCK".equals(args[i].toUpperCase())) {
					MONSTER_REPO_LOCK = true;
					System.out.println("MONSTER_LOCK is ON");
				}
				else if ("MONSTER_FLIP".equals(args[i].toUpperCase())) {
					MONSTER_FLIP = true;
					System.out.println("MONSTER_FLIP is ON");
				}
				else if ("NO_SUMMON".equals(args[i].toUpperCase())) {
					NO_SUMMON = true;
					System.out.println("NO_SUMMON is ON");
				}
				else if ("SMALL_FRAME".equals(args[i].toUpperCase())) {
					SMALL_FRAME = true;
					System.out.println("SMALL_FRAME is ON");
				}
				else if ("DISABLE_ERROR_LOGGING".equals(args[i].toUpperCase())) {
					DISABLE_ERROR_LOGGING = true;
					System.out.println("DISABLE_ERROR_LOGGING is ON");
				}
				else if ("DISABLE_FAMILIAR".equals(args[i].toUpperCase())) {
					DISABLE_FAMILIAR = true;
					System.out.println("DISABLE_FAMILIAR is ON");
				}
				else if ("IGNORE_CHARS".equals(args[i].toUpperCase())) {
					IGNORE_CHARS = true;
					System.out.println("IGNORE_CHARS is ON");
				}
				else if ("SUMMON_MULTIPLE".equals(args[i].toUpperCase())) {
					SUMMON_MULTIPLE = true;
					System.out.println("SUMMON_MULTIPLE is ON");
				}
				else if ("DIAGNOSTICS".equals(args[i].toUpperCase())) {
					DIAGNOSTICS = true;
					diagnosticsForced = true;
					System.out.println("DIAGNOSTICS is ON (forced - the host option cannot turn it off)");
				}
				else if (args[i].toUpperCase().endsWith(".RSGAME")) {
					System.setProperty(LAUNCH_GAME,args[i]);
				}
			}
		}
		if (!DISABLE_ERROR_LOGGING) {
			setupErrorLogging();
		}
	}
	public static boolean isDebug() {
		return DEBUG_ON;
	}
	public static boolean isCheat() {
		return CHEAT_ON;
	}
	public static boolean isMonsterLock() {
		return MONSTER_REPO_LOCK;
	}
	public static boolean isMonsterFlip() {
		return MONSTER_FLIP;
	}
	public static boolean isNoSummon() {
		return NO_SUMMON;
	}
	public static boolean isDiagnostics() {
		return DIAGNOSTICS;
	}
	/**
	 * Applies the "Enable Diagnostics" host option.  Ignored if the DIAGNOSTICS launch argument
	 * forced diagnostics on.
	 */
	public static void setDiagnosticsFromHostPrefs(boolean enabled) {
		if (!diagnosticsForced) {
			DIAGNOSTICS = enabled;
		}
	}
	/**
	 * Emits a developer diagnostic line, but only while diagnostics are enabled.  Callers should
	 * keep building the message inline - the string concatenation is the cheap part next to the
	 * synchronized PrintStream write this skips.
	 */
	public static void diag(String message) {
		if (DIAGNOSTICS) {
			System.out.println(message);
		}
	}
	public static boolean isSmallFrame() {
		return SMALL_FRAME;
	}
	public static boolean isErrorLogging() {
		return !DISABLE_ERROR_LOGGING;
	}
	public static boolean isDisableFamiliar() {
		return DISABLE_FAMILIAR;
	}
	public static boolean isIgnoreChars() {
		return IGNORE_CHARS;
	}
	public static boolean isSummonMultiple() {
		return SUMMON_MULTIPLE;
	}
	public static void setupErrorLogging() {
		try {
			File currentLocation = new File(".");
			currentLocation = new File(currentLocation.getCanonicalPath());
			errorFile = File.createTempFile("RealmSpeakError",".log",currentLocation);
			System.out.println("Error log: "+errorFile.getAbsolutePath());
			errorStream = new PrintStream(new FileOutputStream(errorFile));
			System.setErr(errorStream);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	public static void shutDown() {
		if (errorStream!=null) {
			String lastLine = "Error Log Generated by RealmSpeak Version "+Constants.REALM_SPEAK_VERSION;
			System.err.print(lastLine);
			errorStream.close();
			long errorLength = errorFile.length();
			long lastLineLength = lastLine.length();
			if (errorLength==lastLineLength) {
				errorFile.delete();
			}
			else {
				JOptionPane.showMessageDialog(null,"Errors were generated during this game.  See log file:\n\n"+errorFile.getAbsolutePath());
			}
		}
	}
}