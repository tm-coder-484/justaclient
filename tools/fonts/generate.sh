#!/usr/bin/env sh
# Regenerates the Inter MSDF atlases and the Lucide icon atlas in src/main/resources/assets/justaclient/{msdf,textures/font}.
# Needs Node 18+ and Python 3. Run from the repository root: sh tools/fonts/generate.sh
set -e
ROOT="$(pwd)"
WORK="$(mktemp -d)"
cd "$WORK"
npm init -y >/dev/null
npm install --silent inter-ui@4.1.1 lucide-static@0.460.0 msdf-bmfont-xml@2.8.0
python3 -m venv venv
venv/bin/pip install --quiet fonttools brotli
for W in Regular SemiBold; do
	venv/bin/python -c "from fontTools.ttLib import TTFont; f=TTFont('node_modules/inter-ui/web/Inter-$W.woff2'); f.flavor=None; f.save('Inter-$W.ttf')"
done
python3 "$ROOT/tools/fonts/charset.py" > charset.txt
for W in Regular SemiBold; do
	LOWER=$(echo "$W" | tr '[:upper:]' '[:lower:]')
	node_modules/.bin/msdf-bmfont -f json -i charset.txt -s 48 -r 6 -m 1024,1024 -t msdf --smart-size --pot -o "inter-$LOWER" "Inter-$W.ttf"
	python3 "$ROOT/tools/fonts/slim.py" "Inter-$W.json" "inter-$LOWER" > "$ROOT/src/main/resources/assets/justaclient/msdf/inter-$LOWER.json"
	cp "inter-$LOWER.png" "$ROOT/src/main/resources/assets/justaclient/textures/font/inter-$LOWER.png"
done
# Icons: Lucide, single-channel SDF because its glyphs are built from overlapping strokes that confuse MSDF.
python3 "$ROOT/tools/fonts/icon_charset.py" node_modules/lucide-static/font/info.json "$ROOT/tools/fonts/icons.txt" > icons.txt
node_modules/.bin/msdf-bmfont -f json -i icons.txt -s 48 -r 6 -m 512,512 -t sdf --smart-size --pot -o lucide node_modules/lucide-static/font/lucide.ttf
python3 "$ROOT/tools/fonts/slim.py" lucide.json lucide > "$ROOT/src/main/resources/assets/justaclient/msdf/lucide.json"
cp lucide.png "$ROOT/src/main/resources/assets/justaclient/textures/font/lucide.png"
cp node_modules/lucide-static/LICENSE "$ROOT/src/main/resources/assets/justaclient/msdf/Lucide-LICENSE.txt"
echo "Atlases written. Keep PX_RANGE in JacPipelines equal to -r (6)."
