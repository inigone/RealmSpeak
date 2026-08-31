package com.robin.magic_realm.RealmSpeak.update;

public class UpdateInfo {
	public final String tag;
	public final String downloadUrl;
	public final String releasePageUrl;
	public final String releaseNotes;

	public UpdateInfo(String tag, String downloadUrl, String releasePageUrl, String releaseNotes) {
		this.tag = tag;
		this.downloadUrl = downloadUrl;
		this.releasePageUrl = releasePageUrl;
		this.releaseNotes = releaseNotes;
	}
}
