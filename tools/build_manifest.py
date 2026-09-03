#!/usr/bin/env python3
"""Turns the raw PvZ2 asset dump into a flat crop manifest.

The dump stores every sprite inside a shared atlas PNG; RESOURCES.json says
which atlas a sprite lives in and at which rectangle. This script resolves the
handful of sprites the game actually needs and writes one TSV line per sprite:

    outputName <TAB> atlasPngName <TAB> x <TAB> y <TAB> w <TAB> h

AtlasExtract.java then does the actual cropping.
"""
import json
import sys
from pathlib import Path

RES = 768

# our plant id -> seed packet sprite name in the dump
PLANTS = {
    # sun producers
    "sunflower": "sunflower", "twin-sunflower": "twinsunflower",
    "sun-shroom": "sunshroom", "primal-sunflower": "primalsunflower",
    "gold-bloom": "goldbloom",
    # shooters
    "peashooter": "peashooter", "repeater": "repeater", "threepeater": "threepeater",
    "snow-pea": "snowpea", "pea-pod": "peapod", "split-pea": "splitpea",
    "citron": "citron", "bowling-bulb": "bowlingbulb",
    "fire-peashooter": "firepeashooter", "starfruit": "starfruit",
    "goo-peashooter": "poisonpeashooter", "mega-gatling-pea": "megagatling",
    "sea-shroom": "seashroom", "puff-shroom": "puffshroom",
    # homing
    "caulipower": "caulipower", "electric-blueberry": "electricblueberry",
    "magnet-shroom": "magnetshroom", "cattail": "homingthistle",
    # strike-through
    "cactus": "cactus", "fume-shroom": "fumeshroom", "laser-bean": "laser_bean",
    # lobbers
    "cabbage-pult": "cabbagepult", "kernel-pult": "kernelpult",
    "melon-pult": "melonpult", "winter-melon": "wintermelon",
    "pepper-pult": "pepperpult",
    # explosives and traps
    "potato-mine": "potatomine", "primal-potato-mine": "primalpotatomine",
    "cherry-bomb": "cherry_bomb", "squash": "squash", "grapeshot": "grapeshot",
    "jalapeno": "jalapeno", "doom-shroom": "doomshroom", "tangle-kelp": "tanglekelp",
    "iceberg-lettuce": "iceburg", "ice-shroom": "iceshroom",
    "hot-potato": "hotpotato", "grave-buster": "gravebuster",
    # melee
    "bonk-choy": "bonkchoy", "phat-beet": "phatbeet", "chomper": "chomper",
    "wasabi-whip": "wasabiwhip", "kiwibeast": "kiwibeast",
    # walls
    "wall-nut": "wallnut", "tall-nut": "tallnut", "endurian": "endurian",
    "garlic": "garlic", "sweet-potato": "sweetpotato", "explode-o-nut": "explodeonut",
    "pumpkin": "pumpkin", "sun-bean": "sunbean",
    "bowling-wallnut": "wallnut", "giant-wallnut": "primalwallnut",
    # modifiers
    "torchwood": "torchwood", "hypno-shroom": "hypnoshroom",
    "imitater": "imitater", "lily-pad": "lilypad",
    # the mint family, one per plant family
    "peppermint": "peppermint", "enlighten-mint": "enlightenmint",
    "appease-mint": "appeasemint", "arma-mint": "armamint",
    "bombard-mint": "bombardmint", "enforce-mint": "enforcemint",
    "reinforce-mint": "reinforcemint", "enchant-mint": "enchantmint",
    "pierce-mint": "spearmint", "cattail-mint": "ailmint",
}

# plants the dump has no seed packet for; taken from their in-game sprite instead
PLANTS_NO_PACKET = {
    "rotobaga": "plant/rotorutabaga/rotorutabaga_123x123",
}

# our zombie id -> almanac portrait name in the dump
ZOMBIES = {
    "normal": "tutorial", "conehead": "tutorial_armor1", "buckethead": "tutorial_armor2",
    "knight": "dark_armor2", "blockhead": "dark_armor4", "imp": "tutorial_imp",
    "gargantuar": "tutorial_gargantuar", "all-star": "modern_allstar", "ra": "ra",
    "explorer": "explorer", "tombraiser": "tomb_raiser", "newspaper": "modern_newspaper",
    "dodo-rider": "iceage_dodo", "hunter": "iceage_hunter", "troglobite": "iceage_troglobite",
    "fisherman": "beach_fisherman", "snorkel": "beach_snorkel", "octopus": "beach_octopus",
    "parasol": "beach_fem_armor1", "jester": "dark_juggler", "wizard": "dark_wizard",
    "king": "dark_king", "imp-dragon": "dark_imp_dragon",
}

