#!/usr/bin/env python3
"""Add missing Chatrix locale keys without replacing existing translations."""

import json
from pathlib import Path

LANG_DIR = Path(__file__).resolve().parents[1] / "src/main/resources/assets/chatrix/lang"


def main() -> None:
    english = json.loads((LANG_DIR / "en_us.json").read_text(encoding="utf-8"))
    for path in sorted(LANG_DIR.glob("*.json")):
        if path.name == "en_us.json":
            continue
        existing = json.loads(path.read_text(encoding="utf-8"))
        merged = {
            key: existing.get(key, english[key])
            for key in english
        }
        path.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Updated {path.name}: {len(merged)} keys")


if __name__ == "__main__":
    main()
