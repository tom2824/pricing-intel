-- Deux enseignes de plus, issues de groupes qu'on ne suivait pas encore (ADR 0019, complété le 2026-09-09) :
-- 1fodiscount (indépendant, Rouen) et Alternate France (groupe Alternate, Allemagne/Belgique). Six enseignes,
-- quatre groupes : la médiane devient un vrai marché.

insert into source (code, label, kind, homepage) values
('1fodiscount', '1fodiscount', 'scraper', 'https://www.1fodiscount.com'),
('alternate',   'Alternate',   'scraper', 'https://www.alternate.fr');
