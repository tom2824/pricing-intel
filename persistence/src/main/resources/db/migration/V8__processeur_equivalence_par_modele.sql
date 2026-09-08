-- Un processeur n'a pas d'équivalent aux yeux de l'acheteur : un 9800X3D n'est pas « une RTX 5070 » dont la marque
-- importe peu. L'équivalence de la famille cpu devient le modèle lui-même (segment = produit), socket et cœurs
-- restent descriptifs. Les clés d'équivalence sont recalculées par l'import du catalogue à la collecte suivante.
update product_family
set attribute_schema = '[
  {"code": "model",     "label": "Modèle",           "type": "text",   "roles": ["equivalence"]},
  {"code": "socket",    "label": "Socket",           "type": "enum",   "values": ["AM4", "AM5", "LGA1700", "LGA1851"], "roles": ["descriptive"]},
  {"code": "cores",     "label": "Cœurs",            "type": "number", "roles": ["descriptive"]},
  {"code": "threads",   "label": "Threads",          "type": "number", "roles": ["descriptive"]},
  {"code": "boost_ghz", "label": "Fréquence boost",  "type": "number", "unit": "GHz", "roles": ["descriptive"]},
  {"code": "l3_mb",     "label": "Cache L3",         "type": "number", "unit": "Mo",  "roles": ["descriptive"]},
  {"code": "tdp_w",     "label": "TDP",              "type": "number", "unit": "W",   "roles": ["descriptive"]},
  {"code": "packaging", "label": "Conditionnement",  "type": "enum",   "values": ["box", "tray"], "roles": ["identity"]}
]'
where code = 'cpu';
