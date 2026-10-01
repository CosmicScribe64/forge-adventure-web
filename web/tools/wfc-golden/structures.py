"""Writes the Shandalar world's structure definitions as TSV for GoldenStructures.java."""
import json, os, sys
res = sys.argv[1]
common = os.path.join(res, "adventure", "common")
world = json.load(open(os.path.join(res, "adventure", "Shandalar", "world", "world.json")))
for biome_path in world["biomesNames"]:
    biome = json.load(open(os.path.join(common, biome_path)))
    bw = round(biome.get("width", 1) * world["width"])
    bh = round(biome.get("height", 1) * world["height"])
    for s in biome.get("structures") or []:
        mapping = s.get("mappingInfo") or []
        print("\t".join(str(v) for v in [
            os.path.join(common, s["sourcePath"]),
            os.path.join(common, s["maskPath"]) if s.get("maskPath") else "-",
            s.get("N", 3), s.get("symmetry", 2), str(s.get("periodicInput", True)).lower(),
            str(s.get("periodicOutput", True)).lower(), s.get("ground", 0), s["width"], s["height"], bw, bh,
            ",".join(m["color"] for m in mapping) or "-",
            ",".join(str(m.get("collision", False)).lower() for m in mapping) or "-"]))
