"""Export vectoriel des plans de coffrage, de ferraillage et de la nomenclature.

Le trace est construit a partir des coordonnees reelles du modele importe. Aucune
position de poteau, poutre ou voile n'est codee en dur. Les sorties sont :
  - DXF AC1024 (AutoCAD 2010) en millimetres, calques normalises ;
  - SVG vectoriel ;
  - CSV de nomenclature des armatures.
"""

from __future__ import annotations

import csv
import math
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Iterable, Sequence
from xml.sax.saxutils import escape

__all__ = ["PlanExportResult", "export_plans", "DxfDocument"]


LAYERS = {
    "AXES": (1, 1),
    "COLONNES": (7, 3),
    "POUTRES": (2, 4),
    "VOILES": (4, 5),
    "PLANCHERS": (8, 6),
    "COTES": (3, 7),
    "TEXTE": (7, 8),
    "CADRES": (6, 9),
    "BARRES": (5, 10),
    "HACHURES": (9, 11),
    "CARTOUCHE": (7, 12),
}


class DxfDocument:
    """Ecrivain DXF ASCII minimal mais conforme (LINE, LWPOLYLINE, CIRCLE, TEXT)."""

    def __init__(self):
        self.entities: list[str] = []

    def _tag(self, code: int, value: object) -> str:
        return f"{code}\n{value}\n"

    def line(self, x1: float, y1: float, x2: float, y2: float, layer: str = "0") -> None:
        out = self._tag(0, "LINE") + self._tag(100, "AcDbEntity") + self._tag(8, layer)
        out += self._tag(100, "AcDbLine")
        out += self._tag(10, f"{x1:.4f}") + self._tag(20, f"{y1:.4f}") + self._tag(30, "0.0")
        out += self._tag(11, f"{x2:.4f}") + self._tag(21, f"{y2:.4f}") + self._tag(31, "0.0")
        self.entities.append(out)

    def polyline(self, points: Sequence[tuple[float, float]], layer: str = "0",
                 closed: bool = False, width: float = 0.0) -> None:
        if len(points) < 2:
            return
        out = self._tag(0, "LWPOLYLINE") + self._tag(100, "AcDbEntity") + self._tag(8, layer)
        out += self._tag(100, "AcDbPolyline") + self._tag(90, len(points))
        out += self._tag(70, "1" if closed else "0")
        if width > 0:
            out += self._tag(43, f"{width:.4f}")
        for x, y in points:
            out += self._tag(10, f"{x:.4f}") + self._tag(20, f"{y:.4f}")
        self.entities.append(out)

    def circle(self, x: float, y: float, radius: float, layer: str = "0") -> None:
        out = self._tag(0, "CIRCLE") + self._tag(100, "AcDbEntity") + self._tag(8, layer)
        out += self._tag(100, "AcDbCircle") + self._tag(10, f"{x:.4f}") + self._tag(20, f"{y:.4f}")
        out += self._tag(30, "0.0") + self._tag(40, f"{radius:.4f}")
        self.entities.append(out)

    def text(self, x: float, y: float, value: str, height: float = 2.5,
             layer: str = "TEXTE", align: int = 0) -> None:
        value = str(value).replace("\n", " ")[:250]
        out = self._tag(0, "TEXT") + self._tag(100, "AcDbEntity") + self._tag(8, layer)
        out += self._tag(100, "AcDbText") + self._tag(10, f"{x:.4f}") + self._tag(20, f"{y:.4f}")
        out += self._tag(30, "0.0") + self._tag(40, f"{height:.3f}") + self._tag(1, value)
        out += self._tag(7, "STANDARD")
        if align:
            out += self._tag(72, align)
            out += self._tag(11, f"{x:.4f}") + self._tag(21, f"{y:.4f}") + self._tag(31, "0.0")
        out += self._tag(100, "AcDbText")
        self.entities.append(out)

    def save(self, path: str | Path, extents: tuple[float, float, float, float]) -> Path:
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        min_x, min_y, max_x, max_y = extents
        chunks: list[str] = []
        chunks.append("0\nSECTION\n2\nHEADER\n")
        chunks.append("9\n$ACADVER\n1\nAC1024\n")
        chunks.append("9\n$INSUNITS\n70\n4\n")
        chunks.append("9\n$MEASUREMENT\n70\n1\n")
        chunks.append(f"9\n$EXTMIN\n10\n{min_x:.4f}\n20\n{min_y:.4f}\n30\n0.0\n")
        chunks.append(f"9\n$EXTMAX\n10\n{max_x:.4f}\n20\n{max_y:.4f}\n30\n0.0\n")
        chunks.append("0\nENDSEC\n")
        chunks.append("0\nSECTION\n2\nTABLES\n")
        chunks.append("0\nTABLE\n2\nLTYPE\n70\n1\n")
        chunks.append("0\nLTYPE\n2\nCONTINUOUS\n70\n0\n3\nSolid line\n72\n65\n73\n0\n40\n0.0\n")
        chunks.append("0\nENDTAB\n")
        chunks.append(f"0\nTABLE\n2\nLAYER\n70\n{len(LAYERS)}\n")
        for name, (color, _) in LAYERS.items():
            chunks.append(
                f"0\nLAYER\n2\n{name}\n70\n0\n62\n{color}\n6\nCONTINUOUS\n"
            )
        chunks.append("0\nENDTAB\n")
        chunks.append("0\nTABLE\n2\nSTYLE\n70\n1\n")
        chunks.append("0\nSTYLE\n2\nSTANDARD\n70\n0\n40\n0.0\n41\n1.0\n50\n0.0\n71\n0\n42\n2.5\n")
        chunks.append("3\ntxt\n4\n\n")
        chunks.append("0\nENDTAB\n0\nENDSEC\n")
        chunks.append("0\nSECTION\n2\nBLOCKS\n0\nENDSEC\n")
        chunks.append("0\nSECTION\n2\nENTITIES\n")
        chunks.extend(self.entities)
        chunks.append("0\nENDSEC\n0\nEOF\n")
        path.write_text("".join(chunks), encoding="utf-8")
        return path


