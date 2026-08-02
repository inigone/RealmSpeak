#!/usr/bin/env python3
"""
Mark a monster generator (Pond/Hive/Tomb) DESTROYED in a RealmSpeak .rsgame save, and blame an
existing character for it - so REVENGE can be tested without playing out the kill.

This sets exactly what TreasureUtility.destroyGenerator() sets when the
EXP_GENERATED_MONSTER_BEHAVIOR host option is on:

    <attribute destroyed="" />
    <attribute generatordestroyer="<character game object id>" />

and NOTHING else.  In particular it deliberately does NOT kill the generated monsters - that is
the whole point of REVENGE: they survive and hunt the character named above.  (With the option
OFF, the game would instead makeDead() every one of them.)

Usage:
    python3 tools/rsgame_destroy_generator.py <save.rsgame> <generator> <character> [-o out.rsgame]
    python3 tools/rsgame_destroy_generator.py products/autosave.rsgame Pond Sorceror

<generator> and <character> may be a name (case-insensitive) or a numeric game object id.
By default the result is written alongside the input as "<name>-revenge.rsgame"; the input is
never modified unless you pass --in-place.

Why text surgery instead of parse-and-rewrite: a save is a ~700KB object graph, and
re-serializing the whole document to change two attributes risks perturbing something unrelated.
This inserts the attribute elements into the target's "this" AttributeBlock and leaves every
other byte of the document exactly as it was.
"""
import argparse
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile

DESTROYED = "destroyed"            # Constants.DESTROYED
GENERATOR_DESTROYER = "generatordestroyer"  # Constants.GENERATOR_DESTROYER
GENERATOR_ID = "generatorid"       # Constants.GENERATOR_ID
GENERATED = "generated"            # Constants.GENERATED
DEAD = "_dead_"                    # Constants.DEAD


def read_save(path):
    """Return (entry_name, xml_text)."""
    with zipfile.ZipFile(path) as archive:
        entry = archive.namelist()[0]
        with archive.open(entry) as handle:
            return entry, handle.read().decode("utf-8")


def write_save(path, entry, xml_text):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(entry, xml_text.encode("utf-8"))


def this_attributes(game_object):
    for block in game_object.findall("AttributeBlock"):
        if block.get("blockName") == "this":
            return {n: e.get(n) for e in block.findall("attribute") for n in e.keys()}
    return {}


def index(xml_text):
    """id -> (name, this-attrs) for every GameObject, via a read-only parse."""
    root = ET.fromstring(xml_text)
    return {
        go.get("id"): (go.get("name"), this_attributes(go))
        for go in root.iter("GameObject")
    }


def resolve(objects, token, predicate, what):
    """Find one object by id or by (case-insensitive) name, and require predicate."""
    if token in objects:
        matches = [token]
    else:
        matches = [
            oid for oid, (name, _) in objects.items()
            if name and name.lower() == token.lower()
        ]
    matches = [oid for oid in matches if predicate(objects[oid][1])]
    if not matches:
        sys.exit("error: no %s matching %r" % (what, token))
    if len(matches) > 1:
        sys.exit("error: %r is ambiguous - %s ids: %s\n       pass the id instead."
                 % (token, what, ", ".join(sorted(matches))))
    return matches[0]


def object_span(xml_text, oid):
    """(start, end) character offsets of the <GameObject id="oid" ...>...</GameObject> element."""
    open_tag = re.search(r'<GameObject\s+id="%s"[\s>]' % re.escape(oid), xml_text)
    if not open_tag:
        sys.exit("error: could not locate GameObject id=%s in the document" % oid)
    end = xml_text.index("</GameObject>", open_tag.start()) + len("</GameObject>")
    return open_tag.start(), end


