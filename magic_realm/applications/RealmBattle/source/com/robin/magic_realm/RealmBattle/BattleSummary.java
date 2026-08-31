package com.robin.magic_realm.RealmBattle;

import java.util.*;

import com.robin.game.objects.GameObject;

public class BattleSummary {
	
	private ArrayList<GameObject> orderedAttackers;
	private Hashtable<GameObject,ArrayList<GameObject>> hash;
	private Hashtable<GameObject,ArrayList<Integer>> attackOrders;
	private Hashtable<GameObject,ArrayList<String>> attackKeys;
	private OutcomeLines.BattleOutcomes outcomes;
	
	public BattleSummary() {
		orderedAttackers = new ArrayList<>();
		hash = new Hashtable<>();
		attackOrders = new Hashtable<>();
		attackKeys = new Hashtable<>();
	}
	/** How each attack is classified, so the numbers here match the summary pane's stamps. */
	public void setOutcomes(OutcomeLines.BattleOutcomes outcomes) {
		this.outcomes = outcomes;
	}
	public void addAttackerTarget(GameObject attacker,GameObject target,int attackOrder,String attackKey) {
		ArrayList<GameObject> targets = hash.get(attacker);
		if (targets==null) {
			targets = new ArrayList<>();
			hash.put(attacker,targets);
			attackOrders.put(attacker,new ArrayList<Integer>());
			attackKeys.put(attacker,new ArrayList<String>());
			orderedAttackers.add(attacker);
		}
		targets.add(target);
		attackOrders.get(attacker).add(Integer.valueOf(attackOrder));
		attackKeys.get(attacker).add(attackKey);
	}
	public ArrayList<BattleSummaryRow> getSummaryRows() {
		ArrayList<BattleSummaryRow> list =  new ArrayList<>();
		int n=0;
		for (GameObject attacker:orderedAttackers) {
			ArrayList<Integer> orders = attackOrders.get(attacker);
			ArrayList<String> keys = attackKeys.get(attacker);
			int index = 0;
			for (GameObject target:hash.get(attacker)) {
				int attackOrder = index<orders.size()?orders.get(index).intValue():0;
				String attackKey = index<keys.size()?keys.get(index):"";
				index++;
				list.add(new BattleSummaryRow(attacker,target,n++,attackOrder,outcomes==null?null:outcomes.colorFor(attackKey)));
			}
		}
		Collections.sort(list);
		return list;
	}
}