@dataclass
class PlanExportResult:
    formwork_dxf: Path | None = None
    reinforcement_dxf: Path | None = None
    formwork_svg: Path | None = None
    reinforcement_svg: Path | None = None
    schedule_csv: Path | None = None
    story: str = ""
    entities: dict[str, int] = field(default_factory=dict)
    warnings: list[str] = field(default_factory=list)

    @property
    def files(self) -> list[Path]:
        return [p for p in (self.formwork_dxf, self.reinforcement_dxf,
                            self.formwork_svg, self.reinforcement_svg, self.schedule_csv) if p]


@dataclass
class _Plot:
    scale: float
    offset_x: float
    offset_y: float
    sheet_w: float = 841.0
    sheet_h: float = 594.0

    def xy(self, x_m: float, y_m: float) -> tuple[float, float]:
        return (self.offset_x + x_m * 1000.0 * self.scale,
                self.offset_y + y_m * 1000.0 * self.scale)


def _select_story(assessment, story_name: str | None) -> str:
    stories = assessment.model.stories
    if not stories:
        return ""
    if story_name:
        match = next((s for s in stories if s.name.strip().lower() == story_name.strip().lower()), None)
        if match:
            return match.name
    main = assessment.model.main_stories or stories
    return main[-1].name


def _story_elements(assessment, story_name: str):
    model = assessment.model
    story = next((s for s in model.stories if s.name == story_name), None)
    elevation = story.elevation if story else 0.0
    columns = []
    for frame in model.columns:
        node = model.points.get(frame.end)
        if node and abs(node.z - elevation) <= 0.15:
            columns.append((frame, node))
    beams = [f for f in model.beams if f.story == story_name]
    walls = [a for a in model.walls if a.story == story_name]
    slabs = [a for a in model.slabs if a.story == story_name]
    return columns, beams, walls, slabs


def _extents(assessment, story_name: str) -> tuple[float, float, float, float]:
    points = []
    for point in assessment.model.points.values():
        points.append((point.x, point.y))
    if not points:
        return 0.0, 0.0, 10.0, 10.0
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    return min(xs), min(ys), max(xs), max(ys)