def insert_attributes(xml_text, oid, new_attrs):
    """Insert <attribute k="v"/> elements into oid's blockName="this" AttributeBlock."""
    start, end = object_span(xml_text, oid)
    block = xml_text[start:end]

    open_match = re.search(r'<AttributeBlock\s+blockName="this"\s*>', block)
    if not open_match:
        sys.exit("error: GameObject id=%s has no \"this\" AttributeBlock" % oid)
    close_at = block.index("</AttributeBlock>", open_match.end())

    # Match the indentation of the existing attribute lines so the file stays readable.
    indent_match = re.search(r'\n(\s*)<attribute ', block[open_match.end():close_at])
    indent = indent_match.group(1) if indent_match else "        "

    # Insert at the START of the closing tag's line, not immediately before the tag itself -
    # otherwise the new lines swallow the closing tag's own indentation.
    line_start = block.rfind("\n", 0, close_at) + 1
    addition = "".join(
        '%s<attribute %s="%s" />\n' % (indent, k, v) for k, v in new_attrs
    )
    patched = block[:line_start] + addition + block[line_start:]
    return xml_text[:start] + patched + xml_text[end:]


def main():
    parser = argparse.ArgumentParser(
        description="Destroy a generator in a .rsgame save and blame a character (REVENGE testing).")
    parser.add_argument("save")
    parser.add_argument("generator", help="generator name (Pond/Hive/Tomb) or game object id")
    parser.add_argument("character", help="character name or game object id to blame")
    parser.add_argument("-o", "--output", help="output path (default: <save>-revenge.rsgame)")
    parser.add_argument("--in-place", action="store_true",
                        help="overwrite the input save (a .bak copy is made first)")
    args = parser.parse_args()

    entry, xml_text = read_save(args.save)
    objects = index(xml_text)

    gen_id = resolve(objects, args.generator,
                     lambda a: "generator" in a, "generator")
    char_id = resolve(objects, args.character,
                      lambda a: "character" in a, "character")

    gen_name, gen_attrs = objects[gen_id]
    char_name, char_attrs = objects[char_id]

    if char_attrs.get("clearing") is None:
        print("warning: %s#%s is not on the map (no clearing attribute).  REVENGE will find no\n"
              "         target and pods will fall back to ordinary wandering."
              % (char_name, char_id))

    new_attrs = []
    if DESTROYED in gen_attrs:
        print("note: %s#%s is already destroyed" % (gen_name, gen_id))
    else:
        new_attrs.append((DESTROYED, ""))
    if gen_attrs.get(GENERATOR_DESTROYER) == char_id:
        print("note: %s#%s already blames %s#%s" % (gen_name, gen_id, char_name, char_id))
    elif GENERATOR_DESTROYER in gen_attrs:
        sys.exit("error: %s#%s already blames game object %s.  Refusing to overwrite an existing\n"
                 "       %s stamp - edit it by hand if that is really what you want."
                 % (gen_name, gen_id, gen_attrs[GENERATOR_DESTROYER], GENERATOR_DESTROYER))
    else:
        new_attrs.append((GENERATOR_DESTROYER, char_id))

    if not new_attrs:
        sys.exit("nothing to do - the save already has this exact state.")

    patched = insert_attributes(xml_text, gen_id, new_attrs)

    # Verify by re-parsing rather than trusting the surgery.
    check = index(patched)[gen_id][1]
    assert DESTROYED in check, "destroyed attribute did not take"
    assert check.get(GENERATOR_DESTROYER) == char_id, "destroyer stamp did not take"

    if args.in_place:
        out = args.save
        shutil.copy2(args.save, args.save + ".bak")
        print("backed up original -> %s.bak" % args.save)
    else:
        out = args.output or (os.path.splitext(args.save)[0] + "-revenge.rsgame")
    write_save(out, entry, patched)

    survivors = [
        oid for oid, (_, a) in objects.items()
        if GENERATED in a and a.get(GENERATOR_ID) == gen_id and DEAD not in a
    ]
    print("\n%s#%s destroyed; blame assigned to %s#%s" % (gen_name, gen_id, char_name, char_id))
    print("%d surviving generated monster(s) will now hunt %s" % (len(survivors), char_name))
    print("wrote %s" % out)
    print("\nLoad it with Host Game -> Load Saved Game.  EXP_GENERATED_MONSTER_BEHAVIOR must be ON\n"
          "in that game, or the pods will simply wander as before.")


if __name__ == "__main__":
    main()
