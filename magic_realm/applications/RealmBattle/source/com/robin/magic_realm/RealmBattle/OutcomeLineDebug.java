package com.robin.magic_realm.RealmBattle;

import java.util.HashSet;

/**
 * Diagnostics for the combat outcome-lines feature, deliberately gathered in one place.
 * <p>
 * Every trace in the feature goes through {@link #log}, so the whole facility has exactly two
 * controls:
 * <ul>
 * <li><b>To turn it off at run time</b> - do nothing.  It is already off unless the game is started
 * with <code>-Drealmspeak.ol.debug=true</code>.  Setting {@link #ENABLED} to a constant
 * <code>false</code> also lets the compiler drop the call bodies.</li>
 * <li><b>To strip it from the source</b> - delete this file and remove every line matching
 * <code>OutcomeLineDebug</code> (<code>grep -rn OutcomeLineDebug</code>).  Each call site is a
 * standalone statement, so no surrounding logic goes with it.</li>
 * </ul>
 * Nothing outside this class holds debug state, and no call site has a side effect on the game.
 */
class OutcomeLineDebug {

	/** Off unless -Drealmspeak.ol.debug=true. */
	static final boolean ENABLED = Boolean.getBoolean("realmspeak.ol.debug");

	private static final HashSet<String> seen = new HashSet<>();

	private OutcomeLineDebug() {
	}

	/**
	 * Reports something the outcome-line code decided, typically why an attack produced no lines.
	 * Each distinct message prints once so a repainting sheet cannot flood the log, and it goes to
	 * System.out rather than err because DebugUtility treats anything on err as an error worth
	 * warning the player about at the end of the game.
	 */
	static void log(String message) {
		if (!ENABLED) return;
		if (seen.add(message)) {
			System.out.println("[OL] "+message);
		}
	}
}