# zombotany zombies have no official art; reuse the plant they wear as a head.
# the i-Zombie sun producer gets the sunflower for the same reason.
ZOMBOTANY = {
    "peashooter-zombie": "peashooter", "wallnut-zombie": "wallnut",
    "jalapeno-zombie": "jalapeno", "squash-zombie": "squash",
    "sun-zombie": "sunflower",
}

# chapter -> lawn background folder in the dump
BACKGROUNDS = {
    "ancient-egypt": "egypt", "frostbite-caves": "iceage",
    "big-wave-beach": "beach", "dark-ages": "dark",
}

# the greenhouse bench has a backdrop of its own
GREENHOUSE = "backgrounds/zen_garden"

# the boss that closes out each chapter
BOSSES = {
    "ancient-egypt": "zombossmech_egypt",
    "frostbite-caves": "zombossmech_iceage",
    "big-wave-beach": "zombossmech_beach",
    "dark-ages": "zombossmech_dark",
}

# what an armoured zombie looks like once its armour is gone
ZOMBIE_BARE = {
    "conehead": "tutorial", "buckethead": "tutorial",
    "knight": "dark", "blockhead": "dark",
    "newspaper": "newspaper_veteran", "parasol": "beach_fem",
}

# the ice block a frozen plant or zombie sits inside, in two layers
ICE = {
    "plant-behind": "effects/frostbite_ice_block_plant_behind/frostbite_ice_block_plant_behind_164x171",
    "plant-front": "effects/frostbite_ice_block_plant/frostbite_ice_block_plant_167x172",
    "zombie-behind": "effects/frostbite_ice_block_zombie_behind/frostbite_ice_block_zombie_behind_159x247",
    "zombie-front": "effects/frostbite_ice_block_zombie/frostbite_ice_block_zombie_153x243",
}

# the bits that come off a zombie: its head and an arm, and the armour it wore.
# The dump names a zombie's body parts by their pixel size rather than by what
# they are, so these were picked out of the plain zombie's sprite set by eye.
ZOMBIE_PARTS = {
    "head": "zombie/zombie_tutorial/zombie_tutorial_82x69",
    "arm": "zombie/zombie_tutorial/zombie_tutorial_36x45",
    "cone": "zombie/zombie_tutorial/zombie_tutorial_80x83_2",
    "bucket": "zombie/zombie_tutorial/zombie_tutorial_96x97",
    "block": "zombie/zombie_tutorial/zombie_tutorial_115x115",
}

# the sandstorm that sweeps across an Ancient Egypt lawn, in two layers
SANDSTORM = {
    "sandstorm-back": "effects/sandstorm_rear/sandstorm_back1",
    "sandstorm-front": "effects/sandstorm_top/sandstorm_front1",
    "sandstorm-cloud": "effects/sandstorm_top/sandstorm_cloud",
}

# the three Dark Ages grave kinds, keyed by what the grave is hiding
GRAVES = {
    "empty": "gravestones/Dark_Noop/Dark_Noop_132x160",
    "sun": "gravestones/Dark_Sun/Dark_Sun_132x160",
    "plantfood": "gravestones/Dark_Plantfood/Dark_Plantfood_132x160",
}

# the three vase kinds the Vasebreaker minigame uses
VASES = {
    "normal": "vasebreaker/Vase_brown/Vase_brown_115x150",
    "plant": "vasebreaker/Vase_green/Vase_green_115x150",
    "ghoul": "vasebreaker/Vase_gargantuar/Vase_gargantuar_115x150",
}

# flat UI sprites, referenced by their exact dump path
UI = {
    "coin": "UI/hud_ingame/coin",
    "gem": "UI/hud_ingame/gem",
    "shovel": "UI/hud_ingame/shovel_icon",
    "shovel-button": "UI/hud_ingame/shovel_button",
    "alert-ring": "UI/hud_ingame/alert_ring",
    "zombie-head": "UI/hud_ingame/challenge_zombie_head",
    "plant-food": "UI/hud_ingame/plantfood_button",
    "sun": "effects/sun/sun_166x166",
    "sun-small": "effects/sun/sun_78x78",
}


