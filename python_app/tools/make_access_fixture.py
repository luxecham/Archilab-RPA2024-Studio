"""Fabrique la fixture Access de démonstration à partir du texte ETABS.

Prérequis : JDK 21 et les jars Jackcess du cache Gradle (voir build_access_fixture.bat).
"""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from parsers import read_tabular

GRADLE = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
TOOLS = ROOT / "tools"


def find_jar(group: str, artifact: str) -> Path:
    candidates = list((GRADLE / group / artifact).rglob("*.jar"))
    if not candidates:
        raise FileNotFoundError(f"Jar introuvable : {group}:{artifact}")
    return sorted(candidates, key=lambda p: p.stat().st_mtime)[-1]


def classpath() -> str:
    jars = [
        find_jar("com.healthmarketscience.jackcess", "jackcess"),
        find_jar("org.apache.commons", "commons-lang3"),
        find_jar("commons-logging", "commons-logging"),
        find_jar("org.apache.commons", "commons-collections4"),
    ]
    return os.pathsep.join(str(j) for j in jars)


def main() -> None:
    source = ROOT / "samples" / "DEMO_ETABS.txt"
    output = ROOT / "samples" / "DEMO_ETABS.mdb"
    tables = read_tabular(source)
    with tempfile.TemporaryDirectory() as temp:
        protocol = Path(temp) / "tables.txt"
        with protocol.open("w", encoding="utf-8", newline="\n") as handle:
            for table in tables:
                handle.write("T\t" + table.name + "\n")
                handle.write("H\t" + "\t".join(table.headers) + "\n")
                for row in table.rows:
                    handle.write("R\t" + "\t".join(row) + "\n")
                handle.write("\n")
        cp = classpath()
        classes = TOOLS / "classes"
        classes.mkdir(exist_ok=True)
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "-cp", cp, "-d", str(classes),
             str(TOOLS / "MakeAccessFixture.java")],
            check=True,
        )
        subprocess.run(
            ["java", "-cp", os.pathsep.join([str(classes), cp]),
             "MakeAccessFixture", str(protocol), str(output)],
            check=True,
        )
    print(f"Fixture Access : {output} ({output.stat().st_size} octets)")


if __name__ == "__main__":
    main()
