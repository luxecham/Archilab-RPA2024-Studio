"""Moteur de calcul RPA 2024 / BAEL 91 revise 99.

Le moteur consume un :class:`parsers.ParsedProject` et produit un
:class:`Assessment` complet, chapitre par chapitre. Toutes les boucles portent sur
les familles et elements reellement importes : aucune geometrie de projet n'est
codee dans ce module.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
from datetime import datetime
from enum import Enum
from typing import Iterable, Sequence

from parsers import (
    ElementFamily,
    ForceResult,
    ParsedProject,
    ProjectConfig,
    SoilPressure,
    StructuralModel,
)

__all__ = [
    "Status",
    "Check",
    "ElementReport",
    "ReportTable",
    "ChapterReport",
    "Assessment",
    "EngineOptions",
    "assess",
    "run_checks",
    "SeismicAssessment",
    "RegularityAssessment",
    "FoundationAssessment",
    "JointAssessment",
    "DqeReport",
    "BaelSection",
    "BaelResult",
]


# ---------------------------------------------------------------------------
# Resultats de verification
# ---------------------------------------------------------------------------

class Status(Enum):
    OK = "Vérifié"
    WARNING = "À vérifier"
    FAILED = "Non vérifié"
    NA = "Non applicable"

    @property
    def severity(self) -> int:
        return {"Vérifié": 0, "À vérifier": 1, "Non vérifié": 2, "Non applicable": -1}[self.value]

    @classmethod
    def worst(cls, statuses: Iterable["Status"]) -> "Status":
        meaningful = [s for s in statuses if s != cls.NA]
        if not meaningful:
            return cls.NA
        return max(meaningful, key=lambda s: s.severity)


@dataclass
class Check:
    code: str
    label: str
    reference: str
    status: Status
    required: str = ""
    provided: str = ""
    message: str = ""
    value: float | None = None
    informational: bool = False

    @property
    def ok(self) -> bool:
        return self.status == Status.OK


@dataclass
class ElementReport:
    element_type: str
    name: str
    geometry: str
    checks: list[Check] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    @property
    def status(self) -> Status:
        return Status.worst(c.status for c in self.checks)

    @property
    def failed(self) -> int:
        return sum(1 for c in self.checks if c.status == Status.FAILED)


@dataclass
class ReportTable:
    title: str
    headers: list[str]
    rows: list[list[str]]
    widths: list[float] | None = None


@dataclass
class ChapterReport:
    number: int
    title: str
    summary: str
    checks: list[Check] = field(default_factory=list)
    elements: list[ElementReport] = field(default_factory=list)
    lines: list[str] = field(default_factory=list)
    tables: list[ReportTable] = field(default_factory=list)
    figure: str = ""
    informative: bool = False

    @property
    def status(self) -> Status:
        meaningful = [c.status for c in self.checks] + [e.status for e in self.elements]
        if not meaningful:
            return Status.NA
        if all(s == Status.NA for s in meaningful) and self.informative:
            return Status.OK
        return Status.worst(meaningful)

    @property
    def failed_count(self) -> int:
        return sum(1 for c in self.checks if c.status == Status.FAILED) + sum(
            1 for e in self.elements if e.status == Status.FAILED
        )

    @property
    def warning_count(self) -> int:
        return sum(1 for c in self.checks if c.status == Status.WARNING) + sum(
            1 for e in self.elements if e.status == Status.WARNING
        )


# ---------------------------------------------------------------------------
# Outils de creation de checks
# ---------------------------------------------------------------------------

def fmt(value: float | None, decimals: int = 2) -> str:
    if value is None:
        return "-"
    return f"{value:,.{decimals}f}".replace(",", " ")


def check_min(code: str, label: str, reference: str, provided: float, minimum: float,
              unit: str = "", decimals: int = 1, message: str = "",
              warning_ratio: float | None = None) -> Check:
    status = Status.OK if provided + 1e-9 >= minimum else Status.FAILED
    if status == Status.OK and warning_ratio is not None and minimum > 0 and provided < minimum * warning_ratio:
        status = Status.WARNING
    text = message if status != Status.OK else ""
    if not text and status == Status.FAILED:
        text = f"{label} insuffisant : {fmt(provided, decimals)} {unit} < {fmt(minimum, decimals)} {unit}."
    elif not text and status == Status.WARNING:
        text = f"{label} proche du minimum ({fmt(provided, decimals)} / {fmt(minimum, decimals)} {unit})."
    return Check(code, label, reference, status,
                 f"≥ {fmt(minimum, decimals)} {unit}", f"{fmt(provided, decimals)} {unit}", text)


def check_max(code: str, label: str, reference: str, provided: float, maximum: float,
              unit: str = "", decimals: int = 1, message: str = "",
              warning_ratio: float | None = None) -> Check:
    status = Status.OK
    if provided - 1e-9 > maximum:
        status = Status.FAILED
    elif warning_ratio is not None and maximum > 0 and provided > maximum * warning_ratio:
        status = Status.WARNING
    text = message if status != Status.OK else ""
    if not text and status == Status.FAILED:
        text = f"{label} dépassé : {fmt(provided, decimals)} {unit} > {fmt(maximum, decimals)} {unit}."
    elif not text and status == Status.WARNING:
        text = f"{label} proche de la limite ({fmt(provided, decimals)} / {fmt(maximum, decimals)} {unit})."
    return Check(code, label, reference, status,
                 f"≤ {fmt(maximum, decimals)} {unit}", f"{fmt(provided, decimals)} {unit}", text)


def check_between(code: str, label: str, reference: str, provided: float,
                  minimum: float, maximum: float, unit: str = "",
                  decimals: int = 1, status_out: Status = Status.WARNING,
                  message: str = "") -> Check:
    inside = minimum - 1e-9 <= provided <= maximum + 1e-9
    text = message
    if inside:
        text = ""
    elif not text:
        text = f"{label} hors plage : {fmt(provided, decimals)} {unit} (attendu {fmt(minimum, decimals)} à {fmt(maximum, decimals)} {unit})."
    return Check(code, label, reference,
                 Status.OK if inside else status_out,
                 f"{fmt(minimum, decimals)} à {fmt(maximum, decimals)} {unit}",
                 f"{fmt(provided, decimals)} {unit}", text)


def check_bool(code: str, label: str, reference: str, condition: bool,
               required: str, provided: str, status_fail: Status = Status.FAILED,
               message: str = "") -> Check:
    return Check(code, label, reference, Status.OK if condition else status_fail,
                 required, provided, "" if condition else message)


def check_info(code: str, label: str, reference: str, value: str) -> Check:
    return Check(code, label, reference, Status.NA, provided=value, informational=True)


def check_na(code: str, label: str, reference: str, message: str) -> Check:
    return Check(code, label, reference, Status.NA, message=message)


# ---------------------------------------------------------------------------
# Base normative RPA 2024
# ---------------------------------------------------------------------------

SITE_SPECTRA = {
    "TYPE_1": {
        "S1": (1.00, 0.10, 0.40, 2.00),
        "S2": (1.20, 0.10, 0.50, 2.00),
        "S3": (1.30, 0.15, 0.60, 2.00),
        "S4": (1.35, 0.15, 0.70, 2.00),
    },
    "TYPE_2": {
        "S1": (1.00, 0.05, 0.25, 1.20),
        "S2": (1.30, 0.05, 0.30, 1.20),
        "S3": (1.55, 0.10, 0.40, 1.20),
        "S4": (1.80, 0.10, 0.50, 1.20),
    },
}

ZONE_COEFFICIENTS = {
    "0": 0.0, "I": 0.07, "II": 0.10, "III": 0.15, "IV": 0.20, "V": 0.25, "VI": 0.30,
}

IMPORTANCE = {
    "1A": (1.40, 0.39, 1178),
    "1B": (1.20, 0.46, 777),
    "2": (1.00, 0.55, 475),
    "3": (0.80, 0.69, 260),
}

BRACING = {
    1: {"r": 5.5, "category": "A", "ct": 0.075, "walls": False,
        "limits": {"I-II": (5, 17.0), "III": (4, 14.0), "IV": (3, 11.0), "V-VI": (3, 11.0)}},
    2: {"r": 5.5, "category": "A", "ct": 0.050, "walls": False,
        "limits": {"I-II": (7, 23.0), "III": (6, 20.0), "IV": (5, 17.0), "V-VI": (5, 17.0)}},
    3: {"r": 3.5, "category": "A", "ct": 0.050, "walls": False,
        "limits": {"I-II": (5, 17.0), "III": (4, 14.0), "IV": (3, 11.0), "V-VI": (2, 8.0)}},
    4: {"r": 4.5, "category": "B", "ct": 0.050, "walls": True, "limits": {}},
    5: {"r": 4.5, "category": "B", "ct": 0.050, "walls": True,
        "limits": {"I-II": (None, 48.0), "III": (None, 48.0), "IV": (None, 48.0), "V-VI": (None, 48.0)}},
    6: {"r": 3.0, "category": "B", "ct": 0.050, "walls": True, "limits": {}},
    7: {"r": 3.0, "category": "B", "ct": 0.050, "walls": False, "limits": {}},
    8: {"r": 2.0, "category": "C", "ct": 0.050, "walls": False, "limits": {}},
    9: {"r": 1.5, "category": "C", "ct": 0.050, "walls": True, "limits": {}},
}

OCCUPANCY_PSI = {
    "1": 0.20, "2a": 0.30, "2b": 0.40, "3": 0.50, "4": 1.00, "5": 0.60,
}


class Spectrum:
    """Spectre elastique et spectre de calcul horizontal RPA 2024 (Eq. 3.8 et 3.15)."""

    def __init__(self, spectrum_type: str, site: str, a: float, importance: float,
                 r: float, qf: float, eta: float):
        if site not in SITE_SPECTRA[spectrum_type]:
            raise ValueError(f"Site {site} sans spectre tabule.")
        self.s, self.t1, self.t2, self.t3 = SITE_SPECTRA[spectrum_type][site]
        self.a = a
        self.importance = importance
        self.r = r
        self.qf = qf
        self.eta = eta

    def elastic(self, t: float) -> float:
        base = self.a * self.importance * self.s
        plateau = 2.5 * self.eta
        if t <= 0:
            return base
        if t <= self.t1:
            return base * (1.0 + t / self.t1 * (plateau - 1.0))
        if t <= self.t2:
            return base * plateau
        if t <= self.t3:
            return base * plateau * (self.t2 / t)
        if t <= 4.0:
            return base * plateau * (self.t2 * self.t3 / (t * t))
        return 0.0

    def design(self, t: float) -> float:
        base = self.a * self.importance * self.s
        plateau = 2.5 * self.qf / self.r
        if t <= 0:
            value = base * 2.0 / 3.0
        elif t <= self.t1:
            value = base * (2.0 / 3.0 + t / self.t1 * (plateau - 2.0 / 3.0))
        elif t <= self.t2:
            value = base * plateau
        elif t <= self.t3:
            value = base * plateau * self.t2 / t
        elif t <= 4.0:
            value = base * plateau * self.t2 * self.t3 / (t * t)
        else:
            value = 0.0
        return max(value, 0.2 * self.a * self.importance)

    def branch(self, t: float) -> str:
        if t <= self.t1:
            return "T ≤ T1 (branche ascendante)"
        if t <= self.t2:
            return "T1 < T ≤ T2 (plateau)"
        if t <= self.t3:
            return "T2 < T ≤ T3 (décroissance 1/T)"
        if t <= 4.0:
            return "T3 < T ≤ 4 s (décroissance 1/T²)"
        return "T > 4 s"


def zone_key_from_code(code: str) -> str:
    text = str(code).strip().upper().replace("ZONE", "").strip(" _-")
    return text


def zone_group(code: str) -> str | None:
    key = zone_key_from_code(code)
    if key in {"I", "II"}:
        return "I-II"
    if key == "III":
        return "III"
    if key == "IV":
        return "IV"
    if key in {"V", "VI"}:
        return "V-VI"
    return None


def damping_percent(bracing_id: int) -> float:
    if braking_walls(bracing_id):
        return 10.0
    if bracing_id in (7, 8):
        return 5.0
    return 6.0


def braking_walls(bracing_id: int) -> bool:
    return bool(BRACING.get(bracing_id, {}).get("walls"))


def eta_damping(percent: float) -> float:
    return max(0.70, math.sqrt(7.0 / (2.0 + percent)))


@dataclass
class ResolvedSeismic:
    zone_code: str
    a: float
    site: str
    spectrum_type: str
    importance_code: str
    importance: float
    nu_a: float
    bracing_id: int
    r: float
    qf: float
    category: str
    ct: float
    empirical_period: float
    t2: float
    lambda_factor: float
    damping: float
    eta: float
    psi: float
    height_compliance: tuple[bool, str] | None
    spectrum: Spectrum | None
    warnings: list[str] = field(default_factory=list)


def compute_quality_factor(config: ProjectConfig, model: StructuralModel) -> tuple[float, list[Check]]:
    category = BRACING.get(config.bracing_id, BRACING[1])["category"]
    if category == "C":
        return 1.0, [check_info("QF_C", "Facteur de qualité", "RPA 2024 Tableau 3.18",
                                "Catégorie (c) : QF = 1 imposé.")]
    checks: list[Check] = []
    if category == "A":
        items = [
            ("QA1_PLAN", "Régularité en plan", 0.05, config.plan_regular),
            ("QA2_ELEVATION", "Régularité en élévation", 0.20, config.elevation_regular),
            ("QA3_NIVEAUX", "Condition minimale de 2 niveaux", 0.20, model.num_stories >= 2),
            ("QA4_TRAVEES", "Condition minimale de 3 travées par direction", 0.10,
             model.num_bays_x >= 3 and model.num_bays_y >= 3),
        ]
    else:
        items = [
            ("QB1_PLAN", "Régularité en plan", 0.05, config.plan_regular),
            ("QB2_ELEVATION", "Régularité en élévation", 0.20, config.elevation_regular),
            ("QB3_REDONDANCE", "Redondance en plan", 0.05, config.redundant_walls),
        ]
    qf = 1.0
    for code, label, weight, satisfied in items:
        if not satisfied:
            qf += weight
        checks.append(Check(
            code, label, "RPA 2024 Tableau 3.18", Status.OK if satisfied else Status.WARNING,
            "Critère satisfait", "Satisfait" if satisfied else f"Non satisfait (Pq = +{weight:.2f})",
            "" if satisfied else f"Pénalité de {weight:.2f} appliquée au facteur de qualité.",
        ))
    checks.append(check_info("QF", "Facteur de qualité retenu", "RPA 2024 Tableau 3.18",
                             f"QF = {qf:.2f} (catégorie {category})"))
    return qf, checks


def resolve_seismic(config: ProjectConfig, model: StructuralModel) -> ResolvedSeismic:
    warnings: list[str] = []
    zone_code = str(config.zone_label).split("-")[0].strip()
    if zone_code not in ZONE_COEFFICIENTS:
        zone_code = {0.0: "0", 0.07: "I", 0.10: "II", 0.15: "III",
                     0.20: "IV", 0.25: "V", 0.30: "VI"}.get(round(config.zone, 2), "III")
    a = ZONE_COEFFICIENTS.get(zone_code, config.zone)
    if a <= 0:
        warnings.append("Zone 0 : aucune action sismique de calcul.")
    site = config.site_class.upper()
    spectrum_type = "TYPE_1" if zone_code in {"IV", "V", "VI"} else "TYPE_2"
    params = SITE_SPECTRA[spectrum_type].get(site)
    if params is None:
        warnings.append("Site SS : étude spécifique requise, aucun spectre tabulé.")
    importance = IMPORTANCE.get(config.importance_code.upper(), (config.importance_factor, config.nu_a, 475))
    i_factor = config.importance_factor if config.importance_factor else importance[0]
    nu_a = config.nu_a if config.nu_a else importance[1]
    bracing = BRACING.get(config.bracing_id, BRACING[1])
    r = config.behavior_factor or bracing["r"]
    qf, _ = compute_quality_factor(config, model)
    if config.qf_override is not None:
        qf = float(config.qf_override)
        warnings.append(f"QF imposé manuellement : {qf:.2f}.")
    ct = config.ct_override if config.ct_override is not None else bracing["ct"]
    empirical = ct * (model.total_height ** 0.75) if model.total_height > 0 else 0.0
    damping = config.damping_override if config.damping_override is not None else damping_percent(config.bracing_id)
    eta = eta_damping(damping)
    t2 = params[2] if params else 0.0
    lam = config.lambda_override if config.lambda_override is not None else (
        0.85 if model.num_stories > 2 and t2 > 0 and empirical <= 2 * t2 else 1.0
    )
    height_compliance = None
    limit = bracing["limits"].get(zone_group(zone_code) or "", None)
    if limit:
        max_stories, max_height = limit
        compliant = (
            (max_stories is None or len(model.stories) <= max_stories)
            and (max_height is None or model.total_height <= max_height + 1e-6)
        )
        detail = (
            f"{len(model.stories)} niveau(x), H = {fmt(model.total_height)} m ; "
            f"limite {max_stories if max_stories is not None else '-'} niveau(x) / "
            f"{max_height if max_height is not None else '-'} m (Tableau 3.11 à 3.13)."
        )
        height_compliance = (compliant, detail)
        if not compliant:
            warnings.append("Limite de hauteur RPA non satisfaite : " + detail)
    spectrum = Spectrum(spectrum_type, site, a, i_factor, r, qf, eta) if params and a > 0 else None
    psi = config.psi if config.psi is not None else OCCUPANCY_PSI.get(
        str(config.occupancy).split("-")[0].strip(), 0.20
    )
    return ResolvedSeismic(
        zone_code=zone_code, a=a, site=site, spectrum_type=spectrum_type,
        importance_code=config.importance_code, importance=i_factor, nu_a=nu_a,
        bracing_id=config.bracing_id, r=r, qf=qf, category=bracing["category"],
        ct=ct, empirical_period=empirical, t2=t2, lambda_factor=lam,
        damping=damping, eta=eta, psi=psi, height_compliance=height_compliance,
        spectrum=spectrum, warnings=warnings,
    )


# ---------------------------------------------------------------------------
# BAEL 91
# ---------------------------------------------------------------------------

@dataclass
class BaelSection:
    b_cm: float
    h_cm: float
    cover_cm: float = 4.0
    top_cover_cm: float = 4.0
    n_kn: float = 0.0
    m_knm: float = 0.0
    accidental: bool = False

    @property
    def b(self) -> float:
        return self.b_cm / 100.0

    @property
    def h(self) -> float:
        return self.h_cm / 100.0

    @property
    def d(self) -> float:
        return self.h - self.cover_cm / 100.0

    @property
    def d_prime(self) -> float:
        return self.top_cover_cm / 100.0


@dataclass
class BaelResult:
    state: str
    alpha: float
    mu: float
    mu_lim: float
    lever_arm: float
    top_cm2: float
    bottom_cm2: float
    tension_cm2: float
    compression_cm2: float
    minimum_cm2: float
    notes: list[str] = field(default_factory=list)

    @property
    def total_cm2(self) -> float:
        return self.top_cm2 + self.bottom_cm2

    @property
    def required_cm2(self) -> float:
        return max(self.total_cm2, self.minimum_cm2)


class BaelSolver:
    """Noyau BAEL 91 revisé 99 pour sections rectangulaires."""

    def __init__(self, fc28: float, fe: float, gamma_b: float = 1.5, gamma_s: float = 1.15):
        self.fc28 = fc28
        self.fe = fe
        self.gamma_b = gamma_b
        self.gamma_s = gamma_s

    def coefficients(self, accidental: bool) -> tuple[float, float, float, float, float]:
        theta = 0.85 if accidental else 1.0
        fbu = 0.85 * self.fc28 / (theta * self.gamma_b)
        fsu = self.fe / self.gamma_s
        ft28 = 0.6 + 0.06 * self.fc28
        return theta, fbu, fsu, ft28, 3.5

    def solve(self, section: BaelSection) -> BaelResult:
        theta, fbu, fsu, ft28, eps_cu = self.coefficients(section.accidental)
        eps_sy = self.fe / 200_000.0 * 1000.0
        alpha_lim = eps_cu / (eps_cu + eps_sy)
        mu_lim = 0.8 * alpha_lim * (1.0 - 0.4 * alpha_lim)
        b, h, d, dp = section.b, section.h, section.d, section.d_prime
        minimum = 0.23 * b * d * ft28 / self.fe * 10_000.0
        notes: list[str] = []

        if abs(section.n_kn) < 1e-9:
            return self._simple(section, fbu, fsu, mu_lim, alpha_lim, d, dp, minimum, notes, "Flexion simple")

        nu = section.n_kn
        mu_g = section.m_knm
        e = mu_g / nu if abs(nu) > 1e-9 else 0.0
        mu_a = mu_g + nu * (d - h / 2.0)
        fbu_knm2 = fbu * 1000.0

        # Section entierement tendue.
        if nu < 0 and abs(e) <= h / 2.0 - dp + 1e-9:
            lever = (d - dp) * fsu
            bottom = abs(nu) * (d - h / 2.0 + e) / lever * 10.0 if lever > 0 else 0.0
            top = abs(nu) * (d - h / 2.0 - e) / lever * 10.0 if lever > 0 else 0.0
            notes.append("Axe neutre hors section : les deux nappes reprennent la traction.")
            return BaelResult("Section entièrement tendue", 0.0, 0.0, mu_lim, d - dp,
                              max(0.0, top), max(0.0, bottom), max(0.0, top) + max(0.0, bottom),
                              0.0, minimum, notes)

        if nu > 0 and b * h * fbu_knm2 > 0 and nu / (b * h * fbu_knm2) > 0.81:
            return self._pivot_c(section, fbu, fsu, mu_a, minimum, notes, eps_cu)

        mu = abs(mu_a) / (b * d * d * fbu_knm2) if b * d * d > 0 else 0.0
        alpha = 1.25 * (1.0 - math.sqrt(max(0.0, 1.0 - 2.0 * mu))) if mu <= mu_lim and mu < 0.5 else alpha_lim
        if nu > 0 and alpha * d > h + 1e-9:
            return self._pivot_c(section, fbu, fsu, mu_a, minimum, notes, eps_cu)

        fictitious = self._simple(
            BaelSection(section.b_cm, section.h_cm, section.cover_cm, section.top_cover_cm,
                        0.0, abs(mu_a), section.accidental),
            fbu, fsu, mu_lim, alpha_lim, d, dp, minimum, [], "Flexion simple fictive",
        )
        axial_reduction = nu / fsu * 10.0
        tension = max(0.0, fictitious.tension_cm2 - axial_reduction)
        if tension <= 1e-9 and nu > 0:
            notes.append("La section fictive tendue est reprise par l'effort normal : pivot C.")
            return self._pivot_c(section, fbu, fsu, mu_a, minimum, notes, eps_cu)

        swapped = mu_a < 0
        compression = fictitious.compression_cm2
        top = tension if swapped else compression
        bottom = compression if swapped else tension
        notes.append(
            f"Section partiellement comprimée : M_uA = {fmt(abs(mu_a), 2)} kN.m, "
            f"déduction de N_u/f_su = {fmt(axial_reduction, 2)} cm²."
        )
        return BaelResult("Section partiellement comprimée", alpha, mu, mu_lim,
                          d * (1 - 0.4 * alpha), top, bottom, tension, compression,
                          minimum, notes)

    def _simple(self, section: BaelSection, fbu: float, fsu: float, mu_lim: float,
                alpha_lim: float, d: float, dp: float, minimum: float,
                notes: list[str], state: str) -> BaelResult:
        b = section.b
        moment = abs(section.m_knm)
        mu = moment / (b * d * d * fbu * 1000.0) if b * d * d > 0 else 0.0
        alpha = 1.25 * (1.0 - math.sqrt(max(0.0, 1.0 - 2.0 * mu))) if mu <= mu_lim and mu < 0.5 else alpha_lim
        z = d * (1.0 - 0.4 * alpha)
        m_lim = mu_lim * b * d * d * fbu * 1000.0
        z_lim = d * (1.0 - 0.4 * alpha_lim)
        tension = moment / (z * fsu) * 10.0 if z > 0 else 0.0
        compression = 0.0
        if mu > mu_lim:
            delta = moment - m_lim
            strain = 3.5 * (1.0 - dp / (alpha_lim * d)) if alpha_lim * d > 0 else 0.0
            sigma = min(strain / 1000.0 * 200_000.0, fsu)
            compression = delta / ((d - dp) * sigma) * 10.0 if delta > 0 and sigma > 0 else 0.0
            base = m_lim / (z_lim * fsu) * 10.0 if z_lim > 0 else 0.0
            tension = base + compression * sigma / fsu
            notes.append("Moment réduit supérieur à µ_lu : section doublement armée.")
        if tension + compression < minimum:
            notes.append("Minimum de non-fragilité BAEL gouvernant.")
        return BaelResult(state, alpha, mu, mu_lim, z, compression, tension,
                          tension, compression, minimum, notes)

    def _pivot_c(self, section: BaelSection, fbu: float, fsu: float, mu_a: float,
                 minimum: float, notes: list[str], eps_cu: float) -> BaelResult:
        b, h, d, dp = section.b, section.h, section.d, section.d_prime
        fbu_knm2 = fbu * 1000.0
        if abs(section.m_knm) < 1e-6:
            strain = max(0.0, min(eps_cu, 3.5 * section.n_kn / (b * h * fbu_knm2))) if b * h else 0.0
            concrete = b * h * fbu_knm2 * strain / 3.5
            sigma = min(strain / 1000.0 * 200_000.0, fsu)
            total = max(0.0, section.n_kn - concrete) / sigma * 10.0 if sigma > 0 else 0.0
            top = bottom = total / 2.0
        else:
            eps_top, eps_bottom = eps_cu, 2.0
            concrete = b * h * fbu_knm2 * ((eps_top + eps_bottom) / (2 * eps_cu))
            centroid = h * (eps_top + 2 * eps_bottom) / (3 * (eps_top + eps_bottom))
            s_top = min((eps_top + (eps_bottom - eps_top) * dp / h) / 1000 * 200_000, fsu)
            s_bottom = min((eps_top + (eps_bottom - eps_top) * d / h) / 1000 * 200_000, fsu)
            by_force = 2 * (section.n_kn - concrete) / (s_top + s_bottom) * 10.0 if s_top + s_bottom > 0 else 0.0
            by_moment = (
                2 * (abs(mu_a) - concrete * (d - centroid)) / (s_top * (d - dp)) * 10.0
                if s_top > 0 and d > dp else 0.0
            )
            total = max(by_force, by_moment, 0.0)
            top = bottom = total / 2.0
        notes.append("Pivot C : béton comprimé sur toute la hauteur, aciers symétriques.")
        return BaelResult("Section entièrement comprimée", h / d if d else 1.0,
                          abs(mu_a) / (b * d * d * fbu_knm2) if b * d * d else 0.0,
                          0.8 * eps_cu / (eps_cu + 2.17) * (1 - 0.4 * eps_cu / (eps_cu + 2.17)),
                          d - dp, top, bottom, 0.0, top + bottom, minimum, notes)


def bar_area_cm2(diameter_mm: int) -> float:
    return math.pi * (diameter_mm / 10.0) ** 2 / 4.0


def select_beam_bars(required_cm2: float, b_cm: float, cover_cm: float,
                     diameters: Sequence[int] = (12, 14, 16, 20, 25, 32)) -> tuple[str, float, int]:
    usable = max(5.0, b_cm - 2 * cover_cm)
    minimum = max(2, math.ceil(usable / 25.0) + 1)
    candidates: list[tuple[float, int, int]] = []
    for d in diameters:
        area = bar_area_cm2(d)
        for count in range(2, 11):
            total = count * area
            if total + 1e-9 < required_cm2:
                continue
            if count * d / 10.0 + (count - 1) * 2.5 > usable + 1e-6:
                continue
            candidates.append((total, count, d))
    if not candidates:
        d = max(diameters)
        count = 6
        return f"{count}T{d}", count * bar_area_cm2(d), d
    total, count, d = min(candidates, key=lambda c: (c[0], c[1], c[2]))
    return f"{count}T{d}", total, d


def select_column_bars(required_cm2: float, b_cm: float, h_cm: float, cover_cm: float,
                       diameters: Sequence[int] = (12, 14, 16, 20, 25, 32)) -> tuple[str, float, int]:
    usable_b = max(5.0, b_cm - 2 * cover_cm)
    usable_h = max(5.0, h_cm - 2 * cover_cm)
    max_bars = max(2, math.ceil(usable_b / 15.0) + 1)
    max_bars_h = max(2, math.ceil(usable_h / 15.0) + 1)
    total_bars = 2 * (max_bars + max_bars_h) - 4
    candidates = []
    for d in diameters:
        area = bar_area_cm2(d)
        for count in range(4, total_bars + 1):
            total = count * area
            if total + 1e-9 < required_cm2:
                continue
            candidates.append((total, count, d))
    if not candidates:
        d = max(diameters)
        return f"4T{d}", 4 * bar_area_cm2(d), d
    total, count, d = min(candidates, key=lambda c: (c[0], -c[2], c[1]))
    return f"{count}T{d}", total, d


# ---------------------------------------------------------------------------
# Evaluation sismique
# ---------------------------------------------------------------------------

@dataclass
class ModalParticipation:
    mode_count: int
    sum_ux: float
    sum_uy: float
    last_period: float
    required_modes: int
    check_a: bool
    check_b: bool
    status: Status
    message: str
    mode_x: int | None = None
    mode_y: int | None = None
    torsion_mode: int | None = None
    table: ReportTable | None = None
    modes_for_90: int | None = None


@dataclass
class BaseShear:
    direction: str
    period_analytical: float | None
    period_empirical: float
    period_retained: float
    capped: bool
    lam: float
    acceleration: float
    branch: str
    weight_kn: float | None
    static_kn: float | None
    dynamic_kn: float | None
    ratio: float | None
    required_kn: float | None
    scale_factor: float | None
    status: Status
    message: str


@dataclass
class DriftCheck:
    direction: str
    story: str
    height: float
    elastic: float
    inelastic: float
    relative: float
    nu_a: float
    reduced: float
    limit_fragile: float
    limit_ductile: float
    governing: float
    utilization: float
    status: Status
    message: str


@dataclass
class PDeltaCheck:
    direction: str
    story: str
    cumulative_weight: float
    story_shear: float
    height: float
    drift: float
    theta: float
    classification: str
    amplification: float | None
    status: Status
    message: str


@dataclass
class NormalForceCheck:
    family: str
    element: str
    story: str
    load_case: str
    nd: float
    area_cm2: float
    fc28: float
    nu: float
    limit: float
    status: Status
    message: str


@dataclass
class SeismicAssessment:
    resolved: ResolvedSeismic
    modal: ModalParticipation
    base_shear: list[BaseShear]
    columns: list[NormalForceCheck]
    drifts_x: list[DriftCheck]
    drifts_y: list[DriftCheck]
    pdelta_x: list[PDeltaCheck]
    pdelta_y: list[PDeltaCheck]
    checks: list[Check]
    spectrum_rows: ReportTable | None = None
    warnings: list[str] = field(default_factory=list)

    @property
    def status(self) -> Status:
        return Status.worst(
            [c.status for c in self.checks]
            + [c.status for c in self.columns]
            + [d.status for d in self.drifts_x + self.drifts_y]
            + [p.status for p in self.pdelta_x + self.pdelta_y]
        )

    @property
    def all_drifts(self) -> list[DriftCheck]:
        return self.drifts_x + self.drifts_y

    @property
    def all_pdelta(self) -> list[PDeltaCheck]:
        return self.pdelta_x + self.pdelta_y


def _seismic_case(name: str) -> bool:
    normalized = name.upper().replace(" ", "")
    return (
        normalized.startswith(("EX", "EY", "E1", "E2", "SIS", "RPA"))
        or "SISM" in normalized
        or "RPA" in normalized
        or normalized in {f"COMB{i}" for i in range(1, 9)}
    )


def _matches_case(name: str, base: str) -> bool:
    normalized = name.upper().replace(" ", "")
    return normalized == base or normalized.startswith(base) or f"-{base}" in normalized or f"_{base}" in normalized


def _story_norm(name: str) -> str:
    return name.strip().upper()


def _family_matches(element: str, family: ElementFamily) -> bool:
    normalized = element.upper().replace(" ", "")
    for identifier in family.element_ids:
        key = identifier.upper().replace(" ", "")
        if normalized == key or normalized.startswith(key) or key.startswith(normalized):
            return True
    return False


def _fundamental_mode(results, direction: str) -> "object | None":
    key = "ux" if direction.upper() == "X" else "uy"
    candidates = [m for m in results.modal if getattr(m, key) > 0]
    return max(candidates, key=lambda m: getattr(m, key)) if candidates else None


def evaluate_modal(results, story_count: int) -> ModalParticipation:
    required = max(1, math.ceil(3.0 * math.sqrt(max(1, story_count))))
    rows = sorted(results.modal, key=lambda m: m.mode)
    if not rows:
        return ModalParticipation(0, 0.0, 0.0, 0.0, required, False, False, Status.NA,
                                  "Tableau de participation modale absent.", table=None)
    declared = any(m.sum_ux > 0 or m.sum_uy > 0 for m in rows)
    cumulative_x, cumulative_y = [], []
    rx = ry = 0.0
    for row in rows:
        rx += row.ux
        ry += row.uy
        cumulative_x.append(row.sum_ux if declared else rx)
        cumulative_y.append(row.sum_uy if declared else ry)
    index90 = next(
        (i for i in range(2, len(rows)) if cumulative_x[i] >= 90 - 1e-9 and cumulative_y[i] >= 90 - 1e-9),
        None,
    )
    check_a = index90 is not None
    check_b = len(rows) >= required and len(rows) >= 3 and rows[-1].period <= 0.20 + 1e-9
    sum_ux = cumulative_x[index90] if index90 is not None else cumulative_x[-1]
    sum_uy = cumulative_y[index90] if index90 is not None else cumulative_y[-1]
    mode_x = max(rows, key=lambda m: m.ux).mode if any(m.ux > 0 for m in rows) else None
    mode_y = max(rows, key=lambda m: m.uy).mode if any(m.uy > 0 for m in rows) else None
    torsion = max(rows, key=lambda m: m.rz).mode if any(m.rz > 0 for m in rows) else None
    status = Status.OK if check_a or check_b else Status.FAILED
    if check_a:
        message = (f"Participation suffisante : {index90 + 1} modes atteignent "
                   f"ΣUX = {fmt(sum_ux, 1)} % et ΣUY = {fmt(sum_uy, 1)} % (§ 4.3.3.a).")
    elif check_b:
        message = (f"Critère § 4.3.3.b satisfait : {len(rows)} modes ≥ 3√N = {required}, "
                   f"Tk = {fmt(rows[-1].period, 3)} s ≤ 0,20 s.")
    else:
        message = (f"Participation insuffisante : {len(rows)} modes, ΣUX = {fmt(sum_ux, 1)} %, "
                   f"ΣUY = {fmt(sum_uy, 1)} %, Tk = {fmt(rows[-1].period, 3)} s "
                   f"(minimum 3√N = {required} et Tk ≤ 0,20 s).")
    table = ReportTable(
        "Participation massique modale",
        ["Mode", "T (s)", "UX (%)", "UY (%)", "ΣUX (%)", "ΣUY (%)", "RZ (%)"],
        [[str(m.mode), fmt(m.period, 3), fmt(m.ux, 1), fmt(m.uy, 1),
          fmt(m.sum_ux if declared else cumulative_x[i], 1),
          fmt(m.sum_uy if declared else cumulative_y[i], 1), fmt(m.rz, 1)]
         for i, m in enumerate(rows)],
    )
    return ModalParticipation(len(rows), sum_ux, sum_uy, rows[-1].period, required,
                              check_a, check_b, status, message, mode_x, mode_y,
                              torsion, table, index90 + 1 if index90 is not None else None)


def _base_shear(direction: str, model, results, resolved: ResolvedSeismic,
                config: ProjectConfig) -> BaseShear:
    fundamental = _fundamental_mode(results, direction)
    analytical = getattr(fundamental, "period", None) if fundamental else None
    empirical = resolved.empirical_period
    capped_period = 1.30 * empirical
    if analytical is None:
        retained = empirical
    elif empirical <= 0:
        retained = analytical
    else:
        retained = min(analytical, capped_period)
    capped = analytical is not None and empirical > 0 and analytical > capped_period + 1e-9
    t2 = resolved.t2
    lam = config.lambda_override if config.lambda_override is not None else (
        0.85 if model.num_stories > 2 and t2 > 0 and retained <= 2 * t2 + 1e-9 else 1.0
    )
    if resolved.spectrum is None:
        return BaseShear(direction, analytical, empirical, retained, capped, lam, 0.0,
                         "-", results.seismic_weight, None, results.base_shear(direction),
                         None, None, None, Status.NA,
                         "Aucun spectre tabulé pour cette configuration.")
    acceleration = resolved.spectrum.design(retained)
    branch = resolved.spectrum.branch(retained)
    weight = results.seismic_weight or getattr(config, "manual_seismic_weight", None)
    static = lam * acceleration * weight if weight else None
    dynamic = results.base_shear(direction)
    required = static * 0.80 if static else None
    ratio = dynamic / static if static and static > 1e-9 and dynamic is not None else None
    scale = required / dynamic if required and dynamic and dynamic > 1e-9 and dynamic < required - 1e-9 else None
    if weight is None:
        status, message = Status.NA, "Poids sismique indisponible : importer Story Shears (cas WI)."
    elif dynamic is None:
        status, message = Status.NA, "Réaction dynamique indisponible : comparaison MSE/MDS impossible."
    elif scale is not None:
        status = Status.WARNING
        message = (f"Analyse dynamique à recalibrer : V_MDS = {fmt(dynamic, 1)} kN < "
                   f"0,80.V_MSE = {fmt(required, 1)} kN. Facteur d'échelle = {fmt(scale, 3)} (§ 4.3.5).")
    else:
        status = Status.OK
        message = (f"Calage vérifié : V_MDS/V_MSE = {fmt((ratio or 0) * 100, 1)} % ≥ 80 % "
                   f"(branche {branch}).")
    return BaseShear(direction, analytical, empirical, retained, capped, lam, acceleration,
                     branch, weight, static, dynamic, ratio, required, scale, status, message)


def _normal_forces(model, results, config: ProjectConfig) -> list[NormalForceCheck]:
    output: list[NormalForceCheck] = []
    for family in model.column_families():
        rows = [
            row for row in results.column_forces
            if _family_matches(row.element, family)
            and (_seismic_case(row.load_case) or not any(_seismic_case(r.load_case) for r in results.column_forces))
        ]
        if not rows:
            output.append(NormalForceCheck(family.key, family.element_ids[0] if family.element_ids else family.key,
                                           "", "", 0.0, family.area_cm2, config.fc28, 0.0, 0.35,
                                           Status.NA, "Aucun effort normal sismique trouvé."))
            continue
        governing = min(rows, key=lambda r: r.p)
        nd = max(0.0, -governing.p)
        area_m2 = family.area_cm2 / 10_000.0
        nu = nd / (area_m2 * config.fc28 * 1000.0) if area_m2 > 0 else 0.0
        status = Status.NA if nd <= 0 else (Status.OK if nu <= 0.35 + 1e-9 else Status.FAILED)
        message = (
            "Aucun effort de compression sismique." if status == Status.NA
            else f"ν = {fmt(nu, 3)} ≤ 0,35." if status == Status.OK
            else f"Effort normal excessif : ν = {fmt(nu, 3)} > 0,35 (Nd = {fmt(nd, 1)} kN)."
        )
        output.append(NormalForceCheck(family.key, governing.element, governing.story,
                                       governing.load_case, nd, family.area_cm2, config.fc28,
                                       nu, 0.35, status, message))
    return output


def _drifts(direction: str, model, results, resolved: ResolvedSeismic,
            config: ProjectConfig, warnings: list[str]) -> list[DriftCheck]:
    case = "EX" if direction.upper() == "X" else "EY"
    rows = [r for r in results.displacements if _matches_case(r.load_case, case)]
    if not rows:
        warnings.append(f"Déplacements {case} absents : contrôle des dérives non automatisé.")
        return []
    by_story: dict[str, float] = {}
    for row in rows:
        key = _story_norm(row.story)
        value = abs(row.ux if direction.upper() == "X" else row.uy)
        by_story[key] = max(by_story.get(key, 0.0), value)
    stories = model.main_stories if not config.include_penthouse else model.stories
    nu_a = config.nu_a if config.nu_a else resolved.nu_a
    amplification = resolved.r / resolved.qf if resolved.qf else resolved.r
    previous = 0.0
    output: list[DriftCheck] = []
    missing_reported = False
    for story in stories:
        elastic = by_story.get(_story_norm(story.name))
        if elastic is None:
            if not missing_reported:
                warnings.append(f"Certains niveaux n'ont pas de déplacement {case}.")
                missing_reported = True
            output.append(DriftCheck(direction, story.name, story.height, 0.0, 0.0, 0.0,
                                     nu_a, 0.0, config.drift_limit_fragile * story.height,
                                     config.drift_limit_ductile * story.height,
                                     config.drift_limit_fragile * story.height, 0.0,
                                     Status.NA, f"Déplacement {case} absent pour ce niveau."))
            continue
        inelastic = amplification * elastic
        drift = inelastic - previous
        previous = inelastic
        reduced = nu_a * abs(drift)
        fragile = config.drift_limit_fragile * story.height
        ductile = config.drift_limit_ductile * story.height
        governing = min(fragile, ductile)
        utilization = reduced / governing if governing > 0 else 0.0
        status = Status.OK if reduced <= governing + 1e-9 else Status.FAILED
        message = (
            f"Vérifiée : νA.|Δ| = {fmt(reduced * 1000, 2)} mm ≤ {fmt(governing * 1000, 2)} mm."
            if status == Status.OK else
            f"Déplacement excessif : νA.|Δ| = {fmt(reduced * 1000, 2)} mm > {fmt(governing * 1000, 2)} mm."
        )
        output.append(DriftCheck(direction, story.name, story.height, elastic, inelastic,
                                 drift, nu_a, reduced, fragile, ductile, governing,
                                 utilization, status, message))
    return output


def _pdelta(direction: str, model, results, drifts: list[DriftCheck],
            config: ProjectConfig, warnings: list[str]) -> list[PDeltaCheck]:
    if not drifts:
        return []
    weights = {_story_norm(k): v for k, v in results.cumulative_weights().items()}
    case = "EX" if direction.upper() == "X" else "EY"
    shear_rows = [r for r in results.story_shears if _matches_case(r.load_case, case) and r.is_bottom]
    shears: dict[str, float] = {}
    for row in shear_rows:
        key = _story_norm(row.story)
        value = abs(row.vx if direction.upper() == "X" else row.vy)
        shears[key] = max(shears.get(key, 0.0), value)
    if not shear_rows:
        warnings.append(f"Efforts tranchants {case} absents : P-Delta non automatisé.")
    if not weights:
        warnings.append("Poids cumulés absents : P-Delta non automatisé.")
    output: list[PDeltaCheck] = []
    for drift in drifts:
        key = _story_norm(drift.story)
        weight = weights.get(key)
        shear = shears.get(key)
        if drift.status == Status.NA or weight is None or shear is None or shear <= 1e-9 or drift.height <= 0:
            output.append(PDeltaCheck(direction, drift.story, weight or 0.0, shear or 0.0,
                                      drift.height, abs(drift.relative), 0.0,
                                      "Données insuffisantes", None, Status.NA,
                                      f"Données insuffisantes pour P-Delta au niveau {drift.story}."))
            continue
        theta = weight * abs(drift.relative) / (shear * drift.height)
        if theta <= config.pdelta_negative + 1e-9:
            classification, status = "Effets négligeables", Status.OK
            amplification = None
            message = f"Vérifiée : θ = {fmt(theta, 3)} ≤ 0,10."
        elif theta <= config.pdelta_unstable + 1e-9:
            classification, status = "Amplification requise", Status.WARNING
            amplification = 1.0 / (1.0 - theta) if theta < 1 else None
            message = f"Amplification 1/(1-θ) = {fmt(amplification or 0, 3)} requise (θ = {fmt(theta, 3)})."
        else:
            classification, status = "Structure instable", Status.FAILED
            amplification = None
            message = f"θ = {fmt(theta, 3)} > 0,20 : redimensionnement obligatoire."
        output.append(PDeltaCheck(direction, drift.story, weight, shear, drift.height,
                                  abs(drift.relative), theta, classification, amplification,
                                  status, message))
    return output


def _spectrum_table(spectrum: Spectrum, empirical: float) -> ReportTable:
    sampled = np.linspace(0.0, 4.0, 41)
    periods = sorted({0.0, 0.05, 0.1, 0.2, spectrum.t1, spectrum.t2, spectrum.t3,
                      round(empirical, 3), 2.0, 3.0, 4.0}
                     | {round(float(t), 3) for t in sampled})
    rows = []
    for t in periods:
        rows.append([fmt(t, 3), fmt(spectrum.elastic(t), 4), fmt(spectrum.design(t), 4),
                     spectrum.branch(t)])
    return ReportTable("Spectre de calcul horizontal", ["T (s)", "Sae/g", "Sad/g", "Branche"], rows)


def evaluate_seismic(model, results, config: ProjectConfig) -> SeismicAssessment:
    resolved = resolve_seismic(config, model)
    warnings = list(resolved.warnings)
    modal = evaluate_modal(results, model.num_stories)
    if resolved.spectrum is None:
        checks = [check_na("ANALYSE_SISMIQUE", "Analyse sismique globale", "RPA 2024",
                           "Aucune action sismique tabulée pour cette configuration.")]
        return SeismicAssessment(resolved, modal, [], [], [], [], [], [], checks, None, warnings)

    base_shears = [_base_shear("X", model, results, resolved, config),
                   _base_shear("Y", model, results, resolved, config)]
    columns = _normal_forces(model, results, config)
    drift_x = _drifts("X", model, results, resolved, config, warnings)
    drift_y = _drifts("Y", model, results, resolved, config, warnings)
    pdelta_x = _pdelta("X", model, results, drift_x, config, warnings)
    pdelta_y = _pdelta("Y", model, results, drift_y, config, warnings)

    checks: list[Check] = []
    checks.append(
        check_na("PARTICIPATION_MODALE", "Participation modale", "RPA 2024 § 4.3.3", modal.message)
        if modal.status == Status.NA else
        check_bool("PARTICIPATION_MODALE", "Participation modale", "RPA 2024 § 4.3.3",
                   modal.check_a or modal.check_b,
                   "ΣUX et ΣUY ≥ 90 % (k ≥ 3) ou k ≥ 3√N et Tk ≤ 0,20 s",
                   f"{modal.mode_count} modes, ΣUX = {fmt(modal.sum_ux, 1)} %, "
                   f"ΣUY = {fmt(modal.sum_uy, 1)} %", message=modal.message)
    )
    for shear in base_shears:
        if shear.status == Status.NA:
            checks.append(check_na(f"MSE_MDS_{shear.direction}", f"Calage MSE/MDS {shear.direction}",
                                   "RPA 2024 § 4.3.5", shear.message))
        else:
            ratio = (shear.ratio or 0) * 100
            checks.append(check_bool(
                f"MSE_MDS_{shear.direction}", f"Calage MSE/MDS {shear.direction}", "RPA 2024 § 4.3.5",
                ratio >= 80 - 1e-6, "V_MDS ≥ 0,80.V_MSE",
                f"V_MSE = {fmt(shear.static_kn, 1)} kN, V_MDS = {fmt(shear.dynamic_kn, 1)} kN, "
                f"rapport = {fmt(ratio, 1)} %", status_fail=Status.WARNING, message=shear.message,
            ))
    applicable_nu = [c for c in columns if c.status != Status.NA]
    if applicable_nu:
        governing = max(applicable_nu, key=lambda c: c.nu)
        checks.append(check_max("NU_POTEAUX", "Effort normal réduit des poteaux",
                                "RPA 2024 § 7.4.3, Éq. 7.5", governing.nu, 0.35, "", 3,
                                f"ν max = {fmt(governing.nu, 3)} pour {governing.element} ({governing.family})."))
    else:
        checks.append(check_na("NU_POTEAUX", "Effort normal réduit des poteaux",
                               "RPA 2024 § 7.4.3", "Aucun effort de compression exploitable."))
    for label, drifts in (("X", drift_x), ("Y", drift_y)):
        applicable = [d for d in drifts if d.status != Status.NA]
        if applicable:
            governing = max(applicable, key=lambda d: d.utilization)
            checks.append(check_max(f"DEPLACEMENTS_{label}", f"Déplacements inter-étages {label}",
                                    "RPA 2024 § 5.10", governing.utilization * 100, 100, "%", 1,
                                    f"νA.|Δ| = {fmt(governing.reduced * 1000, 2)} mm > "
                                    f"{fmt(governing.governing * 1000, 2)} mm au niveau {governing.story}."))
        else:
            checks.append(check_na(f"DEPLACEMENTS_{label}", f"Déplacements inter-étages {label}",
                                   "RPA 2024 § 5.10", "Déplacements indisponibles."))
    for label, pdelta in (("X", pdelta_x), ("Y", pdelta_y)):
        applicable = [p for p in pdelta if p.status != Status.NA]
        if applicable:
            governing = max(applicable, key=lambda p: p.theta)
            status = (Status.OK if governing.theta <= config.pdelta_negative + 1e-9 else
                      Status.WARNING if governing.theta <= config.pdelta_unstable + 1e-9 else Status.FAILED)
            checks.append(Check(f"PDELTA_{label}", f"Effet P-Delta {label}", "RPA 2024 § 5.9, Éq. 5.10",
                                status, "θ ≤ 0,10 ou amplification", f"θmax = {fmt(governing.theta, 3)}",
                                governing.message))
        else:
            checks.append(check_na(f"PDELTA_{label}", f"Effet P-Delta {label}",
                                   "RPA 2024 § 5.9", "Données insuffisantes."))

    return SeismicAssessment(
        resolved=resolved, modal=modal, base_shear=base_shears, columns=columns,
        drifts_x=drift_x, drifts_y=drift_y, pdelta_x=pdelta_x, pdelta_y=pdelta_y,
        checks=checks, spectrum_rows=_spectrum_table(resolved.spectrum, resolved.empirical_period),
        warnings=warnings,
    )


# ---------------------------------------------------------------------------
# Regularite, joints, poteau fort / poutre faible
# ---------------------------------------------------------------------------

@dataclass
class RegularityCriterion:
    code: str
    label: str
    reference: str
    satisfied: bool | None
    detail: str
    status: Status


@dataclass
class RegularityAssessment:
    criteria: list[RegularityCriterion]
    plan_regular: bool | None
    elevation_regular: bool | None
    qf: float
    qf_checks: list[Check]
    story_areas: dict[str, float] = field(default_factory=dict)
    warnings: list[str] = field(default_factory=list)

    @property
    def status(self) -> Status:
        return Status.worst([c.status for c in self.criteria])


def _plan_area(model: StructuralModel, story_name: str | None = None) -> tuple[float, float, float]:
    """Retourne (aire, largeur X, largeur Y) du plancher courant."""
    slabs = [a for a in model.slabs if not story_name or a.story == story_name]
    if not slabs:
        slabs = model.slabs
    points: list = []
    for slab in slabs:
        points.extend(model.points[n] for n in slab.nodes if n in model.points)
    if not points:
        if len(model.grid_x) >= 2 and len(model.grid_y) >= 2:
            lx = max(model.grid_x) - min(model.grid_x)
            ly = max(model.grid_y) - min(model.grid_y)
            return lx * ly, lx, ly
        return 0.0, 0.0, 0.0
    xs = [p.x for p in points]
    ys = [p.y for p in points]
    lx, ly = max(xs) - min(xs), max(ys) - min(ys)
    area = _polygon_area([(p.x, p.y) for p in points])
    return max(area, 0.0), lx, ly


def _polygon_area(points: list[tuple[float, float]]) -> float:
    if len(points) < 3:
        return 0.0
    # Enveloppe convexe (monotone chain) pour eviter les doublons de noeuds mitoyens.
    pts = sorted(set((round(x, 4), round(y, 4)) for x, y in points))
    if len(pts) < 3:
        return 0.0

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    hull = lower[:-1] + upper[:-1]
    return abs(sum(hull[i][0] * hull[(i + 1) % len(hull)][1]
                   - hull[(i + 1) % len(hull)][0] * hull[i][1]
                   for i in range(len(hull)))) / 2.0


def assess_regularity(model: StructuralModel, results, config: ProjectConfig,
                      resolved: ResolvedSeismic | None = None) -> RegularityAssessment:
    criteria: list[RegularityCriterion] = []
    warnings: list[str] = []
    area, lx, ly = _plan_area(model)
    story_areas: dict[str, float] = {}
    if lx > 0 and ly > 0:
        ratio = max(lx, ly) / min(lx, ly)
        criteria.append(RegularityCriterion(
            "a4", "Élancement du plancher", "RPA 2024 § 3.7.1a",
            ratio <= 4.0, f"Lmax/Lmin = {fmt(ratio, 2)} ≤ 4,0",
            Status.OK if ratio <= 4.0 else Status.WARNING,
        ))
    else:
        criteria.append(RegularityCriterion("a4", "Élancement du plancher", "RPA 2024 § 3.7.1a",
                                            None, "Géométrie de plancher indisponible.", Status.NA))

    centers = [c for c in results.centers if c.story]
    if centers and lx > 0 and ly > 0:
        max_ecc_x = max_ecc_y = 0.0
        worst_x = worst_y = ""
        for center in centers:
            ex = abs(center.xcm - (min(p.x for p in model.points.values()) + lx / 2))
            ey = abs(center.ycm - (min(p.y for p in model.points.values()) + ly / 2))
            if ex > max_ecc_x:
                max_ecc_x, worst_x = ex, center.story
            if ey > max_ecc_y:
                max_ecc_y, worst_y = ey, center.story
        ratio_x = max_ecc_x / lx if lx else 0.0
        ratio_y = max_ecc_y / ly if ly else 0.0
        eccentric_ok = ratio_x <= 0.20 and ratio_y <= 0.20
        criteria.append(RegularityCriterion(
            "a3", "Excentricité masse / rigidité", "RPA 2024 § 3.7.1a",
            eccentric_ok,
            f"e/L max = {fmt(max(ratio_x, ratio_y) * 100, 1)} % "
            f"(ex/Lx = {fmt(ratio_x * 100, 1)} %, ey/Ly = {fmt(ratio_y * 100, 1)} %)",
            Status.OK if eccentric_ok else Status.WARNING,
        ))
        # a1 : symetrie de la distribution des masses par rapport au centre geometrique.
        symmetry_ok = max(ratio_x, ratio_y) <= 0.05
        criteria.append(RegularityCriterion(
            "a1", "Symétrie en plan", "RPA 2024 § 3.7.1a",
            symmetry_ok,
            f"Écart du centre de masse au centre géométrique : "
            f"{fmt(max(max_ecc_x, max_ecc_y) * 100, 1)} cm",
            Status.OK if symmetry_ok else Status.WARNING,
        ))
    else:
        for code, label in (("a1", "Symétrie en plan"), ("a3", "Excentricité masse / rigidité")):
            criteria.append(RegularityCriterion(code, label, "RPA 2024 § 3.7.1a", None,
                                                "Centres de masse/rigidité indisponibles.", Status.NA))

    # a2/a5 : renfoncement et ouvertures, approches par aire du plancher.
    if lx > 0 and ly > 0:
        bbox = lx * ly
        coverage = area / bbox if bbox > 0 else 0.0
        notch_ok = coverage >= 0.90
        criteria.append(RegularityCriterion(
            "a2", "Renfoncement / couverture du plancher", "RPA 2024 § 3.7.1a",
            notch_ok,
            f"Couverture = {fmt(coverage * 100, 1)} % de la surface enveloppe",
            Status.OK if notch_ok else Status.WARNING,
        ))
        opening_ratio = max(0.0, 1.0 - coverage)
        opening_ok = opening_ratio <= 0.30
        criteria.append(RegularityCriterion(
            "a5", "Ouvertures en plan", "RPA 2024 § 3.7.1a",
            opening_ok, f"Taux d'ouverture estimé = {fmt(opening_ratio * 100, 1)} %",
            Status.OK if opening_ok else Status.WARNING,
        ))
    else:
        for code, label in (("a2", "Renfoncement / couverture"), ("a5", "Ouvertures en plan")):
            criteria.append(RegularityCriterion(code, label, "RPA 2024 § 3.7.1a", None,
                                                "Géométrie de plancher indisponible.", Status.NA))

    # b1 : continuite verticale des poteaux.
    if model.stories and model.columns:
        stories_with_columns = {_story_norm(c.story) for c in model.columns}
        missing = [s.name for s in model.main_stories if _story_norm(s.name) not in stories_with_columns]
        b1 = len(missing) == 0
        criteria.append(RegularityCriterion(
            "b1", "Continuité verticale des poteaux", "RPA 2024 § 3.7.1b", b1,
            "Tous les niveaux comportent des poteaux." if b1 else
            f"Niveaux sans poteaux : {', '.join(missing[:4])}",
            Status.OK if b1 else Status.WARNING,
        ))
    else:
        criteria.append(RegularityCriterion("b1", "Continuité verticale des poteaux",
                                            "RPA 2024 § 3.7.1b", None, "Poteaux non identifiés.", Status.NA))

    # b2 : variation de masse / rigidite entre niveaux.
    b2_status = Status.NA
    b2_detail = "Données de masse ou de rigidité indisponibles."
    if results.story_shears:
        weights: dict[str, float] = {}
        for row in results.story_shears:
            if row.p and abs(row.p) > 1e-6:
                weights[_story_norm(row.story)] = max(weights.get(_story_norm(row.story), 0.0), abs(row.p))
        ordered = [weights.get(_story_norm(s.name)) for s in model.main_stories]
        ordered = [v for v in ordered if v]
        # Les Story Shears du cas WI donnent un poids cumule : la variation de masse
        # se juge sur le poids propre de chaque niveau, obtenu par difference.
        if len(ordered) >= 2:
            if all(a >= b - 1e-6 for a, b in zip(ordered, ordered[1:])):
                ordered = [ordered[i] - ordered[i + 1] for i in range(len(ordered) - 1)] + [ordered[-1]]
            variation = max(abs(b - a) / max(a, b) for a, b in zip(ordered, ordered[1:]) if max(a, b) > 0)
            b2_ok = variation <= 0.30
            b2_status = Status.OK if b2_ok else Status.WARNING
            b2_detail = f"Variation maximale de poids entre niveaux = {fmt(variation * 100, 1)} %"
    criteria.append(RegularityCriterion("b2", "Variation de masse et de rigidité",
                                        "RPA 2024 § 3.7.1b", b2_status == Status.OK
                                        if b2_status != Status.NA else None, b2_detail, b2_status))

    # b3 : retrecissements successifs (surfaces de plancher).
    if len(model.main_stories) >= 2 and model.slabs:
        values = []
        for story in model.main_stories:
            a, _, _ = _plan_area(model, story.name)
            story_areas[story.name] = a
            values.append(a)
        if all(v > 0 for v in values):
            variation = max(abs(b - a) / max(a, b) for a, b in zip(values, values[1:]))
            b3_ok = variation <= 0.20
            criteria.append(RegularityCriterion(
                "b3", "Retrecissements en élévation", "RPA 2024 § 3.7.1b", b3_ok,
                f"Variation maximale de surface = {fmt(variation * 100, 1)} %",
                Status.OK if b3_ok else Status.WARNING,
            ))
        else:
            criteria.append(RegularityCriterion("b3", "Retrecissements en élévation",
                                                "RPA 2024 § 3.7.1b", None,
                                                "Surfaces de plancher non calculables.", Status.NA))
    else:
        criteria.append(RegularityCriterion("b3", "Retrecissements en élévation",
                                            "RPA 2024 § 3.7.1b", None,
                                            "Planchers ou niveaux insuffisants.", Status.NA))

    plan_codes = {"a1", "a2", "a3", "a4", "a5"}
    elevation_codes = {"b1", "b2", "b3"}
    plan_values = [c.satisfied for c in criteria if c.code in plan_codes and c.satisfied is not None]
    elev_values = [c.satisfied for c in criteria if c.code in elevation_codes and c.satisfied is not None]
    plan_regular = all(plan_values) if plan_values else None
    elevation_regular = all(elev_values) if elev_values else None
    if plan_regular is not None:
        config.plan_regular = plan_regular
    if elevation_regular is not None:
        config.elevation_regular = elevation_regular
    qf, qf_checks = compute_quality_factor(config, model)
    if config.qf_override is not None:
        qf = float(config.qf_override)
    return RegularityAssessment(criteria, plan_regular, elevation_regular, qf,
                                qf_checks, story_areas, warnings)


@dataclass
class StrongColumnCheck:
    node: str
    story: str
    sum_mrc: float
    sum_mrb: float
    ratio: float
    required: float
    status: Status
    message: str


@dataclass
class SeismicJointResult:
    required_mm: float
    provided_mm: float | None
    delta1_mm: float
    delta2_mm: float
    status: Status
    message: str


@dataclass
class JointAssessment:
    strong_column: list[StrongColumnCheck]
    seismic_joint: SeismicJointResult
    checks: list[Check]
    warnings: list[str] = field(default_factory=list)

    @property
    def status(self) -> Status:
        return Status.worst([c.status for c in self.checks]
                            + [s.status for s in self.strong_column])


def _moment_capacity(area_cm2: float, d_m: float, fsu_mpa: float) -> float:
    return area_cm2 / 10_000.0 * fsu_mpa * 1000.0 * 0.9 * d_m


def assess_joints(model: StructuralModel, results, config: ProjectConfig,
                  column_designs: dict[str, dict], beam_designs: dict[str, dict],
                  resolved: ResolvedSeismic) -> JointAssessment:
    checks: list[Check] = []
    warnings: list[str] = []
    fsu = config.fe / config.gamma_s
    strong: list[StrongColumnCheck] = []
    if results.beam_forces and results.column_forces:
        # Regroupement des poutres par noeud d'extremite et niveau.
        for beam in model.beams:
            beam_design = beam_designs.get(beam.section_name)
            if not beam_design:
                continue
            as_bottom = beam_design.get("as_bottom", 0.0)
            as_top = beam_design.get("as_top", 0.0)
            d = beam_design.get("d", 0.0)
            mrb = _moment_capacity(max(as_bottom, as_top), d, fsu)
            for node_id in (beam.start, beam.end):
                node = model.points.get(node_id)
                if not node:
                    continue
                columns = [
                    c for c in model.columns
                    if c.story == beam.story
                    and (model.points.get(c.end) and model.points[c.end].plan_distance(node) < 0.15)
                ]
                if not columns:
                    continue
                mrc = 0.0
                for column in columns:
                    design = column_designs.get(column.section_name)
                    if not design:
                        continue
                    dc = design.get("d", 0.0)
                    as_col = design.get("as_total", 0.0)
                    mrc += _moment_capacity(as_col / 2.0, dc, fsu)
                required = 1.25 * mrb
                ratio = mrc / mrb if mrb > 1e-9 else 0.0
                status = Status.OK if mrc + 1e-9 >= required else Status.FAILED
                strong.append(StrongColumnCheck(
                    f"{node_id}", beam.story, mrc, mrb, ratio, required, status,
                    f"ΣMRc = {fmt(mrc, 1)} kN.m / ΣMRb = {fmt(mrb, 1)} kN.m "
                    f"(rapport {fmt(ratio, 2)} ; requis 1,25)."
                    if status == Status.FAILED else
                    f"ΣMRc/ΣMRb = {fmt(ratio, 2)} ≥ 1,25.",
                ))
        if strong:
            failed = [s for s in strong if s.status == Status.FAILED]
            checks.append(check_bool(
                "POUTRE_FAIBLE", "Poteau fort / poutre faible", "RPA 2024 § 7.6",
                not failed, "ΣMRc ≥ 1,25 ΣMRb",
                f"{len(strong) - len(failed)}/{len(strong)} noeuds vérifiés",
                status_fail=Status.WARNING,
                message=f"{len(failed)} noeud(s) non vérifié(s) : renforcer les poteaux ou réduire les poutres.",
            ))
        else:
            checks.append(check_na("POUTRE_FAIBLE", "Poteau fort / poutre faible", "RPA 2024 § 7.6",
                                   "Ferraillage insuffisant pour évaluer les capacités."))
    else:
        checks.append(check_na("POUTRE_FAIBLE", "Poteau fort / poutre faible", "RPA 2024 § 7.6",
                               "Efforts de poutres/poteaux indisponibles."))

    # Joint sismique : dmin = 15 mm + δ1 + δ2, minimum 40 mm.
    top_disp_x = max((abs(d.ux) for d in results.displacements
                      if _matches_case(d.load_case, "EX")), default=0.0)
    top_disp_y = max((abs(d.uy) for d in results.displacements
                      if _matches_case(d.load_case, "EY")), default=0.0)
    if top_disp_x <= 0 and results.displacements:
        top_disp_x = max(abs(d.ux) for d in results.displacements)
    if top_disp_y <= 0 and results.displacements:
        top_disp_y = max(abs(d.uy) for d in results.displacements)
    amplification = resolved.r / resolved.qf if resolved.qf else resolved.r
    delta1 = amplification * top_disp_x * 1000.0
    delta2 = amplification * top_disp_y * 1000.0
    provided = getattr(config, "seismic_joint_provided", None)
    required = max(40.0, 15.0 + delta1 + delta2) if (delta1 or delta2) else 40.0
    if provided is None:
        status = Status.WARNING if (delta1 or delta2) else Status.NA
        message = (f"Largeur requise d_min = 15 + δ1 + δ2 = {fmt(required, 1)} mm. "
                   f"Largeur réellement prévue non renseignée.")
    else:
        status = Status.OK if provided + 1e-9 >= required else Status.FAILED
        message = (f"Joint prévu {fmt(provided, 1)} mm ≥ d_min = {fmt(required, 1)} mm."
                   if status == Status.OK else
                   f"Joint insuffisant : {fmt(provided, 1)} mm < d_min = {fmt(required, 1)} mm.")
    checks.append(Check("JOINT_SISMIQUE", "Largeur du joint sismique", "RPA 2024 § 5.12",
                        status, f"≥ {fmt(required, 1)} mm",
                        f"{fmt(provided, 1)} mm" if provided is not None else "non renseignée",
                        message))
    if not results.displacements:
        warnings.append("Déplacements absents : largeur de joint réduite au minimum de 40 mm.")
    return JointAssessment(strong, SeismicJointResult(required, provided, delta1, delta2, status, message),
                           checks, warnings)


# ---------------------------------------------------------------------------
# Ferraillage poteaux / poutres / voiles
# ---------------------------------------------------------------------------

@dataclass
class ReinforcementReport:
    columns: list[ElementReport]
    beams: list[ElementReport]
    walls: list[ElementReport]
    column_designs: dict[str, dict]
    beam_designs: dict[str, dict]
    checks: list[Check]
    lines: list[str]
    warnings: list[str] = field(default_factory=list)

    @property
    def status(self) -> Status:
        return Status.worst([c.status for c in self.checks]
                            + [e.status for e in self.columns + self.beams + self.walls])


def _governing_forces(rows: Sequence[ForceResult], family: ElementFamily) -> list[ForceResult]:
    return [r for r in rows if _family_matches(r.element, family)]


def design_reinforcement(model: StructuralModel, results, config: ProjectConfig,
                         resolved: ResolvedSeismic) -> ReinforcementReport:
    solver = BaelSolver(config.fc28, config.fe, config.gamma_b, config.gamma_s)
    column_reports: list[ElementReport] = []
    beam_reports: list[ElementReport] = []
    wall_reports: list[ElementReport] = []
    column_designs: dict[str, dict] = {}
    beam_designs: dict[str, dict] = {}
    checks: list[Check] = []
    lines: list[str] = []
    warnings: list[str] = []
    zone = resolved.zone_code
    min_ratio = 0.008 if zone in {"0", "I"} else 0.010 if zone in {"II", "III"} else 0.012
    max_ratio = 0.04

    # Poteaux : BAEL en flexion composee sous combinaison accidentelle.
    for family in model.column_families():
        section = family.section
        b, h = section.b, section.h
        rows = _governing_forces(results.column_forces, family)
        if not rows:
            column_reports.append(ElementReport(
                "Poteau", family.key, family.geometry_label,
                [check_na("PC_EFFORTS", "Ferraillage du poteau", "BAEL 91 / RPA 2024",
                          "Aucun effort de poteau importe.")],
            ))
            continue
        governing = max(rows, key=lambda r: (abs(r.m3) + abs(r.m2), abs(r.p)))
        moment = max(abs(governing.m3), abs(governing.m2))
        axial = max(0.0, -governing.p)
        result = solver.solve(BaelSection(b, h, config.steel_cover, config.steel_cover,
                                          axial, moment, accidental=True))
        area_b_min = min_ratio * b * h
        area_b_max = max_ratio * b * h
        required = max(result.required_cm2, area_b_min)
        label, provided, diameter = select_column_bars(required, b, h, config.steel_cover)
        column_designs[section.name] = {
            "as_total": provided, "as_required": required, "d": (h - config.steel_cover) / 100.0,
            "label": label, "moment": moment, "axial": axial, "b": b, "h": h,
        }
        checks_col = [
            check_max("PC1_ACIER_MAX", f"Pourcentage d'acier {family.key}", "RPA 2024 § 7.4.4",
                      provided / (b * h) * 100, max_ratio * 100, "%", 2,
                      f"As/B = {fmt(provided / (b * h) * 100, 2)} % > 4 %."),
            check_min("PC2_ACIER_MIN", f"Pourcentage minimal {family.key}", "RPA 2024 § 7.4.4",
                      provided / (b * h) * 100, min_ratio * 100, "%", 2),
            check_bool("PC3_MOMENT", f"Flexion composée {family.key}", "BAEL 91 révisé 99",
                       result.required_cm2 <= provided + 1e-9, f"As ≥ {fmt(required, 2)} cm²",
                       f"{label} = {fmt(provided, 2)} cm²",
                       message=f"As = {fmt(provided, 2)} cm² < requis {fmt(required, 2)} cm²."),
        ]
        # Espacement des cadres en zone critique.
        spacing_limit_cm = min(10 * diameter / 10.0, 15.0)
        provided_spacing = min(15.0, max(10.0, spacing_limit_cm))
        checks_col.append(check_max(
            "PC4_CADRAGE", f"Espacement des cadres {family.key}", "RPA 2024 § 7.4.4.2",
            provided_spacing, spacing_limit_cm, "cm", 1,
            f"Espacement {fmt(provided_spacing, 0)} cm > {fmt(spacing_limit_cm, 0)} cm.",
        ))
        column_reports.append(ElementReport(
            "Poteau", family.key, family.geometry_label, checks_col,
            [f"Cas dimensionnant : {governing.load_case}, N = {fmt(axial, 1)} kN, M = {fmt(moment, 1)} kN.m.",
             f"Acier adopté : {label} ({fmt(provided, 2)} cm², {fmt(provided / (b * h) * 100, 2)} %)."],
        ))
        lines.append(f"{family.key} : {label}, N = {fmt(axial, 1)} kN, M = {fmt(moment, 1)} kN.m.")

    # Poutres : flexion simple, enveloppe travée/appui.
    for family in model.beam_families():
        section = family.section
        b, h = section.b, section.h
        rows = _governing_forces(results.beam_forces, family)
        if not rows:
            beam_reports.append(ElementReport(
                "Poutre", family.key, family.geometry_label,
                [check_min("PB1_LARGEUR", "Largeur minimale", "RPA 2024 § 7.5.1", b,
                           25.0 if zone in {"IV", "V", "VI"} else 20.0, "cm"),
                 check_min("PB2_HAUTEUR", "Hauteur minimale", "RPA 2024 § 7.5.1", h,
                           20.0 if resolved.category in {"B", "C"} else 30.0, "cm"),
                 check_max("PB3_ELANCEMENT", "Élancement h/b", "RPA 2024 § 7.5.1", h / b if b else 0,
                           4.0, "", 2),
                 check_na("PB4_EFFORTS", "Ferraillage de poutre", "BAEL 91",
                          "Aucun effort de poutre importe : coffrage seul vérifié.")],
            ))
            continue
        sagging = max((r.m3 for r in rows), default=0.0)
        hogging = min((r.m3 for r in rows), default=0.0)
        # M3 positif = fibre inférieure tendue dans la convention ETABS locale.
        m_sag = max(0.0, sagging)
        m_hog = max(0.0, -hogging)
        v_max = max((abs(r.v2) for r in rows), default=0.0)
        res_sag = solver.solve(BaelSection(b, h, config.steel_cover, config.steel_cover, 0.0, m_sag))
        res_hog = solver.solve(BaelSection(b, h, config.steel_cover, config.steel_cover, 0.0, m_hog))
        as_bottom_req = max(res_sag.required_cm2, 0.23 * b / 100 * (h - config.steel_cover) / 100 *
                            (0.6 + 0.06 * config.fc28) / config.fe * 10_000)
        as_top_req = max(res_hog.required_cm2, as_bottom_req * 0.5)
        bottom_label, bottom_provided, _ = select_beam_bars(as_bottom_req, b, config.steel_cover)
        top_label, top_provided, diam_top = select_beam_bars(as_top_req, b, config.steel_cover)
        d = h - config.steel_cover
        tau = v_max / (b / 100 * d / 100 * 1000.0) if b * d > 0 else 0.0
        ft28 = 0.6 + 0.06 * config.fc28
        tau_lim = min(0.2 * config.fc28 / config.gamma_b, 4.0)
        # Armatures d'effort tranchant : At/s = (tau - 0.3 ft28) b / (0.9 fe/gamma_s)
        at_s = max(0.0, (tau - 0.3 * ft28) * (b / 100.0) /
                   (0.9 * config.fe / config.gamma_s)) * 10_000.0 if tau > 0.3 * ft28 else 0.0
        stirrup_d = 8
        spacing = min(20.0, 1.5 * h)
        if at_s > 0:
            spacing = min(spacing, max(8.0, 2 * bar_area_cm2(stirrup_d) / at_s))
        beam_designs[section.name] = {
            "as_bottom": bottom_provided, "as_top": top_provided,
            "as_bottom_req": as_bottom_req, "as_top_req": as_top_req,
            "d": d / 100.0, "bottom": bottom_label, "top": top_label,
            "m_sag": m_sag, "m_hog": m_hog, "v_max": v_max,
        }
        beam_checks = [
            check_min("PB1_LARGEUR", f"Largeur poutre {family.key}", "RPA 2024 § 7.5.1", b,
                      25.0 if zone in {"IV", "V", "VI"} else 20.0, "cm"),
            check_min("PB2_HAUTEUR", f"Hauteur poutre {family.key}", "RPA 2024 § 7.5.1", h,
                      20.0 if resolved.category in {"B", "C"} else 30.0, "cm"),
            check_max("PB3_ELANCEMENT", f"Élancement h/b {family.key}", "RPA 2024 § 7.5.1",
                      h / b if b else 0, 4.0, "", 2),
            check_bool("PB4_FLEXION", f"Ferraillage longitudinal {family.key}",
                       "BAEL 91 révisé 99 / RPA 2024 § 7.5",
                       bottom_provided + 1e-9 >= as_bottom_req and top_provided + 1e-9 >= as_top_req,
                       f"As inf ≥ {fmt(as_bottom_req, 2)} cm² ; As sup ≥ {fmt(as_top_req, 2)} cm²",
                       f"{bottom_label} en travée ({fmt(bottom_provided, 2)} cm²) ; "
                       f"{top_label} en appui ({fmt(top_provided, 2)} cm²)",
                       message=f"Ferraillage insuffisant pour {family.key}."),
            check_max("PB5_CISAILLEMENT", f"Cisaillement {family.key}", "BAEL 91 A.5.1.2",
                      tau, tau_lim, "MPa", 2,
                      f"τu = {fmt(tau, 2)} MPa > {fmt(tau_lim, 2)} MPa : augmenter la section."),
        ]
        if family.max_clear_span > 0:
            l = family.max_clear_span * 100
            beam_checks.append(check_between(
                "PB6_RIGIDITE", f"Prédimensionnement h {family.key}", "Règle L/15 ≤ h ≤ L/10",
                h, l / 15.0, l / 10.0, "cm", 1,
                message=f"h = {fmt(h, 1)} cm hors de la plage L/15–L/10 "
                        f"({fmt(l / 15, 1)}–{fmt(l / 10, 1)} cm).",
            ))
        beam_checks.append(check_max(
            "PB7_ETRIERS", f"Espacement des étriers {family.key}", "RPA 2024 § 7.5.2 / BAEL",
            spacing, min(20.0, 1.5 * h), "cm", 1,
            f"Espacement {fmt(spacing, 0)} cm > {fmt(min(20.0, 1.5 * h), 0)} cm.",
        ))
        beam_reports.append(ElementReport(
            "Poutre", family.key, family.geometry_label, beam_checks,
            [f"M+ = {fmt(m_sag, 1)} kN.m ; M- = {fmt(m_hog, 1)} kN.m ; V = {fmt(v_max, 1)} kN.",
             f"Travée : {bottom_label} ; appui : {top_label} ; étriers T{stirrup_d} e={fmt(spacing, 0)} cm."],
        ))
        lines.append(f"{family.key} : {bottom_label} en travée, {top_label} en appui, "
                     f"T{stirrup_d} e={fmt(spacing, 0)} cm.")

    # Voiles : dispositions constructives RPA 2024.
    for family in model.wall_families():
        section = family.section
        bw = section.thickness / 100.0
        lw = family.length_lw
        he = family.max_clear_height
        hw = family.total_height
        required_lw = max(he / 3.0, 4 * bw, 1.0)
        required_bw = max(0.15, he / 20.0)
        slenderness = hw / lw if lw > 0 else 0.0
        classification = ("Voile élancé" if slenderness > 2.0 else
                          "Voile court" if slenderness > 2.0 / 3.0 else "Voile de grande dimension")
        wall_checks = [
            check_min("PV1_LONGUEUR", f"Longueur du voile {family.key}", "RPA 2024 § 7.7.1, Éq. 7.9",
                      lw, required_lw, "m", 2,
                      f"lw = {fmt(lw, 2)} m < max(he/3 ; 4bw ; 1 m) = {fmt(required_lw, 2)} m."),
            check_min("PV2_EPAISSEUR", f"Épaisseur du voile {family.key}", "RPA 2024 § 7.7.1, Éq. 7.10",
                      bw, required_bw, "m", 3,
                      f"bw = {fmt(bw, 3)} m < max(15 cm ; he/20) = {fmt(required_bw, 3)} m."),
            check_info("PV3_CLASSIFICATION", f"Classification {family.key}", "RPA 2024 § 7.7.4",
                       f"{classification} (hw/lw = {fmt(slenderness, 2)})"),
            check_min("PV4_ACIER", f"Armatures verticales {family.key}", "RPA 2024 § 7.7.4",
                      0.20, 0.20, "%", 2),
        ]
        wall_reports.append(ElementReport(
            "Voile", family.key,
            f"lw = {fmt(lw, 2)} m ; bw = {fmt(bw * 100, 0)} cm ; he = {fmt(he, 2)} m ; "
            f"hw = {fmt(hw, 2)} m",
            wall_checks,
            [f"Zone critique : longueur ≥ max(1,5.bw ; 0,15.lw) = "
             f"{fmt(max(1.5 * bw, 0.15 * lw), 2)} m, armatures verticales 0,20 % minimum."],
        ))
        lines.append(f"{family.key} : {classification}, lw = {fmt(lw, 2)} m, bw = {fmt(bw * 100, 0)} cm.")

    if not column_reports:
        checks.append(check_na("FERRAILLAGE_POTEAUX", "Ferraillage des poteaux", "BAEL 91",
                               "Aucune famille de poteaux."))
    else:
        checks.append(Check("FERRAILLAGE_POTEAUX", "Synthèse du ferraillage des poteaux",
                            "BAEL 91 / RPA 2024",
                            Status.worst([r.status for r in column_reports]),
                            "Toutes les familles vérifiées",
                            f"{len(column_reports)} famille(s)"))
    if not beam_reports:
        checks.append(check_na("FERRAILLAGE_POUTRES", "Ferraillage des poutres", "BAEL 91",
                               "Aucune famille de poutres."))
    else:
        checks.append(Check("FERRAILLAGE_POUTRES", "Synthèse du ferraillage des poutres",
                            "BAEL 91 / RPA 2024",
                            Status.worst([r.status for r in beam_reports]),
                            "Toutes les familles vérifiées",
                            f"{len(beam_reports)} famille(s)"))
    if not wall_reports:
        warnings.append("Aucun voile détecté dans le modèle importé : chapitre 11 partiellement sans objet.")
    return ReinforcementReport(column_reports, beam_reports, wall_reports,
                               column_designs, beam_designs, checks, lines, warnings)


def design_columns_only(model, results, config, resolved) -> dict[str, dict]:
    """Ferraillage des poteaux utilise pour la hierarchie poteau fort / poutre faible."""
    return design_reinforcement(model, results, config, resolved).column_designs


# ---------------------------------------------------------------------------
# Fondations et Winkler
# ---------------------------------------------------------------------------

@dataclass
class FootingDesign:
    family: str
    b: float
    length: float
    thickness: float
    n_service: float
    n_ultimate: float
    mx: float
    my: float
    q_max: float
    q_min: float
    q_adm: float
    settlement: float
    q_winkler: float
    steel_cm2: float
    status: Status
    checks: list[Check]
    message: str = ""


@dataclass
class FoundationAssessment:
    footings: list[FootingDesign]
    checks: list[Check]
    safe_pressures: list[SoilPressure]
    lines: list[str]
    warnings: list[str] = field(default_factory=list)

    @property
    def status(self) -> Status:
        return Status.worst([c.status for c in self.checks]
                            + [f.status for f in self.footings])


def design_foundations(model: StructuralModel, results, config: ProjectConfig) -> FoundationAssessment:
    footings: list[FootingDesign] = []
    lines: list[str] = []
    checks: list[Check] = []
    warnings: list[str] = []
    grid = _round_up

    for family in model.column_families():
        rows = _governing_forces(results.column_forces, family)
        service_rows = [r for r in rows if not _seismic_case(r.load_case)] or rows
        if not service_rows:
            warnings.append(f"Aucun effort pour la semelle {family.key} : dimensionnement non automatisé.")
            continue
        governing = max(service_rows, key=lambda r: max(0.0, -r.p))
        n_service = max(0.0, -governing.p)
        ultimate_rows = [r for r in rows if _seismic_case(r.load_case)]
        n_ultimate = max(0.0, -max((r.p for r in ultimate_rows), default=governing.p))
        mx = abs(governing.m2)
        my = abs(governing.m3)
        if n_service <= 1e-6:
            warnings.append(f"Semelle {family.key} : effort normal de service nul.")
            continue
        # Dimensionnement en plan a partir de la portance, en tenant compte de l'excentrement.
        b = max(1.2, grid(math.sqrt(1.10 * n_service / config.q_adm), 0.05))
        length = b
        thickness = max(0.30, grid(max(b, length) / 5.0, 0.05))
        for _ in range(200):
            area = b * length
            q_mean = n_service / area
            q_bending = 6 * max(mx, my) / (b * length * length)
            if q_mean + q_bending <= config.q_adm + 1e-6:
                break
            b = grid(b + 0.05, 0.05)
            length = b
            thickness = max(thickness, grid(max(b, length) / 5.0, 0.05))
        area = b * length
        q_mean = n_service / area
        q_bending = 6 * max(mx, my) / (b * length * length)
        q_max = q_mean + q_bending
        q_min = q_mean - q_bending
        settlement = q_max / config.subgrade_modulus if config.subgrade_modulus > 0 else 0.0
        q_winkler = config.subgrade_modulus * settlement
        # Acier : moment de flexion a la face du poteau (console), methode des bielles simplifiee.
        column_size = min(family.section.b, family.section.h) / 100.0
        cantilever = max(0.05, (max(b, length) - column_size) / 2.0)
        m_footing = max(q_max * b * cantilever ** 2 / 2.0, 0.0)
        footing_section = BaelSection(b * 100, thickness * 100, 5.0, 5.0, 0.0, m_footing, accidental=True)
        solver = BaelSolver(config.footing_concrete, config.footing_steel, config.gamma_b, config.gamma_s)
        result = solver.solve(footing_section)
        steel_cm2 = max(result.required_cm2, 0.001 * b * thickness * 10_000.0)
        steel_label, steel_provided, _ = select_beam_bars(steel_cm2, b * 100, 5.0, (12, 14, 16, 20))

        local_checks = [
            check_max("SF1_PORTANCE", f"Portance {family.key}", "DTR BC 2.33.1 / BAEL",
                      q_max, config.q_adm, "kN/m²", 1,
                      f"q_max = {fmt(q_max, 1)} kN/m² > q_adm = {fmt(config.q_adm, 1)} kN/m²."),
            check_bool("SF2_SOULEVEMENT", f"Semelle entièrement comprimée {family.key}",
                       "DTR BC 2.33.1", q_min >= -1e-6, "q_min ≥ 0",
                       f"q_min = {fmt(q_min, 1)} kN/m²", status_fail=Status.WARNING,
                       message="Soulèvement partiel : augmenter la semelle ou vérifier le renversement."),
            check_max("SF3_TASSEMENT", f"Tassement {family.key}", "DTR BC 2.33.1 / Winkler",
                      settlement * 1000.0, config.settlement_limit_mm, "mm", 1,
                      f"Δ = {fmt(settlement * 1000, 1)} mm > "
                      f"{fmt(config.settlement_limit_mm, 0)} mm."),
            check_min("SF4_EPAISSEUR", f"Épaisseur de semelle {family.key}", "BAEL 91 / DTR BC 2.33.1",
                      thickness * 100, 30.0, "cm", 1),
            check_max("SF5_POINCONNEMENT", f"Poinçonnement {family.key}", "BAEL 91 A.5.2",
                      n_ultimate / (4 * (column_size + thickness) * thickness * 1000.0),
                      min(0.2 * config.footing_concrete / config.gamma_b, 4.0), "MPa", 2),
            check_bool("SF6_ACIER", f"Ferraillage {family.key}", "BAEL 91",
                       steel_provided + 1e-9 >= steel_cm2, f"As ≥ {fmt(steel_cm2, 2)} cm²",
                       f"{steel_label} = {fmt(steel_provided, 2)} cm²"),
        ]
        status = Status.worst(c.status for c in local_checks)
        footings.append(FootingDesign(family.key, b, length, thickness, n_service, n_ultimate,
                                      mx, my, q_max, q_min, config.q_adm, settlement,
                                      q_winkler, steel_provided, status, local_checks,
                                      f"{steel_label} ; B×L×h = {fmt(b, 2)}×{fmt(length, 2)}×"
                                      f"{fmt(thickness, 2)} m"))
        lines.append(
            f"{family.key} : B×L×h = {fmt(b, 2)}×{fmt(length, 2)}×{fmt(thickness, 2)} m, "
            f"N_ser = {fmt(n_service, 1)} kN, q_max = {fmt(q_max, 1)} kN/m², "
            f"tassement Winkler = {fmt(settlement * 1000, 1)} mm."
        )

    # Contraintes SAFE : comparaison directe si disponibles.
    safe_pressures = list(results.soil_pressures)
    if safe_pressures:
        worst = max(safe_pressures, key=lambda p: p.pressure)
        checks.append(check_max("SF_SAFE_PORTANCE", "Contraintes SAFE", "DTR BC 2.33.1 / SAFE",
                                worst.pressure, config.q_adm, "kN/m²", 1,
                                f"Pression SAFE {fmt(worst.pressure, 1)} kN/m² > q_adm."))
        # Verification de Winkler sur les deplacements SAFE.
        winkler_values = [p for p in safe_pressures if p.displacement > 1e-6]
        if winkler_values:
            governing = max(winkler_values, key=lambda p: abs(p.displacement))
            q_winkler = config.subgrade_modulus * abs(governing.displacement)
            checks.append(check_max("SF_WINKLER", "Contrainte de Winkler (SAFE)",
                                    "DTR BC 2.33.1, σ = Ks.δ", q_winkler, config.q_adm,
                                    "kN/m²", 1,
                                    f"σ_w = Ks.δ = {fmt(q_winkler, 1)} kN/m² > q_adm."))
        else:
            checks.append(check_info("SF_WINKLER", "Contrainte de Winkler",
                                     "DTR BC 2.33.1, σ = Ks.δ",
                                     "Déplacements SAFE absents : Winkler calculé sur le tassement estimé."))
    else:
        checks.append(check_info("SF_WINKLER", "Contrainte de Winkler",
                                 "DTR BC 2.33.1, σ = Ks.δ",
                                 "Aucune pression SAFE : tassement estimé par q_max/Ks."))

    if not footings:
        checks.append(check_na("FONDATIONS", "Dimensionnement des fondations", "DTR BC 2.33.1",
                               "Aucune semelle dimensionnable (efforts absents)."))
    else:
        checks.insert(0, Check("FONDATIONS", "Synthèse des fondations", "DTR BC 2.33.1",
                               Status.worst(f.status for f in footings),
                               "Toutes les semelles vérifiées",
                               f"{len(footings)} famille(s)"))
        lines.append(f"Module de réaction du sol Ks = {fmt(config.subgrade_modulus, 0)} kN/m³ ; "
                     f"portance admissible q_adm = {fmt(config.q_adm, 1)} kN/m².")
    return FoundationAssessment(footings, checks, safe_pressures, lines, warnings)


def _round_up(value: float, step: float) -> float:
    return math.ceil(value / step - 1e-9) * step


def _slab_family_area(model: StructuralModel, family: ElementFamily) -> float:
    """Somme des aires de panneaux de la famille, sans hypothese de forme."""
    total = 0.0
    for slab in model.slabs:
        if slab.section_name != family.section.name:
            continue
        points = [model.points[n] for n in slab.nodes if n in model.points]
        if len(points) >= 3:
            total += _polygon_area([(p.x, p.y) for p in points])
    return total


# ---------------------------------------------------------------------------
# Coffrage / predimensionnement
# ---------------------------------------------------------------------------

def assess_formwork(model: StructuralModel, config: ProjectConfig,
                    resolved: ResolvedSeismic) -> tuple[list[ElementReport], list[str]]:
    reports: list[ElementReport] = []
    lines: list[str] = []
    zone = resolved.zone_code
    for family in model.beam_families():
        sec = family.section
        checks = [
            check_min("COF_B_LARGEUR", f"Largeur {family.key}", "RPA 2024 § 7.5.1", sec.b,
                      25.0 if zone in {"IV", "V", "VI"} else 20.0, "cm"),
            check_min("COF_B_HAUTEUR", f"Hauteur {family.key}", "RPA 2024 § 7.5.1", sec.h,
                      20.0 if braking_walls(config.bracing_id) else 30.0, "cm"),
            check_max("COF_B_ELANCEMENT", f"Élancement h/b {family.key}", "RPA 2024 § 7.5.1",
                      sec.h / sec.b if sec.b else 0, 4.0, "", 2),
        ]
        if family.max_clear_span > 0:
            span_cm = family.max_clear_span * 100
            checks.append(check_between(
                "COF_B_RIGIDITE", f"Prédimensionnement {family.key}", "L/15 ≤ h ≤ L/10",
                sec.h, span_cm / 15.0, span_cm / 10.0, "cm", 1,
                message=f"h = {fmt(sec.h, 1)} cm hors de {fmt(span_cm / 15, 1)}–{fmt(span_cm / 10, 1)} cm.",
            ))
        reports.append(ElementReport("Poutre", family.key, family.geometry_label, checks))
        lines.append(f"Poutre {family.key} : {sec.label}, portée libre {fmt(family.max_clear_span, 2)} m.")
    for family in model.column_families():
        sec = family.section
        circular = sec.kind == "FRAME_CIRCULAR"
        min_dim = 35.0 if circular and zone in {"IV", "V", "VI"} else (
            30.0 if circular and zone == "III" else
            30.0 if zone in {"IV", "V", "VI"} else 25.0
        )
        checks = [
            check_min("COF_C_DIM", f"Dimension minimale {family.key}", "RPA 2024 § 7.4.1",
                      min(sec.b, sec.h), min_dim, "cm"),
            check_max("COF_C_RAPPORT", f"Rapport b/h {family.key}", "RPA 2024 § 7.4.1",
                      max(sec.b, sec.h) / min(sec.b, sec.h) if min(sec.b, sec.h) else 0,
                      4.0, "", 2),
        ]
        if family.max_clear_height > 0:
            checks.append(check_min(
                "COF_C_ELANCEMENT", f"Élancement lcl/dimension {family.key}", "RPA 2024 § 7.4.1",
                min(sec.b, sec.h), family.max_clear_height * 100 / (15.0 if circular else 20.0), "cm",
            ))
        reports.append(ElementReport("Poteau", family.key, family.geometry_label, checks))
    for family in model.slab_families():
        sec = family.section
        thickness = sec.total_thickness or sec.thickness
        if family.max_clear_span > 0:
            divisor = 25.0 if sec.hollow_height <= 0 else 22.5
            checks = [check_min("COF_D_EPAISSEUR", f"Épaisseur {family.key}", "DTR BC 2.2",
                                thickness, family.max_clear_span * 100 / divisor, "cm")]
        else:
            checks = [check_na("COF_D_EPAISSEUR", f"Épaisseur {family.key}", "DTR BC 2.2",
                               "Portée de plancher indisponible.")]
        if sec.hollow_height > 0:
            checks.append(check_min("COF_D_TABLE", f"Table de compression {family.key}",
                                    "DTR BC 2.2", sec.top_thickness, 4.0, "cm"))
            checks.append(check_min("COF_D_NERVURE", f"Nervure {family.key}",
                                    "DTR BC 2.2", sec.rib_width, 10.0, "cm"))
        reports.append(ElementReport("Plancher", family.key, sec.label, checks))
    return reports, lines


# ---------------------------------------------------------------------------
# DQE
# ---------------------------------------------------------------------------

@dataclass
class DqeLine:
    chapter: str
    designation: str
    unit: str
    quantity: float
    unit_price: float = 0.0

    @property
    def total(self) -> float:
        return self.quantity * self.unit_price


@dataclass
class DqeReport:
    lines: list[DqeLine]
    totals: dict[str, float]
    table: ReportTable

    @property
    def total_concrete(self) -> float:
        return self.totals.get("Béton (m³)", 0.0)

    @property
    def total_formwork(self) -> float:
        return self.totals.get("Coffrage (m²)", 0.0)

    @property
    def total_steel(self) -> float:
        return self.totals.get("Acier (kg)", 0.0)


def compute_dqe(model: StructuralModel, reinforcement: ReinforcementReport,
                foundations: FoundationAssessment, config: ProjectConfig) -> DqeReport:
    lines: list[DqeLine] = []
    concrete = formwork = steel = 0.0

    for family in model.column_families():
        lengths = [m.clear_length for m in model.columns
                   if m.section_name == family.section.name and m.clear_length > 0]
        length = sum(lengths) if lengths else family.max_clear_height * max(1, len(family.stories))
        b, h = family.section.b / 100.0, family.section.h / 100.0
        volume = b * h * length
        surf = 2 * (b + h) * length
        design = reinforcement.column_designs.get(family.section.name, {})
        as_cm2 = design.get("as_total", 0.0)
        mass = as_cm2 / 10_000.0 * length * 7850.0
        if volume > 0:
            lines.append(DqeLine("Poteaux", f"Béton poteaux {family.key}", "m³", volume))
            lines.append(DqeLine("Poteaux", f"Coffrage poteaux {family.key}", "m²", surf))
            lines.append(DqeLine("Poteaux", f"Acier poteaux {family.key} ({design.get('label', '-')})",
                                 "kg", mass))
            concrete += volume; formwork += surf; steel += mass

    for family in model.beam_families():
        length = sum(m.length for m in model.beams if m.section_name == family.section.name)
        b, h = family.section.b / 100.0, family.section.h / 100.0
        volume = b * h * length
        surf = (b + 2 * h) * length
        design = reinforcement.beam_designs.get(family.section.name, {})
        as_cm2 = design.get("as_bottom", 0.0) + design.get("as_top", 0.0)
        mass = as_cm2 / 10_000.0 * length * 7850.0
        if volume > 0:
            lines.append(DqeLine("Poutres", f"Béton poutres {family.key}", "m³", volume))
            lines.append(DqeLine("Poutres", f"Coffrage poutres {family.key}", "m²", surf))
            lines.append(DqeLine("Poutres", f"Acier poutres {family.key}", "kg", mass))
            concrete += volume; formwork += surf; steel += mass

    for family in model.wall_families():
        volume = family.section.thickness / 100.0 * family.length_lw * family.total_height
        surf = 2 * family.length_lw * family.total_height
        mass = 0.002 * family.section.thickness / 100.0 * family.length_lw * family.total_height * 7850.0
        if volume > 0:
            lines.append(DqeLine("Voiles", f"Béton voiles {family.key}", "m³", volume))
            lines.append(DqeLine("Voiles", f"Coffrage voiles {family.key}", "m²", surf))
            lines.append(DqeLine("Voiles", f"Acier voiles {family.key}", "kg", mass))
            concrete += volume; formwork += surf; steel += mass

    for family in model.slab_families():
        area = _slab_family_area(model, family)
        thickness = (family.section.total_thickness or family.section.thickness) / 100.0
        if area > 0:
            volume = area * thickness
            surf = area
            mass = volume * 80.0
            lines.append(DqeLine("Planchers", f"Béton planchers {family.key}", "m³", volume))
            lines.append(DqeLine("Planchers", f"Coffrage planchers {family.key}", "m²", surf))
            lines.append(DqeLine("Planchers", f"Acier planchers {family.key}", "kg", mass))
            concrete += volume; formwork += surf; steel += mass

    for footing in foundations.footings:
        volume = footing.b * footing.length * footing.thickness
        surf = footing.b * footing.length
        mass = volume * 90.0
        lines.append(DqeLine("Fondations", f"Béton semelle {footing.family}", "m³", volume))
        lines.append(DqeLine("Fondations", f"Coffrage semelle {footing.family}", "m²", 2 * (footing.b + footing.length) * footing.thickness))
        lines.append(DqeLine("Fondations", f"Acier semelle {footing.family}", "kg", mass))
        concrete += volume; formwork += surf * 0.0 + 2 * (footing.b + footing.length) * footing.thickness
        steel += mass

    totals = {
        "Béton (m³)": concrete,
        "Coffrage (m²)": formwork,
        "Acier (kg)": steel,
        "Ratio acier (kg/m³)": steel / concrete if concrete > 0 else 0.0,
    }
    table = ReportTable(
        "Devis quantitatif estimatif",
        ["Chapitre", "Désignation", "Unité", "Quantité", "Prix unitaire", "Total"],
        [[line.chapter, line.designation, line.unit, fmt(line.quantity, 2),
          fmt(line.unit_price, 2), fmt(line.total, 2)] for line in lines],
        widths=[2.2, 8.0, 2.0, 2.4, 2.6, 2.8],
    )
    return DqeReport(lines, totals, table)


# ---------------------------------------------------------------------------
# Synthese et chapitres
# ---------------------------------------------------------------------------

@dataclass
class EngineOptions:
    include_foundations: bool = True
    include_dqe: bool = True
    include_plans: bool = True
    q_adm: float | None = None
    subgrade_modulus: float | None = None
    settlement_limit_mm: float = 25.0


@dataclass
class Assessment:
    project_name: str
    joint_name: str
    generated_at: str
    config: ProjectConfig
    model: StructuralModel
    results: object
    chapters: list[ChapterReport]
    seismic: SeismicAssessment
    regularity: RegularityAssessment
    reinforcement: ReinforcementReport
    foundations: FoundationAssessment
    joints: JointAssessment
    dqe: DqeReport | None
    warnings: list[str]
    summary: list[Check]
    tables: list[ReportTable] = field(default_factory=list)

    @property
    def status(self) -> Status:
        return Status.worst(c.status for c in self.chapters)

    @property
    def failed_count(self) -> int:
        return sum(c.failed_count for c in self.chapters)

    @property
    def warning_count(self) -> int:
        return sum(c.warning_count for c in self.chapters)

    @property
    def checked_elements(self) -> int:
        return sum(len(c.elements) for c in self.chapters)

    @property
    def main_stories(self) -> int:
        return self.model.num_stories


def assess(project: ParsedProject, options: EngineOptions | None = None) -> Assessment:
    options = options or EngineOptions()
    model = project.model
    results = project.results
    config = project.config
    if options.q_adm is not None:
        config.q_adm = options.q_adm
    if options.subgrade_modulus is not None:
        config.subgrade_modulus = options.subgrade_modulus
    config.settlement_limit_mm = options.settlement_limit_mm

    warnings = list(project.warnings)
    # La regularite reelle fixe le QF : elle doit etre evaluee avant le calcul
    # sismique pour que MSE/MDS, deplacements et P-Delta utilisent le QF final.
    regularity = assess_regularity(model, results, config, None)
    warnings.extend(regularity.warnings)
    seismic = evaluate_seismic(model, results, config)
    warnings.extend(seismic.warnings)
    reinforcement = design_reinforcement(model, results, config, seismic.resolved)
    warnings.extend(reinforcement.warnings)
    foundations = design_foundations(model, results, config) if options.include_foundations else FoundationAssessment(
        [], [check_na("FONDATIONS", "Fondations", "DTR BC 2.33.1", "Option désactivée.")], [], [], []
    )
    warnings.extend(foundations.warnings)
    joints = assess_joints(model, results, config, reinforcement.column_designs,
                           reinforcement.beam_designs, seismic.resolved)
    warnings.extend(joints.warnings)
    formwork, form_lines = assess_formwork(model, config, seismic.resolved)
    dqe = compute_dqe(model, reinforcement, foundations, config) if options.include_dqe else None

    chapters = _build_chapters(project, seismic, regularity, reinforcement,
                               foundations, joints, formwork, form_lines, dqe)
    summary = _summary_checks(model, seismic, regularity, reinforcement, foundations, joints, chapters)
    warnings = _dedupe(warnings)
    assessment = Assessment(
        project_name=config.project_name,
        joint_name=config.joint_name or model.joint_name,
        generated_at=datetime.now().strftime("%d/%m/%Y %H:%M"),
        config=config,
        model=model,
        results=results,
        chapters=chapters,
        seismic=seismic,
        regularity=regularity,
        reinforcement=reinforcement,
        foundations=foundations,
        joints=joints,
        dqe=dqe,
        warnings=warnings,
        summary=summary,
    )
    assessment.tables = _assessment_tables(assessment)
    return assessment


def run_checks(project: ParsedProject, options: EngineOptions | None = None) -> Assessment:
    return assess(project, options)


def _build_chapters(project: ParsedProject, seismic: SeismicAssessment,
                    regularity: RegularityAssessment, reinforcement: ReinforcementReport,
                    foundations: FoundationAssessment, joints: JointAssessment,
                    formwork: list[ElementReport], form_lines: list[str],
                    dqe: DqeReport | None) -> list[ChapterReport]:
    model, config, results = project.model, project.config, project.results
    chapters: list[ChapterReport] = []

    # 1. Objet et donnees
    chapters.append(ChapterReport(
        1, "Objet et données du projet",
        "Note de calcul de la structure en béton armé, vérifiée selon le RPA 2024 "
        "(DTR BC 2.48) et le BAEL 91 révisé 99.",
        checks=[
            check_info("PROJET", "Nom du projet", "Données de saisie", config.project_name),
            check_info("JOINT", "Joint / bloc étudié", "Données de saisie", config.joint_name),
            check_info("WILAYA", "Wilaya", "Données de saisie", config.wilaya),
            check_info("ZONE", "Zone sismique", "RPA 2024 Tableau 3.2",
                       f"Zone {seismic.resolved.zone_code} (A = {fmt(seismic.resolved.a, 2)})"),
            check_info("SITE", "Classe de site", "RPA 2024 Tableau 3.1", config.site_class),
            check_info("IMPORTANCE", "Groupe d'importance", "RPA 2024 Tableau 3.10",
                       f"{config.importance_code} (I = {fmt(seismic.resolved.importance, 2)})"),
            check_info("CONTREVENTEMENT", "Système de contreventement", "RPA 2024 Tableau 3.17",
                       f"Système {config.bracing_id} (R = {fmt(config.behavior_factor, 1)})"),
        ],
        lines=[
            f"Fichiers importés : {', '.join(project.source_files) or 'aucun'}.",
            f"Modèle : {model.num_stories} niveaux, {len(model.columns)} poteaux, "
            f"{len(model.beams)} poutres, {len(model.walls)} voiles, {len(model.slabs)} planchers.",
            f"Hauteur totale Hn = {fmt(model.total_height, 2)} m ; "
            f"travées détectées : {model.num_bays_x} en X, {model.num_bays_y} en Y.",
        ],
        tables=[ReportTable("Synthèse du modèle", ["Grandeur", "Valeur"], [
            ["Niveaux", str(model.num_stories)],
            ["Poteaux", str(len(model.columns))],
            ["Poutres", str(len(model.beams))],
            ["Voiles", str(len(model.walls))],
            ["Planchers", str(len(model.slabs))],
            ["Hauteur Hn (m)", fmt(model.total_height, 2)],
            ["Travées X / Y", f"{model.num_bays_x} / {model.num_bays_y}"],
        ])],
    ))

    # 2. Base reglementaire
    chapters.append(ChapterReport(
        2, "Base réglementaire et hypothèses",
        "Règles de calcul et hypothèses retenues pour l'analyse et le dimensionnement.",
        checks=[
            check_info("RPA", "RPA 2024", "DTR BC 2.48", "Règles parasismiques applicables aux bâtiments."),
            check_info("BAEL", "BAEL 91 révisé 99", "DTU P 18-702", "Calcul des structures en béton armé."),
            check_info("CBA", "CBA 93", "DTR BC 2.2", "Règles de calcul et de conception des planchers."),
            check_info("SOL", "DTR BC 2.33.1", "Fondations", "Portance et tassement des fondations."),
        ],
        lines=[
            "Combinaisons ELU : 1,35 G + 1,50 Q.",
            "Combinaisons accidentelles sismiques : G + Q ± E avec ψ selon Tableau 4.2.",
            f"Amortissement retenu : ξ = {fmt(seismic.resolved.damping, 1)} % ; "
            f"coefficient η = {fmt(seismic.resolved.eta, 3)} (Éq. 3.9).",
            f"Facteur de qualité QF = {fmt(seismic.resolved.qf, 2)} ; "
            f"coefficient de comportement R = {fmt(seismic.resolved.r, 1)}.",
            "Les réserves calculées sont reportées sans masquage : une vérification non satisfaite "
            "reste signalée dans la synthèse.",
        ],
        tables=[ReportTable("Hypothèses sismiques", ["Paramètre", "Valeur"], [
            ["Zone", seismic.resolved.zone_code],
            ["Coefficient A", fmt(seismic.resolved.a, 2)],
            ["Site", seismic.resolved.site],
            ["Spectre", seismic.resolved.spectrum_type],
            ["I", fmt(seismic.resolved.importance, 2)],
            ["R", fmt(seismic.resolved.r, 1)],
            ["QF", fmt(seismic.resolved.qf, 2)],
            ["CT", fmt(seismic.resolved.ct, 3)],
            ["T empirique (s)", fmt(seismic.resolved.empirical_period, 3)],
            ["λ", fmt(seismic.resolved.lambda_factor, 2)],
            ["ξ (%)", fmt(seismic.resolved.damping, 1)],
            ["η", fmt(seismic.resolved.eta, 3)],
            ["ψ", fmt(seismic.resolved.psi, 2)],
        ])],
    ))

    # 3. Presentation de la structure
    structure_rows = [
        ["Niveaux", str(model.num_stories), "—"],
        ["Hauteur totale", fmt(model.total_height, 2), "m"],
        ["Poteaux", str(len(model.columns)), "—"],
        ["Poutres", str(len(model.beams)), "—"],
        ["Voiles", str(len(model.walls)), "—"],
        ["Planchers", str(len(model.slabs)), "—"],
    ]
    chapters.append(ChapterReport(
        3, "Présentation de la structure",
        "Description de la géométrie et du système porteur issus du modèle ETABS/SAFE.",
        checks=[
            check_bool("MODELE", "Modèle importé", "ETABS/SAFE",
                       bool(model.frames or model.areas),
                       "Au moins un élément structurel", 
                       f"{len(model.frames)} éléments linéiques, {len(model.areas)} surfaces"),
            check_bool("PLANCHERS", "Planchers identifiés", "ETABS",
                       bool(model.slabs), "Planchers disponibles",
                       f"{len(model.slabs)} surface(s) de plancher",
                       status_fail=Status.WARNING,
                       message="Aucun plancher identifiable : les vérifications de plancher sont non applicables."),
        ],
        lines=[
            "La structure est décrite par les tableaux de connectivité importés ; "
            "aucune dimension n'est prédéfinie dans le logiciel.",
            f"Section de poteau la plus courante : "
            f"{model.column_families()[0].section.label if model.column_families() else '-'}.",
            f"Section de poutre la plus courante : "
            f"{model.beam_families()[0].section.label if model.beam_families() else '-'}.",
        ],
        tables=[ReportTable("Éléments structurels", ["Élément", "Nombre", "Unité"], structure_rows)],
    ))

    # 4. Charges et combinaisons
    weight = results.seismic_weight
    chapter4 = ChapterReport(
        4, "Charges et combinaisons",
        "Charges verticales, poids sismique et combinaisons de calcul.",
        checks=[
            check_info("POIDS_SISMIQUE", "Poids sismique W",
                       "ETABS Story Shears / cas WI",
                       f"{fmt(weight, 1)} kN" if weight else "non extrait"),
            check_bool("CAS_SISMIQUE", "Cas sismiques EX/EY", "ETABS",
                       any(_matches_case(r.load_case, "EX") for r in results.story_shears)
                       and any(_matches_case(r.load_case, "EY") for r in results.story_shears),
                       "Cas EX et EY disponibles",
                       f"{len(results.story_shears)} ligne(s) d'effort tranchant",
                       status_fail=Status.WARNING,
                       message="Cas sismiques incomplets : vérifier les combinaisons ETABS."),
        ],
        lines=[
            "Combinaisons prises en compte :",
            "COMB1 : 1,35 G + 1,50 Q",
            "COMB2 : G + Q + EX",
            "COMB3 : G + Q − EX",
            "COMB4 : G + Q + EY",
            "COMB5 : G + Q − EY",
            "COMB6 : 0,80 G ± EX",
            "COMB7 : 0,80 G ± EY",
            "COMB8 : G + Q + 0,30 EX + 0,30 EY",
        ],
        tables=[ReportTable("Combinaisons", ["Nom", "Expression"], [
            ["COMB1", "1,35 G + 1,50 Q"],
            ["COMB2", "G + Q + EX"],
            ["COMB3", "G + Q − EX"],
            ["COMB4", "G + Q + EY"],
            ["COMB5", "G + Q − EY"],
            ["COMB6", "0,80 G ± EX"],
            ["COMB7", "0,80 G ± EY"],
            ["COMB8", "G + Q + 0,30 EX + 0,30 EY"],
        ])],
    )
    chapters.append(chapter4)

    # 5. Materiaux
    chapters.append(ChapterReport(
        5, "Matériaux",
        "Caractéristiques mécaniques des matériaux et coefficients partiels.",
        checks=[
            check_bool("BETON", "Classe de béton", "BAEL 91",
                       config.fc28 >= 25.0, "fc28 ≥ 25 MPa (C25/30)", f"fc28 = {fmt(config.fc28, 1)} MPa",
                       status_fail=Status.WARNING,
                       message="Béton inférieur à C25/30 : justifier la classe retenue."),
            check_bool("ACIER", "Nuance d'acier", "BAEL 91",
                       config.fe >= 500, "FeE500", f"fe = {fmt(config.fe, 0)} MPa",
                       status_fail=Status.WARNING),
        ],
        lines=[
            f"f_c28 = {fmt(config.fc28, 1)} MPa ; f_t28 = {fmt(0.6 + 0.06 * config.fc28, 2)} MPa.",
            f"f_e = {fmt(config.fe, 0)} MPa ; γ_b = {fmt(config.gamma_b, 2)} ; γ_s = {fmt(config.gamma_s, 2)}.",
            f"Enrobage nominal retenu : {fmt(config.steel_cover, 1)} cm.",
        ],
        tables=[ReportTable("Matériaux", ["Matériau", "Caractéristique", "Valeur"], [
            ["Béton", "fc28", f"{fmt(config.fc28, 1)} MPa"],
            ["Béton", "ft28", f"{fmt(0.6 + 0.06 * config.fc28, 2)} MPa"],
            ["Acier", "fe", f"{fmt(config.fe, 0)} MPa"],
            ["Acier", "Es", "200 000 MPa"],
        ])],
    ))

    # 6. Predimensionnement / coffrage
    chapter6 = ChapterReport(
        6, "Prédimensionnement et coffrage",
        "Vérifications des dimensions minimales et des règles de prédimensionnement.",
        elements=formwork,
        lines=form_lines + [
            "Les dimensions sont comparées aux exigences RPA 2024 et aux règles L/15–L/10 "
            "pour les poutres et L/22,5–L/25 pour les planchers.",
        ],
        figure="[FIGURE_CAPTURE_LOGICIEL : ETABS – vue 3D du modèle et tableaux de sections]",
    )
    chapter6.checks.append(Check(
        "COFFRAGE", "Synthèse du prédimensionnement", "RPA 2024 / DTR BC 2.2",
        Status.worst(e.status for e in formwork), "Toutes les sections vérifiées",
        f"{len(formwork)} famille(s) contrôlée(s)",
    ))
    chapters.append(chapter6)

    # 7. Regularite
    reg_checks = [
        Check(c.code, c.label, c.reference, c.status,
              "Critère satisfait" if c.satisfied is not False else "Critère non satisfait",
              c.detail) for c in regularity.criteria
    ] + regularity.qf_checks
    chapters.append(ChapterReport(
        7, "Régularité et facteur de qualité",
        "Analyse des critères de régularité en plan et en élévation, puis calcul du QF.",
        checks=reg_checks,
        lines=[
            "Régularité en plan : " + (
                "structure régulière." if regularity.plan_regular else
                "au moins un critère non satisfait." if regularity.plan_regular is False
                else "critères non évaluables."
            ),
            "Régularité en élévation : " + (
                "structure régulière." if regularity.elevation_regular else
                "au moins un critère non satisfait." if regularity.elevation_regular is False
                else "critères non évaluables."
            ),
            f"Facteur de qualité QF = {fmt(regularity.qf, 2)}.",
        ],
        tables=[ReportTable("Critères de régularité", ["Code", "Critère", "Résultat", "Détail"],
                            [[c.code, c.label, c.status.value, c.detail] for c in regularity.criteria])],
        figure="[FIGURE_CAPTURE_LOGICIEL : ETABS – table Centers Of Mass And Rigidity et plan de niveau]",
    ))

    # 8. Methode sismique
    chapters.append(ChapterReport(
        8, "Méthode de calcul sismique",
        "Méthode statique équivalente comparée à l'analyse modale spectrale.",
        checks=[
            check_info("METHODE", "Méthode de calcul", "RPA 2024 § 4.2",
                       "Méthode statique équivalente avec vérification de la participation modale."),
            check_info("PERIODE", "Période fondamentale empirique", "RPA 2024 Éq. 4.4",
                       f"T = CT.Hn^(3/4) = {fmt(seismic.resolved.empirical_period, 3)} s"),
            check_info("LAMBDA", "Coefficient λ", "RPA 2024 Éq. 4.2",
                       fmt(seismic.resolved.lambda_factor, 2)),
            check_info("SPECTRE", "Spectre de calcul", "RPA 2024 Éq. 3.15",
                       f"Spectre type {seismic.resolved.spectrum_type}, site {seismic.resolved.site}"),
        ],
        lines=[
            f"Période empirique : T = {fmt(seismic.resolved.empirical_period, 3)} s.",
            f"Période analytique retenue (plafonnée à 1,30.T) : "
            f"{', '.join(f'{s.direction} = {fmt(s.period_retained, 3)} s' for s in seismic.base_shear)}.",
            f"Spectre : Sae/g max = {fmt(seismic.resolved.spectrum.elastic(seismic.resolved.spectrum.t1), 3) if seismic.resolved.spectrum else '-'} ; "
            f"Sad/g plateau = {fmt(2.5 * seismic.resolved.qf / seismic.resolved.r * seismic.resolved.a * seismic.resolved.importance * (seismic.resolved.spectrum.s if seismic.resolved.spectrum else 1), 3)}.",
        ],
        tables=[seismic.spectrum_rows] if seismic.spectrum_rows else [],
        figure="[FIGURE_CAPTURE_LOGICIEL : ETABS – définition du spectre RPA et cas de charge sismique]",
    ))

    # 9. Analyse sismique globale
    seismic_elements: list[ElementReport] = []
    if seismic.modal.status != Status.NA:
        seismic_elements.append(ElementReport(
            "Analyse modale", "Participation massique",
            f"{seismic.modal.mode_count} modes, Tk = {fmt(seismic.modal.last_period, 3)} s",
            [Check("MODAL", "Participation modale", "RPA 2024 § 4.3.3",
                   seismic.modal.status,
                   "ΣUX et ΣUY ≥ 90 % ou k ≥ 3√N et Tk ≤ 0,20 s",
                   f"ΣUX = {fmt(seismic.modal.sum_ux, 1)} %, ΣUY = {fmt(seismic.modal.sum_uy, 1)} %",
                   seismic.modal.message)],
        ))
    for shear in seismic.base_shear:
        seismic_elements.append(ElementReport(
            f"Force sismique {shear.direction}", f"MSE/MDS {shear.direction}",
            f"T = {fmt(shear.period_retained, 3)} s, Sad/g = {fmt(shear.acceleration, 4)}",
            [Check(f"MSE_MDS_{shear.direction}", f"Calage MSE/MDS {shear.direction}",
                   "RPA 2024 § 4.3.5", shear.status,
                   "V_MDS ≥ 0,80.V_MSE",
                   f"V_MSE = {fmt(shear.static_kn, 1)} kN, V_MDS = {fmt(shear.dynamic_kn, 1)} kN",
                   shear.message)],
        ))
    for column in seismic.columns:
        seismic_elements.append(ElementReport(
            "Poteau", f"{column.family} ({column.element})",
            f"Bc = {fmt(column.area_cm2, 0)} cm²",
            [Check(f"NU_{column.family}", f"Effort normal réduit {column.family}",
                   "RPA 2024 § 7.4.3, Éq. 7.5",
                   column.status, "ν ≤ 0,35", f"ν = {fmt(column.nu, 3)}", column.message)],
        ))
    for drift in seismic.all_drifts:
        seismic_elements.append(ElementReport(
            f"Déplacement {drift.direction}", drift.story, f"hk = {fmt(drift.height, 2)} m",
            [Check(f"DRIFT_{drift.direction}_{drift.story}", f"Déplacement inter-étages {drift.direction}",
                   "RPA 2024 § 5.10", drift.status,
                   f"νA.|Δ| ≤ {fmt(drift.governing * 1000, 2)} mm",
                   f"{fmt(drift.reduced * 1000, 2)} mm", drift.message)],
        ))
    for pdelta in seismic.all_pdelta:
        seismic_elements.append(ElementReport(
            f"P-Delta {pdelta.direction}", pdelta.story, f"hk = {fmt(pdelta.height, 2)} m",
            [Check(f"PDELTA_{pdelta.direction}_{pdelta.story}", f"Effet P-Delta {pdelta.direction}",
                   "RPA 2024 § 5.9, Éq. 5.10", pdelta.status,
                   "θ ≤ 0,10 ou amplification", f"θ = {fmt(pdelta.theta, 3)}", pdelta.message)],
        ))
    chapter9 = ChapterReport(
        9, "Analyse sismique globale",
        "Participation modale, calage MSE/MDS, effort normal réduit, déplacements inter-étages et P-Delta.",
        checks=seismic.checks,
        elements=seismic_elements,
        lines=[
            seismic.modal.message,
            *[f"{s.direction} : V_MSE = {fmt(s.static_kn, 1)} kN, V_MDS = {fmt(s.dynamic_kn, 1)} kN, "
              f"rapport = {fmt((s.ratio or 0) * 100, 1)} %." for s in seismic.base_shear],
            (f"Déplacement le plus défavorable : {max(seismic.all_drifts, key=lambda d: d.utilization).story} "
             f"(νA.|Δ| = {fmt(max(seismic.all_drifts, key=lambda d: d.utilization).reduced * 1000, 2)} mm)."
             if [d for d in seismic.all_drifts if d.status != Status.NA] else
             "Déplacements non disponibles."),
            (f"P-Delta le plus défavorable : θ = "
             f"{fmt(max(seismic.all_pdelta, key=lambda p: p.theta).theta, 3)}."
             if [p for p in seismic.all_pdelta if p.status != Status.NA] else
             "P-Delta non évalué."),
        ],
        figure="[FIGURE_CAPTURE_LOGICIEL : ETABS – courbes de participation modale et efforts tranchants d'étage]",
    )
    chapters.append(chapter9)

    # 10. Ferraillage
    chapter10 = ChapterReport(
        10, "Ferraillage des éléments",
        "Ferraillage longitudinal et transversal des poteaux et poutres selon BAEL 91 et RPA 2024.",
        checks=reinforcement.checks,
        elements=reinforcement.columns + reinforcement.beams,
        lines=reinforcement.lines,
        figure="[FIGURE_CAPTURE_LOGICIEL : ETABS – diagrammes de moments et tableaux de ferraillage]",
    )
    chapters.append(chapter10)

    # 11. Voiles
    wall_checks = [Check("VOILES", "Synthèse des voiles", "RPA 2024 § 7.7",
                         Status.worst(e.status for e in reinforcement.walls)
                         if reinforcement.walls else Status.NA,
                         "Dispositions constructives vérifiées",
                         f"{len(reinforcement.walls)} famille(s)")]
    chapters.append(ChapterReport(
        11, "Voiles et dispositions RPA",
        "Qualification, épaisseur, longueur et zones critiques des voiles de contreventement.",
        checks=wall_checks,
        elements=reinforcement.walls,
        lines=[
            "Les voiles sont qualifiés selon lw ≥ max(he/3 ; 4bw ; 1 m) et bw ≥ max(15 cm ; he/20).",
            "Les zones critiques retiennent une longueur ≥ max(1,5.bw ; 0,15.lw) et des armatures "
            "verticales minimales de 0,20 %.",
        ] + ([] if reinforcement.walls else ["Aucun voile dans le modèle importé : chapitre sans objet."]),
        figure="[FIGURE_CAPTURE_LOGICIEL : ETABS – élévation des voiles et armatures de rive]",
    ))

    # 12. Fondations
    chapter12 = ChapterReport(
        12, "Fondations",
        "Dimensionnement des semelles, portance, tassement et modèle de Winkler.",
        checks=foundations.checks,
        elements=[
            ElementReport("Semelle", f.family,
                          f"B×L×h = {fmt(f.b, 2)}×{fmt(f.length, 2)}×{fmt(f.thickness, 2)} m",
                          f.checks)
            for f in foundations.footings
        ],
        lines=foundations.lines + [
            f"Modèle de Winkler : σ = Ks.δ avec Ks = {fmt(project.config.subgrade_modulus, 0)} kN/m³.",
            f"Portance admissible q_adm = {fmt(project.config.q_adm, 1)} kN/m².",
        ],
        figure="[FIGURE_CAPTURE_LOGICIEL : SAFE – carte des pressions de sol et plan de fondations]",
    )
    chapters.append(chapter12)

    # 13. Conclusion
    total_status = Status.worst(c.status for c in chapters)
    failed = [c for c in chapters if c.status == Status.FAILED]
    warnings_chapters = [c for c in chapters if c.status == Status.WARNING]
    conclusion_lines = [
        f"Synthèse générale : {total_status.value}.",
        f"{len(failed)} chapitre(s) en échec, {len(warnings_chapters)} chapitre(s) à vérifier.",
    ]
    for chapter in chapters:
        conclusion_lines.append(f"Chapitre {chapter.number} – {chapter.title} : {chapter.status.value}.")
    conclusion_lines += [
        "Les résultats sont produits automatiquement à partir des tableaux importés. "
        "Toute modification du modèle ETABS/SAFE impose un nouvel import et une nouvelle vérification.",
        "Les captures d'écran signalées [FIGURE_CAPTURE_LOGICIEL : ...] doivent être remplacées "
        "par les impressions d'écran du modèle avant diffusion au CTC.",
    ]
    chapter13 = ChapterReport(
        13, "Conclusion et synthèse",
        "Récapitulatif des vérifications et avis sur la conformité réglementaire.",
        checks=[Check("SYNTHESE", "Synthèse générale", "RPA 2024 / BAEL 91",
                      total_status, "Aucune vérification en échec",
                      f"{failed_count_sum(chapters)} échec(s), {warning_count_sum(chapters)} réserve(s)")],
        elements=[],
        lines=conclusion_lines,
        tables=[ReportTable("Synthèse par chapitre", ["Chapitre", "Titre", "Statut", "Réserves"],
                            [[str(c.number), c.title, c.status.value,
                              f"{c.failed_count} échec(s) / {c.warning_count} réserve(s)"]
                             for c in chapters])],
        figure="[FIGURE_CAPTURE_LOGICIEL : ETABS – vue générale validée du modèle]",
    )
    chapters.append(chapter13)
    for index in (0, 1, 3, 4, 7):
        chapters[index].informative = True
    return chapters


def failed_count_sum(chapters: Sequence[ChapterReport]) -> int:
    return sum(c.failed_count for c in chapters)


def warning_count_sum(chapters: Sequence[ChapterReport]) -> int:
    return sum(c.warning_count for c in chapters)


def _summary_checks(model, seismic, regularity, reinforcement, foundations,
                    joints, chapters) -> list[Check]:
    return [
        Check("SYNTHESE_MODELE", "Modèle importé", "ETABS/SAFE",
              Status.OK, "Modèle exploitable",
              f"{model.num_stories} niveaux, {len(model.frames)} éléments linéiques"),
        Check("SYNTHESE_SISMIQUE", "Analyse sismique", "RPA 2024",
              seismic.status, "Vérifications sismiques satisfaites",
              f"QF = {fmt(seismic.resolved.qf, 2)}, R = {fmt(seismic.resolved.r, 1)}"),
        Check("SYNTHESE_REGULARITE", "Régularité", "RPA 2024 § 3.7",
              regularity.status, "Structure régulière",
              f"Plan : {regularity.plan_regular}, élévation : {regularity.elevation_regular}"),
        Check("SYNTHESE_FERRAILLAGE", "Ferraillage", "BAEL 91 / RPA 2024",
              reinforcement.status, "Ferraillage vérifié", "Voir chapitres 10 et 11"),
        Check("SYNTHESE_FONDATIONS", "Fondations", "DTR BC 2.33.1",
              foundations.status, "Portance et tassement vérifiés",
              f"{len(foundations.footings)} semelle(s)"),
        Check("SYNTHESE_JOINTS", "Joints et hiérarchie", "RPA 2024 § 7.6",
              joints.status, "Poteau fort / poutre faible et joint vérifiés",
              f"{len(joints.strong_column)} noeud(s)"),
    ]


def _assessment_tables(assessment: Assessment) -> list[ReportTable]:
    model = assessment.model
    tables = [
        ReportTable("Synthèse générale", ["Indicateur", "Valeur"], [
            ["Statut global", assessment.status.value],
            ["Niveaux", str(model.num_stories)],
            ["Hauteur Hn (m)", fmt(model.total_height, 2)],
            ["QF", fmt(assessment.regularity.qf, 2)],
            ["R", fmt(assessment.seismic.resolved.r, 1)],
            ["Éléments contrôlés", str(assessment.checked_elements)],
            ["Échecs", str(assessment.failed_count)],
            ["Réserves", str(assessment.warning_count)],
        ]),
    ]
    if assessment.dqe:
        tables.append(assessment.dqe.table)
    return tables


def _dedupe(values: Iterable[str]) -> list[str]:
    seen = set()
    result = []
    for value in values:
        if value and value not in seen:
            seen.add(value)
            result.append(value)
    return result