def load_sprites(resources_json):
    """All 768-res sprites that live inside an atlas, keyed by lowercase path."""
    data = json.loads(Path(resources_json).read_text())
    sprites = {}
    for group in data["groups"]:
        if group.get("type") != "simple" or group.get("res") != str(RES):
            continue
        for res in group.get("resources", []):
            if res.get("type") != "Image" or "parent" not in res:
                continue
            key = res["path"].replace("\\", "/").lower()
            sprites[key] = res
    return sprites


def atlas_png(sprite):
    """ATLASIMAGE_ATLAS_UI_SEEDPACKETS_768_00 -> UI_SEEDPACKETS_768_00.PNG"""
    return sprite["parent"].replace("ATLASIMAGE_ATLAS_", "") + ".PNG"


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    sprites = load_sprites(root / "RESOURCES.json")

    wanted = {}
    for our, theirs in PLANTS.items():
        wanted[f"plants/{our}"] = f"images/{RES}/initial/ui/packets/{theirs}"
    for our, theirs in PLANTS_NO_PACKET.items():
        wanted[f"plants/{our}"] = f"images/{RES}/initial/{theirs}"
    for our, theirs in {**ZOMBIES}.items():
        wanted[f"zombies/{our}"] = f"images/{RES}/initial/ui/almanac/packets_zombies/{theirs}"
    for our, theirs in ZOMBOTANY.items():
        wanted[f"zombies/{our}"] = f"images/{RES}/initial/ui/packets/{theirs}"
    for our, theirs in BACKGROUNDS.items():
        wanted[f"backgrounds/{our}"] = f"images/{RES}/full/backgrounds/{theirs}/texture"
        wanted[f"backgrounds/{our}-right"] = f"images/{RES}/full/backgrounds/{theirs}/texture_right"
    for our, theirs in BOSSES.items():
        wanted[f"bosses/{our}"] = f"images/{RES}/initial/ui/almanac/packets_zombies/{theirs}"
    for our, theirs in ZOMBIE_BARE.items():
        wanted[f"zombies/{our}-bare"] = f"images/{RES}/initial/ui/almanac/packets_zombies/{theirs}"
    for our, theirs in ICE.items():
        wanted[f"ice/{our}"] = f"images/{RES}/initial/{theirs}"
    for our, theirs in ZOMBIE_PARTS.items():
        wanted[f"parts/{our}"] = f"images/{RES}/initial/{theirs}"
    for our, theirs in SANDSTORM.items():
        wanted[f"props/{our}"] = f"images/{RES}/initial/{theirs}"
    for our, theirs in GRAVES.items():
        wanted[f"props/grave-{our}"] = f"images/{RES}/full/{theirs}"
    for our, theirs in VASES.items():
        wanted[f"props/vase-{our}"] = f"images/{RES}/full/{theirs}"
    wanted["backgrounds/greenhouse"] = f"images/{RES}/full/{GREENHOUSE}"
    for our, theirs in UI.items():
        wanted[f"ui/{our}"] = f"images/{RES}/initial/{theirs}"

    lines, missing = [], []
    for name, path in sorted(wanted.items()):
        sprite = sprites.get(path.lower())
        if sprite is None:  # sprites are split across the initial/ and full/ trees
            sprite = sprites.get(path.lower().replace("/initial/", "/full/"))
        if sprite is None:
            sprite = sprites.get(path.lower().replace("/full/", "/initial/"))
        if sprite is None:
            missing.append(f"{name}  <-  {path}")
            continue
        lines.append("\t".join([name, atlas_png(sprite), str(sprite["ax"]),
                                str(sprite["ay"]), str(sprite["aw"]), str(sprite["ah"])]))

    Path("manifest.tsv").write_text("\n".join(lines) + "\n")
    print(f"resolved {len(lines)} sprites -> manifest.tsv")
    if missing:
        print(f"\n{len(missing)} UNRESOLVED:")
        for m in missing:
            print("   ", m)


if __name__ == "__main__":
    main()