def _make_plot(assessment, story_name: str) -> _Plot:
    min_x, min_y, max_x, max_y = _extents(assessment, story_name)
    span_x = max(1.0, max_x - min_x)
    span_y = max(1.0, max_y - min_y)
    drawing_w, drawing_h = 500.0, 390.0
    scale = min(drawing_w / (span_x * 1000.0), drawing_h / (span_y * 1000.0))
    offset_x = 70.0 + (drawing_w - span_x * 1000.0 * scale) / 2.0 - min_x * 1000.0 * scale
    offset_y = 120.0 + (drawing_h - span_y * 1000.0 * scale) / 2.0 - min_y * 1000.0 * scale
    return _Plot(scale, offset_x, offset_y)


def _draw_sheet(doc: DxfDocument, plot: _Plot, title: str, subtitle: str,
                project: str, joint: str, status: str, notes: Sequence[str],
                schedule: Sequence[Sequence[str]] | None = None) -> None:
    # Cadre et cartouche.
    doc.polyline([(10, 10), (plot.sheet_w - 10, 10), (plot.sheet_w - 10, plot.sheet_h - 10),
                  (10, plot.sheet_h - 10)], "CARTOUCHE", closed=True)
    tx = plot.sheet_w - 200
    ty = 15
    doc.polyline([(tx, ty), (plot.sheet_w - 15, ty), (plot.sheet_w - 15, ty + 70),
                  (tx, ty + 70)], "CARTOUCHE", closed=True)
    doc.text(tx + 5, ty + 62, "ARCHILAB RPA2024 NoteDeCalcul Studio", 4.2, "CARTOUCHE")
    doc.text(tx + 5, ty + 54, title, 3.6, "CARTOUCHE")
    doc.text(tx + 5, ty + 47, subtitle, 2.8, "CARTOUCHE")
    rows = [
        ("Projet", project), ("Joint", joint), ("Statut", status),
        ("Date", datetime.now().strftime("%d/%m/%Y")),
    ]
    y = ty + 39
    for label, value in rows:
        doc.text(tx + 5, y, f"{label} : {value}", 2.4, "CARTOUCHE")
        y -= 6
    doc.text(tx + 5, ty + 10, "Echelle : selon plan  |  Unites : mm", 2.2, "CARTOUCHE")

    # Notes a gauche du cartouche.
    doc.text(20, 85, "NOTES", 3.2, "TEXTE")
    for index, note in enumerate(notes):
        doc.text(20, 78 - index * 6, f"- {note}", 2.3, "TEXTE")

    if schedule:
        doc.text(20, 45, "NOMENCLATURE DES ARMATURES (extrait)", 3.0, "TEXTE")
        header = schedule[0]
        positions = [20, 70, 130, 220, 300, 350]
        for i, cell in enumerate(header[:6]):
            doc.text(positions[i], 39, str(cell), 2.2, "TEXTE")
        for row_index, row in enumerate(schedule[1:16], start=1):
            for i, cell in enumerate(row[:6]):
                doc.text(positions[i], 39 - row_index * 4.2, str(cell), 2.0, "TEXTE")


