# Prints the characters for the Lucide icons listed in icons.txt, for msdf-bmfont's charset file.
# Usage: python3 icon_charset.py <lucide-static/font/info.json> <icons.txt>
import json
import sys

info = json.load(open(sys.argv[1], encoding="utf-8"))
names = [n.strip() for n in open(sys.argv[2], encoding="utf-8") if n.strip()]
out = []
for name in names:
    entry = info.get(name)
    if entry is None:
        sys.exit("No Lucide icon named " + name)
    out.append(chr(int(entry["encodedCode"].lstrip("\\"), 16)))
sys.stdout.write("".join(out))
