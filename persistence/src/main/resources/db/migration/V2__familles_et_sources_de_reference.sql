-- Familles de référence du catalogue de démonstration (ADR 0009 et 0015) et sources connues (ADR 0006).
-- Rôles : identity = entre dans la clé naturelle ; equivalence = entre dans la clé de segment ; descriptive = information.

insert into product_family (code, label, attribute_schema, quarantine_threshold) values
('gpu', 'Carte graphique', '[
  {"code": "chipset",   "label": "Chipset",        "type": "text",   "roles": ["equivalence"]},
  {"code": "vram_gb",   "label": "Mémoire vidéo",  "type": "number", "unit": "Go", "roles": ["equivalence"]},
  {"code": "variant",   "label": "Variante",       "type": "text",   "roles": ["identity"]},
  {"code": "length_mm", "label": "Longueur",       "type": "number", "unit": "mm", "roles": ["descriptive"]},
  {"code": "tdp_w",     "label": "TDP",            "type": "number", "unit": "W",  "roles": ["descriptive"]}
]', 0.500),
('ssd', 'SSD', '[
  {"code": "capacity_gb",  "label": "Capacité",          "type": "number", "unit": "Go",   "roles": ["equivalence"]},
  {"code": "interface",    "label": "Interface",         "type": "enum",   "values": ["SATA", "PCIe 3.0 NVMe", "PCIe 4.0 NVMe", "PCIe 5.0 NVMe"], "roles": ["equivalence"]},
  {"code": "form_factor",  "label": "Format",            "type": "enum",   "values": ["2.5\"", "M.2 2280", "M.2 2230"], "roles": ["equivalence"]},
  {"code": "read_mbps",    "label": "Lecture séquentielle", "type": "number", "unit": "Mo/s", "roles": ["descriptive"]},
  {"code": "heatsink",     "label": "Dissipateur",       "type": "boolean", "roles": ["identity"]}
]', 0.500),
('ram', 'Mémoire vive', '[
  {"code": "capacity_gb",  "label": "Capacité",   "type": "number", "unit": "Go",  "roles": ["equivalence"]},
  {"code": "type",         "label": "Type",       "type": "enum",   "values": ["DDR4", "DDR5"], "roles": ["equivalence"]},
  {"code": "speed_mts",    "label": "Fréquence",  "type": "number", "unit": "MT/s", "roles": ["equivalence"]},
  {"code": "cas_latency",  "label": "Latence CAS","type": "number", "roles": ["equivalence"]},
  {"code": "kit",          "label": "Kit",        "type": "text",   "roles": ["identity"]},
  {"code": "rgb",          "label": "RGB",        "type": "boolean", "roles": ["descriptive"]}
]', 0.500),
('game', 'Jeu vidéo', '[
  {"code": "edition",  "label": "Édition",    "type": "text", "roles": ["identity"]},
  {"code": "genre",    "label": "Genre",      "type": "text", "roles": ["descriptive"]},
  {"code": "platform", "label": "Plateforme", "type": "text", "roles": ["descriptive"]}
]', 0.800);

insert into source (code, label, kind, homepage) values
('ldlc',         'LDLC',         'scraper', 'https://www.ldlc.com'),
('topachat',     'TopAchat',     'scraper', 'https://www.topachat.com'),
('materiel-net', 'Materiel.net', 'scraper', 'https://www.materiel.net'),
('cheapshark',   'CheapShark',   'api',     'https://www.cheapshark.com');