def _draw_common(doc: DxfDocument, assessment, story_name: str, plot: _Plot) -> int:
    model = assessment.model
    columns, beams, walls, slabs = _story_elements(assessment, story_name)
    count = 0

    for gx in model.grid_x:
        p1 = plot.xy(gx, min(model.grid_y) - 1.0)
        p2 = plot.xy(gx, max(model.grid_y) + 1.0)
        doc.line(*p1, *p2, "AXES")
        doc.text(p1[0] + 1.5, p1[1] - 5.0, f"X={gx:.2f}", 2.2, "AXES")
        count += 2
    for gy in model.grid_y:
        p1 = plot.xy(min(model.grid_x) - 1.0, gy)
        p2 = plot.xy(max(model.grid_x) + 1.0, gy)
        doc.line(*p1, *p2, "AXES")
        doc.text(p1[0] - 15.0, p1[1] + 1.0, f"Y={gy:.2f}", 2.2, "AXES")
        count += 2

    for frame in beams:
        start = model.points.get(frame.start)
        end = model.points.get(frame.end)
        if not start or not end:
            continue
        doc.line(*plot.xy(start.x, start.y), *plot.xy(end.x, end.y), "POUTRES")
        count += 1

    for slab in slabs:
        pts = [model.points[n] for n in slab.nodes if n in model.points]
        if len(pts) >= 3:
            doc.polyline([plot.xy(p.x, p.y) for p in pts], "PLANCHERS", closed=True)
            section = model.sections.get(slab.section_name)
            centroid_x = sum(p.x for p in pts) / len(pts)
            centroid_y = sum(p.y for p in pts) / len(pts)
            cx, cy = plot.xy(centroid_x, centroid_y)
            doc.text(cx, cy, section.label if section else slab.section_name, 2.4, "TEXTE", align=1)
            count += 2

    for wall in walls:
        pts = [model.points[n] for n in wall.nodes if n in model.points]
        if len(pts) >= 3:
            doc.polyline([plot.xy(p.x, p.y) for p in pts], "VOILES", closed=True, width=0.35)
            centroid_x = sum(p.x for p in pts) / len(pts)
            centroid_y = sum(p.y for p in pts) / len(pts)
            doc.text(*plot.xy(centroid_x, centroid_y), wall.pier or wall.section_name, 2.6, "TEXTE", align=1)
            count += 2

    for frame, node in columns:
        section = model.sections.get(frame.section_name)
        b = (section.b if section else 30.0) / 1000.0
        h = (section.h if section else 30.0) / 1000.0
        p1 = plot.xy(node.x - b / 2, node.y - h / 2)
        p2 = plot.xy(node.x + b / 2, node.y + h / 2)
        doc.polyline([(p1[0], p1[1]), (p2[0], p1[1]), (p2[0], p2[1]), (p1[0], p2[1])],
                     "COLONNES", closed=True)
        doc.line(p1[0], p1[1], p2[0], p2[1], "HACHURES")
        count += 2

    _draw_dimensions(doc, model, plot)
    count += 2 * (len(model.grid_x) + len(model.grid_y))
    return count


def _draw_dimensions(doc: DxfDocument, model, plot: _Plot) -> None:
    """Cotation lineaire entre axes, en metres."""
    if len(model.grid_x) >= 2:
        y_line = plot.xy(min(model.grid_x), min(model.grid_y) - 1.2)[1]
        for a, b in zip(model.grid_x, model.grid_x[1:]):
            x1 = plot.xy(a, min(model.grid_y))[0]
            x2 = plot.xy(b, min(model.grid_y))[0]
            doc.line(x1, y_line, x2, y_line, "COTES")
            doc.line(x1, y_line - 2, x1, y_line + 2, "COTES")
            doc.line(x2, y_line - 2, x2, y_line + 2, "COTES")
            doc.text((x1 + x2) / 2, y_line + 2.5, f"{b - a:.2f}", 2.2, "COTES", align=1)
    if len(model.grid_y) >= 2:
        x_line = plot.xy(min(model.grid_x) - 1.2, min(model.grid_y))[0]
        for a, b in zip(model.grid_y, model.grid_y[1:]):
            y1 = plot.xy(min(model.grid_x), a)[1]
            y2 = plot.xy(min(model.grid_x), b)[1]
            doc.line(x_line, y1, x_line, y2, "COTES")
            doc.line(x_line - 2, y1, x_line + 2, y1, "COTES")
            doc.line(x_line - 2, y2, x_line + 2, y2, "COTES")
            doc.text(x_line - 3, (y1 + y2) / 2, f"{b - a:.2f}", 2.2, "COTES", align=2)


