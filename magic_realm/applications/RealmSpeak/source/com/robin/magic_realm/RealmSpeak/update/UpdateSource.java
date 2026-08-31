package com.robin.magic_realm.RealmSpeak.update;

import java.io.IOException;
import java.util.Optional;

/**
 * Pluggable source for checking application update availability.
 * Implement this to support different release providers (inigone, upstream, etc.).
 */
public interface UpdateSource {
	/** Human-readable label shown in dialogs (e.g. "inigone" or "upstream"). */
	String getSourceName();

	/**
	 * Check whether a newer release is available.
	 *
	 * @param currentTag the full version tag currently installed (e.g. "1264-inigone.2"),
	 *                   or null/empty if unknown
	 * @return the latest update if one is newer than currentTag, or empty if already up to date
	 * @throws IOException on network failure
	 */
	Optional<UpdateInfo> checkForUpdate(String currentTag) throws IOException;
}
