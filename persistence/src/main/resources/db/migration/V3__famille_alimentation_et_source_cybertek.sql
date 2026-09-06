-- ADR 0019 : quatrième segment (alimentations ATX) et quatrième source (Cybertek, groupe indépendant de LDLC).

insert into product_family (code, label, attribute_schema, quarantine_threshold) values
('psu', 'Alimentation', '[
  {"code": "wattage_w",   "label": "Puissance",       "type": "number", "unit": "W",  "roles": ["equivalence"]},
  {"code": "atx_version", "label": "Norme ATX",       "type": "enum",   "values": ["ATX 2.4", "ATX 3.0", "ATX 3.1"], "roles": ["descriptive"]},
  {"code": "efficiency",  "label": "Certification",   "type": "enum",   "values": ["80+ Bronze", "80+ Gold", "80+ Platinum", "80+ Titanium"], "roles": ["equivalence"]},
  {"code": "modular",     "label": "Modularité",      "type": "enum",   "values": ["non modulaire", "semi-modulaire", "modulaire"], "roles": ["equivalence"]},
  {"code": "color",       "label": "Couleur",         "type": "text",   "roles": ["identity"]},
  {"code": "fan_mm",      "label": "Ventilateur",     "type": "number", "unit": "mm", "roles": ["descriptive"]},
  {"code": "pcie_5_12v",  "label": "Connecteur 12V-2x6", "type": "number", "roles": ["descriptive"]}
]', 0.500);

insert into source (code, label, kind, homepage) values
('cybertek', 'Cybertek', 'scraper', 'https://www.cybertek.fr');
