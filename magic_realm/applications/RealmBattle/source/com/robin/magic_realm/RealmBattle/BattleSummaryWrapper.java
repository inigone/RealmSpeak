package com.robin.magic_realm.RealmBattle;

import java.util.ArrayList;
import java.util.Iterator;

import com.robin.game.objects.*;
import com.robin.magic_realm.components.BattleChit;
import com.robin.magic_realm.components.CharacterChitComponent;
import com.robin.magic_realm.components.RealmComponent;
import com.robin.magic_realm.components.wrapper.SpellWrapper;

public class BattleSummaryWrapper extends GameObjectWrapper {
	
	private static final String ATTACKERS = "as";
	private static final String TARGETS = "ts";
	private static final String ORDERS = "os"; // attack order position, parallel to ATTACKERS
	private static final String KEYS = "ks"; // attack identity, so the colour can be looked up
	
	public BattleSummaryWrapper(GameObject go) {
		super(go);
	}
	public String getBlockName() {
		return "_BSUM_";
	}
	public void initFromBattleChits(ArrayList<BattleChit> battleChits,BattleModel.AttackOrder order) {
		clearBattleSummary();
		
		ArrayList<GameObject> battleChitsAdded = new ArrayList<>();
		for (BattleChit bp : battleChits) {
			if (battleChitsAdded.contains(bp.getGameObject())) continue;
			if (bp instanceof SpellWrapper) {
				SpellWrapper spell = (SpellWrapper)bp;
				for (RealmComponent rc : spell.getTargets()) {
					BattleChit target = (BattleChit)rc;
					addBattleSummaryKill(bp.getGameObject(),target.getGameObject(),order.positionOf(bp),BattleModel.getAttackOrderKey(bp));
				}
			}
			else {
				BattleChit target = (BattleChit)bp.getTarget();
				if (target==null) {
					// this happens with the monster weapons
					RealmComponent monster = RealmComponent.getRealmComponent(bp.getGameObject().getHeldBy());
					target = (BattleChit)monster.getTarget();
				}
				if (target!=null) {
					addBattleSummaryKill(bp.getGameObject(),target.getGameObject(),order.positionOf(bp),BattleModel.getAttackOrderKey(bp));
				}
				if (bp instanceof CharacterChitComponent) {
					BattleChit target2 = (BattleChit) ((CharacterChitComponent)bp).get2ndTarget();
					if (target2!=null) {
						addBattleSummaryKill(bp.getGameObject(),target2.getGameObject(),order.positionOf(bp),BattleModel.getAttackOrderKey(bp));
					}
				}	
			}
			battleChitsAdded.add(bp.getGameObject());
		}
	}
	public BattleSummary getBattleSummary() {
		BattleSummary bs = new BattleSummary();
		ArrayList<String> attackers = getList(ATTACKERS);
		ArrayList<String> targets = getList(TARGETS);
		ArrayList<String> orders = getList(ORDERS);
		ArrayList<String> keys = getList(KEYS);
		GameData data = getGameObject().getGameData();
		if (attackers!=null && attackers.size()>0) {
			Iterator<String> k = attackers.iterator();
			Iterator<String> d = targets.iterator();
			Iterator<String> o = orders==null?null:orders.iterator();
			Iterator<String> ky = keys==null?null:keys.iterator();
			while(k.hasNext()) {
				String kid = k.next();
				String did = d.next();
				// Summaries saved before this list existed simply carry no number
				int attackOrder = (o!=null && o.hasNext())?Integer.parseInt(o.next()):0;
				String attackKey = (ky!=null && ky.hasNext())?ky.next():"";
				GameObject kGo = data.getGameObject(Long.valueOf(kid));
				GameObject dGo = data.getGameObject(Long.valueOf(did));
				bs.addAttackerTarget(kGo,dGo,attackOrder,attackKey);
			}
		}
		return bs;
	}
	private void clearBattleSummary() {
		getGameObject().removeAttributeBlock(getBlockName());
	}
	private void addBattleSummaryKill(GameObject attacker,GameObject target,int attackOrder,String attackKey) {
		addListItem(ATTACKERS,attacker.getStringId());
		addListItem(TARGETS,target.getStringId());
		addListItem(ORDERS,String.valueOf(attackOrder));
		addListItem(KEYS,attackKey);
	}
}