def _draw_reinforcement(doc: DxfDocument, assessment, story_name: str,
                        plot: _Plot) -> int:
    model = assessment.model
    columns, beams, walls, _ = _story_elements(assessment, story_name)
    count = 0
    for frame, node in columns:
        design = assessment.reinforcement.column_designs.get(frame.section_name, {})
        label = design.get("label", "à définir")
        x, y = plot.xy(node.x, node.y)
        doc.circle(x, y, 7.0, "CADRES")
        doc.text(x + 8, y + 2, label, 2.2, "BARRES")
        doc.text(x + 8, y - 3, "T8 e=15", 1.9, "CADRES")
        count += 3
    for frame in beams:
        start = model.points.get(frame.start)
        end = model.points.get(frame.end)
        if not start or not end:
            continue
        design = assessment.reinforcement.beam_designs.get(frame.section_name, {})
        mid_x = (start.x + end.x) / 2.0
        mid_y = (start.y + end.y) / 2.0
        x, y = plot.xy(mid_x, mid_y)
        doc.line(*plot.xy(start.x, start.y), *plot.xy(end.x, end.y), "POUTRES")
        doc.text(x, y + 4, design.get("bottom", "-"), 2.1, "BARRES", align=1)
        doc.text(x, y - 6, design.get("top", "-") + " (appui)", 2.0, "BARRES", align=1)
        doc.text(x, y + 9, "T8 e=15", 1.8, "CADRES", align=1)
        count += 4
    for wall in walls:
        pts = [model.points[n] for n in wall.nodes if n in model.points]
        if len(pts) >= 3:
            doc.polyline([plot.xy(p.x, p.y) for p in pts], "VOILES", closed=True, width=0.35)
            cx = sum(p.x for p in pts) / len(pts)
            cy = sum(p.y for p in pts) / len(pts)
            x, y = plot.xy(cx, cy)
            doc.text(x, y + 4, "T12 e=20 (2 nappes)", 2.0, "BARRES", align=1)
            doc.text(x, y - 4, "rive : 4T16", 1.9, "BARRES", align=1)
            count += 3
    return count


def _schedule_rows(assessment) -> list[list[str]]:
    rows: list[list[str]] = [["Type", "Repère", "Section", "Ferraillage", "Longueur", "Nb"]]
    for family in assessment.model.column_families():
        design = assessment.reinforcement.column_designs.get(family.section.name, {})
        length = family.max_clear_height or 0.0
        rows.append(["Poteau", family.key, family.section.label, design.get("label", "-"),
                     f"{length:.2f} m", str(len(family.element_ids))])
    for family in assessment.model.beam_families():
        design = assessment.reinforcement.beam_designs.get(family.section.name, {})
        rows.append(["Poutre", family.key, family.section.label,
                     f"{design.get('bottom', '-')} / {design.get('top', '-')}",
                     f"{family.max_clear_span:.2f} m", str(len(family.element_ids))])
    for family in assessment.model.wall_families():
        rows.append(["Voile", family.key, family.section.label, "T12 e=20 + 4T16 rive",
                     f"{family.length_lw:.2f} m", str(len(family.element_ids))])
    for footing in assessment.foundations.footings:
        rows.append(["Semelle", footing.family,
                     f"{footing.b:.2f}x{footing.length:.2f}x{footing.thickness:.2f} m",
                     footing.message.split(";")[0].strip(), "-", "1"])
    return rows


def _write_schedule_csv(rows: list[list[str]], path: Path) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.writer(handle, delimiter=";")
        writer.writerows(rows)
    return path


