# Characters baked into the atlases: printable ASCII, Latin-1 and a few UI symbols.
import sys

chars = "".join(chr(c) for c in range(32, 127)) + "".join(chr(c) for c in range(160, 256)) + "•…–—‘’“”€™°×→←↑↓"
sys.stdout.reconfigure(encoding="utf-8")
sys.stdout.write(chars)
