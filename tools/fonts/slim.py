# Drops fields Justaclient does not read from msdf-bmfont JSON so the shipped files stay small.
import json
import sys

data = json.load(open(sys.argv[1], encoding="utf-8"))
data["pages"] = [sys.argv[2] + ".png"]
data["info"].pop("charset", None)
for c in data["chars"]:
    for key in ("index", "chnl", "page", "char"):
        c.pop(key, None)
json.dump(data, sys.stdout, separators=(",", ":"))