def _svg_plan(assessment, story_name: str, mode: str, title: str) -> str:
    model = assessment.model
    min_x, min_y, max_x, max_y = _extents(assessment, story_name)
    span_x = max(1.0, max_x - min_x)
    span_y = max(1.0, max_y - min_y)
    width, height = 1188, 840
    margin = 70
    scale = min((width - 2 * margin) / span_x, (height - 220) / span_y)
    def xy(x, y):
        return (margin + (x - min_x) * scale, 160 + (max_y - y) * scale)
    columns, beams, walls, slabs = _story_elements(assessment, story_name)
    parts = [
        f'<?xml version="1.0" encoding="UTF-8"?>',
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<style>text{font-family:Arial,Helvetica,sans-serif;fill:#1f2933}'
        '.grid{stroke:#9aa5b1;stroke-width:0.6;stroke-dasharray:8 4}'
        '.beam{stroke:#d64545;stroke-width:2.0;fill:none}'
        '.wall{stroke:#2f6f9f;stroke-width:9;fill:#dbeafe;fill-opacity:.55}'
        '.slab{stroke:#7b8794;stroke-width:0.8;fill:#f8fafc;fill-opacity:.35}'
        '.col{fill:#1f2933;stroke:#1f2933;stroke-width:1}'
        '.title{font-size:22px;font-weight:bold}.sub{font-size:14px}.small{font-size:11px}'
        '</style>',
        '<rect x="10" y="10" width="1168" height="820" fill="none" stroke="#1f2933" stroke-width="1.4"/>',
        f'<text class="title" x="35" y="48">{escape(title)}</text>',
        f'<text class="sub" x="35" y="72">{escape(assessment.project_name)} – {escape(assessment.joint_name)} – {escape(story_name)}</text>',
    ]
    for gx in model.grid_x:
        x, _ = xy(gx, min_y)
        _, y1 = xy(gx, min_y)
        _, y2 = xy(gx, max_y)
        parts.append(f'<line class="grid" x1="{x:.1f}" y1="{y1:.1f}" x2="{x:.1f}" y2="{y2:.1f}"/>')
        parts.append(f'<text class="small" x="{x:.1f}" y="{y1 + 16:.1f}" text-anchor="middle">X={gx:.2f}</text>')
    for gy in model.grid_y:
        x1, y = xy(min_x, gy)
        x2, _ = xy(max_x, gy)
        parts.append(f'<line class="grid" x1="{x1:.1f}" y1="{y:.1f}" x2="{x2:.1f}" y2="{y:.1f}"/>')
        parts.append(f'<text class="small" x="{x1 - 8:.1f}" y="{y + 4:.1f}" text-anchor="end">Y={gy:.2f}</text>')
    for slab in slabs:
        pts = [model.points[n] for n in slab.nodes if n in model.points]
        if len(pts) >= 3:
            points = " ".join(f"{xy(p.x, p.y)[0]:.1f},{xy(p.x, p.y)[1]:.1f}" for p in pts)
            parts.append(f'<polygon class="slab" points="{points}"/>')
            center = xy(sum(p.x for p in pts) / len(pts), sum(p.y for p in pts) / len(pts))
            section = model.sections.get(slab.section_name)
            parts.append(f'<text class="small" x="{center[0]:.1f}" y="{center[1]:.1f}" text-anchor="middle">'
                         f'{escape(section.label if section else slab.section_name)}</text>')
    for wall in walls:
        pts = [model.points[n] for n in wall.nodes if n in model.points]
        if len(pts) >= 3:
            points = " ".join(f"{xy(p.x, p.y)[0]:.1f},{xy(p.x, p.y)[1]:.1f}" for p in pts)
            parts.append(f'<polygon class="wall" points="{points}"/>')
            center = xy(sum(p.x for p in pts) / len(pts), sum(p.y for p in pts) / len(pts))
            parts.append(f'<text class="small" x="{center[0]:.1f}" y="{center[1]:.1f}" text-anchor="middle">'
                         f'{escape(wall.pier or wall.section_name)}</text>')
    for frame in beams:
        start = model.points.get(frame.start)
        end = model.points.get(frame.end)
        if not start or not end:
            continue
        p1, p2 = xy(start.x, start.y), xy(end.x, end.y)
        parts.append(f'<line class="beam" x1="{p1[0]:.1f}" y1="{p1[1]:.1f}" x2="{p2[0]:.1f}" y2="{p2[1]:.1f}"/>')
        if mode == "reinforcement":
            design = assessment.reinforcement.beam_designs.get(frame.section_name, {})
            parts.append(f'<text class="small" x="{(p1[0]+p2[0])/2:.1f}" y="{(p1[1]+p2[1])/2-6:.1f}" '
                         f'text-anchor="middle">{escape(design.get("bottom", "-"))}</text>')
    for frame, node in columns:
        section = model.sections.get(frame.section_name)
        b = (section.b if section else 30.0) / 1000.0
        h = (section.h if section else 30.0) / 1000.0
        p1 = xy(node.x - b / 2, node.y + h / 2)
        w = max(8.0, b * scale)
        hh = max(8.0, h * scale)
        parts.append(f'<rect class="col" x="{p1[0]:.1f}" y="{p1[1]:.1f}" width="{w:.1f}" height="{hh:.1f}"/>')
        if mode == "reinforcement":
            design = assessment.reinforcement.column_designs.get(frame.section_name, {})
            parts.append(f'<text class="small" x="{p1[0] + w + 4:.1f}" y="{p1[1] + hh / 2 + 4:.1f}">'
                         f'{escape(design.get("label", "-"))}</text>')
    parts.append('<rect x="900" y="650" width="258" height="160" fill="#fff" stroke="#1f2933"/>')
    parts.append('<text class="sub" x="914" y="678">ARCHILAB RPA2024</text>')
    parts.append(f'<text class="small" x="914" y="700">Statut : {escape(assessment.status.value)}</text>')
    parts.append(f'<text class="small" x="914" y="718">QF = {assessment.regularity.qf:.2f} – R = {assessment.seismic.resolved.r:.1f}</text>')
    parts.append(f'<text class="small" x="914" y="736">Date : {assessment.generated_at}</text>')
    parts.append(f'<text class="small" x="914" y="754">Echelle : selon plan – mm</text>')
    parts.append(f'<text class="small" x="914" y="780">Titre : {escape(title)}</text>')
    parts.append('</svg>')
    return "\n".join(parts)


def export_plans(assessment, out_dir: str | Path, story_name: str | None = None) -> PlanExportResult:
    output = Path(out_dir)
    output.mkdir(parents=True, exist_ok=True)
    story = _select_story(assessment, story_name)
    result = PlanExportResult(story=story)
    if not story:
        result.warnings.append("Aucun niveau disponible : plans non générés.")
        return result
    plot = _make_plot(assessment, story)
    schedule = _schedule_rows(assessment)

    form = DxfDocument()
    _draw_sheet(form, plot, "PLAN DE COFFRAGE", f"Niveau {story}",
                assessment.project_name, assessment.joint_name, assessment.status.value,
                ["Les cotes sont en metres sur le modele.", "Vérifier les sections avant execution.",
                 "Plan généré automatiquement depuis les tableaux ETABS importés."], schedule)
    result.entities["formwork"] = _draw_common(form, assessment, story, plot)
    result.formwork_dxf = form.save(output / f"PLAN_COFFRAGE_{_safe(story)}.dxf",
                                    (0, 0, plot.sheet_w, plot.sheet_h))

    reinf = DxfDocument()
    _draw_sheet(reinf, plot, "PLAN DE FERRAILLAGE", f"Niveau {story}",
                assessment.project_name, assessment.joint_name, assessment.status.value,
                ["Armatures issues du calcul BAEL/RPA.", "Cadres et étriers à vérifier en zone critique.",
                 "Recouvrements selon BAEL 91."], schedule)
    _draw_common(reinf, assessment, story, plot)
    result.entities["reinforcement"] = _draw_reinforcement(reinf, assessment, story, plot)
    result.reinforcement_dxf = reinf.save(output / f"PLAN_FERRAILLAGE_{_safe(story)}.dxf",
                                          (0, 0, plot.sheet_w, plot.sheet_h))

    result.formwork_svg = output / f"PLAN_COFFRAGE_{_safe(story)}.svg"
    result.formwork_svg.write_text(_svg_plan(assessment, story, "formwork", "Plan de coffrage"),
                                   encoding="utf-8")
    result.reinforcement_svg = output / f"PLAN_FERRAILLAGE_{_safe(story)}.svg"
    result.reinforcement_svg.write_text(_svg_plan(assessment, story, "reinforcement", "Plan de ferraillage"),
                                        encoding="utf-8")
    result.schedule_csv = _write_schedule_csv(schedule, output / f"NOMENCLATURE_{_safe(assessment.joint_name)}.csv")
    return result


def _safe(value: str) -> str:
    return "".join(c if c.isalnum() or c in "-_" else "_" for c in value.strip()) or "JOINT"
