"""Genere un jeu de donnees de demonstration ETABS + SAFE.

Le jeu represente un batiment R+4, 3 travees x 3 travees, avec 4 voiles
et 20 panneaux de plancher. Il sert uniquement aux tests et a la demonstration :
les moteurs de production ne connaissent aucune de ces dimensions.
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parent
LEVELS = 5
GRID = [0.0, 4.0, 8.0, 12.0]
STORY_HEIGHT = 3.0


def _table(name: str, headers: list[str], rows: list[list[str]]) -> str:
    lines = [name, "\t".join(headers)]
    lines.extend("\t".join(str(c) for c in row) for row in rows)
    return "\n".join(lines)


def _point(level: int, ix: int, iy: int) -> str:
    return f"J{level}_{ix}_{iy}"


def build_etabs_text() -> str:
    blocks: list[str] = []
    blocks.append(_table("Program Control", ["CurrUnits", "Units"], [["kN, m, C", "kN, m, C"]]))

    stories = [
        [f"N{level}", str(STORY_HEIGHT), str(level * STORY_HEIGHT)]
        for level in range(1, LEVELS + 1)
    ]
    blocks.append(_table("Story Data", ["Story", "Height", "Elevation"], stories))

    points = []
    for level in range(LEVELS + 1):
        for ix, x in enumerate(GRID):
            for iy, y in enumerate(GRID):
                points.append([_point(level, ix, iy), str(x), str(y), str(level * STORY_HEIGHT)])
    blocks.append(_table("Point Coordinates", ["UniqueName", "X", "Y", "Z"], points))

    sections = [
        ["C50x50", "C25/30", "Rectangular", "0.50", "0.50"],
        ["P30x35", "C25/30", "Rectangular", "0.35", "0.30"],
    ]
    blocks.append(_table("Frame Section Properties", ["SectionName", "Material", "Shape", "t3", "t2"], sections))
    blocks.append(_table("Area Section Properties", ["SectionName", "Material", "Type", "Thickness", "TotalThickness"], [
        ["SB20", "C25/30", "Slab", "0.20", "0.20"],
        ["V20", "C25/30", "Wall", "0.20", "0.20"],
    ]))

    frames = []
    assignments = []
    for level in range(1, LEVELS + 1):
        for ix in range(len(GRID)):
            for iy in range(len(GRID)):
                fid = f"C{level}_{ix}_{iy}"
                frames.append([fid, _point(level - 1, ix, iy), _point(level, ix, iy), "Column", f"N{level}"])
                assignments.append([fid, "C50x50", f"N{level}"])
        for iy in range(len(GRID)):
            for ix in range(len(GRID) - 1):
                fid = f"BX{level}_{ix}_{iy}"
                frames.append([fid, _point(level, ix, iy), _point(level, ix + 1, iy), "Beam", f"N{level}"])
                assignments.append([fid, "P30x35", f"N{level}"])
        for ix in range(len(GRID)):
            for iy in range(len(GRID) - 1):
                fid = f"BY{level}_{ix}_{iy}"
                frames.append([fid, _point(level, ix, iy), _point(level, ix, iy + 1), "Beam", f"N{level}"])
                assignments.append([fid, "P30x35", f"N{level}"])
    blocks.append(_table("Connectivity - Frame", ["Frame", "Joint1", "Joint2", "Type", "Story"], frames))
    blocks.append(_table("Frame Section Assignments", ["Frame", "Section", "Story"], assignments))

    areas = []
    area_assignments = []
    piers = []
    for level in range(1, LEVELS + 1):
        for ix in range(len(GRID) - 1):
            for iy in range(len(GRID) - 1):
                aid = f"S{level}_{ix}_{iy}"
                areas.append([aid,
                              _point(level, ix, iy), _point(level, ix + 1, iy),
                              _point(level, ix + 1, iy + 1), _point(level, ix, iy + 1),
                              "Floor", f"N{level}"])
                area_assignments.append([aid, "SB20", f"N{level}"])
        wall_specs = [
            ("VX1", _point(level - 1, 0, 0), _point(level - 1, 1, 0), _point(level, 1, 0), _point(level, 0, 0)),
            ("VX2", _point(level - 1, 2, 3), _point(level - 1, 3, 3), _point(level, 3, 3), _point(level, 2, 3)),
            ("VY1", _point(level - 1, 0, 0), _point(level - 1, 0, 1), _point(level, 0, 1), _point(level, 0, 0)),
            ("VY2", _point(level - 1, 3, 2), _point(level - 1, 3, 3), _point(level, 3, 3), _point(level, 3, 2)),
        ]
        for index, (pier, a, b, c, d) in enumerate(wall_specs, start=1):
            aid = f"W{level}_{index}"
            areas.append([aid, a, b, c, d, "Wall", f"N{level}"])
            area_assignments.append([aid, "V20", f"N{level}"])
            piers.append([aid, pier])
    blocks.append(_table("Connectivity - Area", ["Area", "Point1", "Point2", "Point3", "Point4", "Type", "Story"], areas))
    blocks.append(_table("Area Section Assignments", ["Area", "Section", "Story"], area_assignments))
    blocks.append(_table("Wall Pier Assignments", ["Area", "Pier"], piers))

    modal = [
        [1, 0.660, 60.0, 0.0, 0.0, 60.0, 0.0, 0.0, 0.0, 4.0, 0.0],
        [2, 0.620, 0.0, 60.0, 0.0, 60.0, 60.0, 0.0, 1.0, 0.0, 0.0],
        [3, 0.560, 15.0, 0.0, 0.0, 75.0, 60.0, 0.0, 0.0, 20.0, 0.0],
        [4, 0.520, 0.0, 15.0, 0.0, 75.0, 75.0, 0.0, 2.0, 0.0, 0.0],
        [5, 0.480, 10.0, 0.0, 0.0, 85.0, 75.0, 0.0, 0.0, 30.0, 0.0],
        [6, 0.440, 0.0, 10.0, 0.0, 85.0, 85.0, 0.0, 3.0, 0.0, 0.0],
        [7, 0.300, 5.0, 0.0, 0.0, 90.0, 85.0, 0.0, 0.0, 8.0, 0.0],
        [8, 0.280, 0.0, 5.0, 0.0, 90.0, 90.0, 0.0, 1.0, 0.0, 0.0],
        [9, 0.150, 2.0, 0.0, 0.0, 92.0, 90.0, 0.0, 0.0, 3.0, 0.0],
        [10, 0.140, 0.0, 2.0, 0.0, 92.0, 92.0, 0.0, 1.0, 0.0, 0.0],
        [11, 0.110, 1.0, 0.0, 0.0, 93.0, 92.0, 0.0, 0.0, 2.0, 0.0],
        [12, 0.100, 0.0, 1.0, 0.0, 93.0, 93.0, 0.0, 1.0, 0.0, 3.0],
    ]
    blocks.append(_table("Modal Participating Mass Ratios",
                         ["Mode", "Period", "UX", "UY", "UZ", "SumUX", "SumUY", "SumUZ", "RX", "RY", "RZ"],
                         modal))

    shears = []
    displacements = []
    for level in range(1, LEVELS + 1):
        story = f"N{level}"
        factor = LEVELS - level + 1
        weight = 800.0 * factor
        shears.append([story, "Bottom", "WI", "0", "0", str(weight)])
        shears.append([story, "Bottom", "EX", str(60.0 * factor), "0", "0"])
        shears.append([story, "Bottom", "EY", "0", str(52.0 * factor), "0"])
        displacements.append([story, "EX", str(0.004 * level), "0", "0", "D1"])
        displacements.append([story, "EY", "0", str(0.0035 * level), "0", "D1"])
    blocks.append(_table("Story Shears", ["Story", "Location", "LoadCase", "VX", "VY", "P"], shears))
    blocks.append(_table("Diaphragm CM Displacements",
                         ["Story", "LoadCase", "UX", "UY", "UZ", "Diaphragm"], displacements))

    column_forces = []
    for level in range(1, LEVELS + 1):
        for ix in range(len(GRID)):
            for iy in range(len(GRID)):
                cid = f"C{level}_{ix}_{iy}"
                for combo in ("COMB2", "COMB3", "COMB1"):
                    column_forces.append([cid, combo, f"N{level}", "0", str(-300 - 10 * level),
                                          "25", "15", "25", str(35 + 2 * level)])
    blocks.append(_table("Column Forces",
                         ["Column", "LoadCase", "Story", "Station", "P", "V2", "V3", "M2", "M3"],
                         column_forces))

    beam_forces = []
    for level in range(1, LEVELS + 1):
        for iy in range(len(GRID)):
            for ix in range(len(GRID) - 1):
                bid = f"BX{level}_{ix}_{iy}"
                sign = 1 if (ix + iy) % 2 == 0 else -1
                beam_forces.append([bid, "COMB1", f"N{level}", "2.0", "0",
                                    str(80 + 5 * level), "0", "20",
                                    str(sign * (95 + 4 * level))])
                beam_forces.append([bid, "COMB2", f"N{level}", "2.0", "0",
                                    str(70 + 5 * level), "0", "18", str(sign * 80)])
        for ix in range(len(GRID)):
            for iy in range(len(GRID) - 1):
                bid = f"BY{level}_{ix}_{iy}"
                sign = -1 if (ix + iy) % 2 == 0 else 1
                beam_forces.append([bid, "COMB1", f"N{level}", "2.0", "0",
                                    str(70 + 4 * level), "0", "18", str(sign * (85 + 3 * level))])
    blocks.append(_table("Beam Forces",
                         ["Beam", "LoadCase", "Story", "Station", "P", "V2", "V3", "M2", "M3"],
                         beam_forces))

    centers = []
    for level in range(1, LEVELS + 1):
        centers.append([f"N{level}", "D1", "6.0", "6.0", "6.02", "5.98", str(800 * (LEVELS - level + 1)), "0"])
    blocks.append(_table("Centers Of Mass And Rigidity",
                         ["Story", "Diaphragm", "XCM", "YCM", "XCR", "YCR", "MassX", "MassY"],
                         centers))
    return "\n\n".join(blocks) + "\n"


def build_safe_text() -> str:
    pressure = _table(
        "Soil Pressure",
        ["Area", "Story", "LoadCase", "Pressure", "Displacement"],
        [
            ["S1_0_0", "N1", "COMB1", "180.0", "0.0050"],
            ["S1_1_0", "N1", "COMB1", "172.0", "0.0048"],
            ["S1_0_1", "N1", "COMB1", "176.0", "0.0049"],
            ["S1_1_1", "N1", "COMB1", "168.0", "0.0046"],
        ],
    )
    punch = _table(
        "Punching Shear",
        ["Area", "Vu", "VRd", "Ratio"],
        [["S1_0_0", "260.0", "520.0", "0.50"], ["S1_1_1", "240.0", "510.0", "0.47"]],
    )
    return pressure + "\n\n" + punch + "\n"


def build_xlsx(etabs_text: str, path: Path) -> None:
    import openpyxl
    workbook = openpyxl.Workbook()
    workbook.remove(workbook.active)
    for block in etabs_text.strip().split("\n\n"):
        lines = [line for line in block.splitlines() if line.strip()]
        if len(lines) < 2:
            continue
        name = lines[0]
        headers = lines[1].split("\t")
        rows = [line.split("\t") for line in lines[2:]]
        sheet = workbook.create_sheet(title=name[:31])
        sheet.append(headers)
        for row in rows:
            sheet.append(row)
    workbook.save(path)


def main() -> None:
    text = build_etabs_text()
    (ROOT / "DEMO_ETABS.txt").write_text(text, encoding="utf-8")
    (ROOT / "DEMO_SAFE.txt").write_text(build_safe_text(), encoding="utf-8")
    build_xlsx(text, ROOT / "DEMO_ETABS.xlsx")
    print("Ecrit :", ROOT / "DEMO_ETABS.txt")
    print("Ecrit :", ROOT / "DEMO_SAFE.txt")
    print("Ecrit :", ROOT / "DEMO_ETABS.xlsx")


if __name__ == "__main__":
    main()
