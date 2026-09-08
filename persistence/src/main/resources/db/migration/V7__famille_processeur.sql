-- Cinquième segment : processeurs de bureau (ADR 0019, complété le 2026-09-08). Les produits qu'aucune enseigne
-- ne vend plus sont retirés du suivi par le catalogue (status = retired), leur historique reste en base.

insert into product_family (code, label, attribute_schema, quarantine_threshold) values
('cpu', 'Processeur', '[
  {"code": "socket",    "label": "Socket",           "type": "enum",   "values": ["AM4", "AM5", "LGA1700", "LGA1851"], "roles": ["equivalence"]},
  {"code": "cores",     "label": "Cœurs",            "type": "number", "roles": ["equivalence"]},
  {"code": "threads",   "label": "Threads",          "type": "number", "roles": ["descriptive"]},
  {"code": "boost_ghz", "label": "Fréquence boost",  "type": "number", "unit": "GHz", "roles": ["descriptive"]},
  {"code": "l3_mb",     "label": "Cache L3",         "type": "number", "unit": "Mo",  "roles": ["descriptive"]},
  {"code": "tdp_w",     "label": "TDP",              "type": "number", "unit": "W",   "roles": ["descriptive"]},
  {"code": "packaging", "label": "Conditionnement",  "type": "enum",   "values": ["box", "tray"], "roles": ["identity"]}
]', 0.500);
