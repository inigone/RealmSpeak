package com.robin.magic_realm.components.utility;

import java.awt.Color;
import java.util.ArrayList;

import com.robin.game.objects.GameObject;

/**
 * Display table for tile-wide effects - the weather/terrain events from the events deck, plus the
 * handful of spells that stamp an attribute onto a tile.
 * <p>
 * Every effect here is stored the same way: a plain attribute on the TILE's GameObject, set by the
 * event (see the events package, which also registers the tile id with RealmEvents.addEffectForTile)
 * or by a spell effect. That is what makes a single lookup table possible - to show a new effect,
 * add a row, nothing else.
 * <p>
 * The {@link #wash} flag marks effects that obscure or dominate the whole hex (fog, storms, flood).
 * Those get a translucent colour wash over the tile in addition to their badge, because "you cannot
 * see/act normally here" reads far faster as a wash than as a two-letter chip. Only the first active
 * wash effect is drawn - see {@link #getWashEffect} - since stacking translucent fills just muddies
 * the terrain art.
 */
public class TileEffects {

	public static class Effect {
		public final String attribute;
		public final String code; // 2-3 chars - must stay short enough for the badge
		public final String name;
		public final String description;
		public final Color color;
		public final boolean wash;

		private Effect(String attribute, String code, String name, String description, Color color, boolean wash) {
			this.attribute = attribute;
			this.code = code;
			this.name = name;
			this.description = description;
			this.color = color;
			this.wash = wash;
		}
	}

	private static final ArrayList<Effect> ALL = new ArrayList<>();

	private static void add(String attribute, String code, String name, String description, Color color, boolean wash) {
		ALL.add(new Effect(attribute, code, name, description, color, wash));
	}

	static {
		// Spell-applied. Listed first so a cast Fog wins the wash over a weaker event on the same tile.
		add(Constants.SP_NO_PEER, "NP", "No PEER (spell)", "A spell prevents PEERing in this hex", new Color(150, 150, 165), true);

		// Events that obscure the hex
		add(Constants.EVENT_FOG, "FOG", "Fog", "Cannot PEER normally in this hex", new Color(170, 170, 175), true);
		add(Constants.EVENT_VIOLENT_STORM, "STM", "Violent Storm", "Violent storm over this hex", new Color(90, 90, 110), true);
		add(Constants.EVENT_HURRICANE_WINDS, "HUR", "Hurricane Winds", "Hurricane winds in this hex", new Color(70, 110, 160), true);
		add(Constants.EVENT_FLOOD, "FLD", "Flood", "This hex is flooded", new Color(60, 120, 190), true);
		add(Constants.EVENT_NIGHT_OF_THE_DEMON, "DMN", "Night of the Demon", "Night of the Demon in this hex", new Color(150, 40, 40), true);

		// Events that change the rules but do not obscure the hex
		add(Constants.EVENT_VIOLENT_WINDS, "VW", "Violent Winds", "Violent winds in this hex", new Color(120, 170, 200), false);
		add(Constants.EVENT_FROZEN_WATER, "ICE", "Frozen River", "The water in this hex is frozen", new Color(200, 230, 245), false);
		add(Constants.EVENT_THORNS, "THN", "Thorns", "Thorns in this hex", new Color(60, 130, 60), false);
		add(Constants.EVENT_CAVE_IN, "CVE", "Cave In", "A cave has collapsed in this hex", new Color(130, 100, 60), false);
		add(Constants.EVENT_ILLUSION, "ILL", "Illusion", "An illusion affects this hex", new Color(180, 80, 180), false);
		add(Constants.EVENT_LOST, "LST", "Lost", "Travellers become lost in this hex", new Color(210, 130, 50), false);
		add(Constants.EVENT_HORSE_WHISPER, "HRS", "Horse Whisper", "Horse Whisper affects this hex", new Color(190, 160, 110), false);
		add(Constants.EVENT_NEGATIVE_AURA, "NEG", "Negative Aura", "A negative aura covers this hex", new Color(110, 60, 160), false);
		add(Constants.EVENT_PEACEFUL_DAY, "PCE", "Peaceful Day", "A peaceful day in this hex", new Color(215, 195, 70), false);
		add(Constants.EVENT_MOUNTAIN_SURGE, "MTN", "Mountain Surge", "A mountain surge affects this hex", new Color(120, 115, 105), false);
	}

	/**
	 * Every effect currently active on this tile, in table order.
	 */
	public static ArrayList<Effect> getActiveEffects(GameObject tile) {
		ArrayList<Effect> active = new ArrayList<>();
		if (tile == null) return active;
		for (Effect effect : ALL) {
			if (tile.hasThisAttribute(effect.attribute)) {
				active.add(effect);
			}
		}
		return active;
	}

	/**
	 * The single effect that should tint the whole hex, or null if none of the active effects is a
	 * wash effect. Deliberately returns only one - see the class comment.
	 */
	public static Effect getWashEffect(GameObject tile) {
		for (Effect effect : getActiveEffects(tile)) {
			if (effect.wash) return effect;
		}
		return null;
	}

	public static boolean hasAnyEffect(GameObject tile) {
		return !getActiveEffects(tile).isEmpty();
	}
}
