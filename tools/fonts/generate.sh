#!/usr/bin/env sh
# Regenerates the Inter MSDF atlases in src/main/resources/assets/justaclient/{msdf,textures/font}.
# Needs Node 18+ and Python 3. Run from the repository root: sh tools/fonts/generate.sh
set -e
ROOT="$(pwd)"
WORK="$(mktemp -d)"
cd "$WORK"
npm init -y >/dev/null
npm install --silent inter-ui@4.1.1 msdf-bmfont-xml@2.8.0
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
echo "Atlases written. Keep PX_RANGE in JacPipelines equal to -r (6)."
