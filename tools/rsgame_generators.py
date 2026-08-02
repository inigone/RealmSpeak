#!/usr/bin/env python3
"""
Report the monster generators (Pond/Hive/Tomb) in a RealmSpeak .rsgame save, plus any
generated monsters already on the board.

A .rsgame file is a zip containing a single GameData XML document, so everything below is
read straight out of the saved object graph - no game engine needed.

Usage:
    python3 tools/rsgame_generators.py products/autosave.rsgame

Why you'd want this: generators only spawn once they are "seen" (SetupCardUtility's summon
loop queries "seen,generator,!destroyed"), and their tile placement is randomized per game
setup.  Run this against a new save to find out where to send a character, and which die
roll each generator answers to.
"""
import sys
import zipfile
import xml.etree.ElementTree as ET

# icon_type -> what the generator actually spawns (see SetupCardUtility.generateMonsters)
SPAWNS = {"blob": "Blob", "wasp": "Wasp", "zombie1": "Undead (zombie)"}

# Constants.DEAD / Constants.GENERATED / Constants.GENERATOR_ID
DEAD = "_dead_"
GENERATED = "generated"
GENERATOR_ID = "generatorid"
GENERATOR_DESTROYER = "generatordestroyer"  # Constants.GENERATOR_DESTROYER (REVENGE)
DEFAULT_NAME = "GameObject"  # GameObject.revertNameToDefault() - a monster left with this was misnamed


def this_attributes(game_object):
    """The 'this' AttributeBlock of a GameObject element, flattened into a dict."""
    for block in game_object.findall("AttributeBlock"):
        if block.get("blockName") == "this":
            return {
                name: el.get(name)
                for el in block.findall("attribute")
                for name in el.keys()
            }
    return {}


def load(path):
    """Return (objects_by_id, parent_by_id) for every GameObject in the save."""
    with zipfile.ZipFile(path) as archive:
        with archive.open(archive.namelist()[0]) as handle:
            root = ET.parse(handle).getroot()

    objects, parent = {}, {}
    for game_object in root.iter("GameObject"):
        oid = game_object.get("id")
        objects[oid] = (game_object.get("name"), this_attributes(game_object))
        for child in game_object.findall("contains"):
            parent[child.get("id")] = oid
    return objects, parent


def locate(oid, objects, parent):
    """Walk up the holds chain to the containing tile.  Returns (tile_name, clearing)."""
    clearing = objects[oid][1].get("clearing")
    visited = set()
    current = parent.get(oid)
    while current is not None and current not in visited:
        visited.add(current)
        name, attributes = objects[current]
        if "tile" in attributes:
            return name, clearing
        if clearing is None:
            clearing = attributes.get("clearing")
        current = parent.get(current)
    # Not held by a tile - undiscovered chits still live in their setup card box.
    return (objects[parent[oid]][0] if oid in parent else "(not on the map)"), clearing


def report(path):
    objects, parent = load(path)
    by_id = lambda item: int(item[0])

    print(f"=== GENERATORS in {path} ===")
    generators = [
        (oid, name, a) for oid, (name, a) in objects.items() if "generator" in a
    ]
    if not generators:
        print("  (none - is this a Magic Realm save with the expansion content enabled?)")
    for oid, name, attributes in sorted(generators, key=by_id):
        tile, clearing = locate(oid, objects, parent)
        spawns = SPAWNS.get(attributes.get("icon_type"), attributes.get("icon_type"))
        flags = [f for f in ("seen", "discovered", "destroyed") if f in attributes]
        print(
            f"  {name:<8} id={oid:<5} spawns={spawns:<16} rate={attributes.get('generator')}"
            f"  monster_die={attributes.get('monster_die')}"
            f"  at={tile} clearing {clearing}"
            f"  [{', '.join(flags) if flags else 'not yet seen'}]"
        )
        # REVENGE (EXP_GENERATED_MONSTER_BEHAVIOR): a destroyed generator remembers who killed it,
        # and its surviving monsters hunt that character instead of dying with it.
        destroyer = attributes.get(GENERATOR_DESTROYER)
        if destroyer:
            who = objects.get(destroyer, ("(missing character)", {}))[0]
            where = locate(destroyer, objects, parent) if destroyer in objects else ("?", "?")
            print(f"           REVENGE: hunting {who}#{destroyer}, currently at {where[0]} clearing {where[1]}")

    alive = [
        (oid, name, a)
        for oid, (name, a) in objects.items()
        if GENERATED in a and DEAD not in a
    ]
    print(f"\n=== GENERATED MONSTERS on the board: {len(alive)} ===")
    misnamed = []
    for oid, name, attributes in sorted(alive, key=by_id):
        tile, clearing = locate(oid, objects, parent)
        home = attributes.get(GENERATOR_ID)
        home_name = objects.get(home, ("?", {}))[0] if home else "?"
        if name == DEFAULT_NAME:
            misnamed.append(oid)
        print(
            f"  {name:<8} id={oid:<5} at={tile} clearing {clearing}"
            f"  from={home_name}#{home}  monster_die={attributes.get('monster_die')}"
            f"  blocked={'yes' if 'blocked' in attributes else 'no'}"
            f"{'  <-- MISNAMED' if name == DEFAULT_NAME else ''}"
        )
    if misnamed:
        print(
            f"\n  *** {len(misnamed)} monster(s) still carry the default name {DEFAULT_NAME!r}:"
            f" {', '.join(misnamed)}\n"
            "  A rename used to have no GameObjectChange of its own, so a run-time rename only\n"
            "  reached other machines by riding along on the next unrelated change - and a replay\n"
            "  that stopped short left the object misnamed.  Monsters spawned BEFORE that fix keep\n"
            "  the bad name; only newly spawned ones should be clean."
        )
    elif alive:
        print(f"\n  All {len(alive)} generated monsters are properly named.")
    if alive:
        print(
            "\n  Note: a blocked generated monster does NOT propagate - moveGeneratedMonster()\n"
            "  returns immediately on isBlocked(), and 'blocked' is only cleared once per day\n"
            "  at day end (RealmHostPanel, after day-end trading)."
        )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    report(sys.argv[1])
