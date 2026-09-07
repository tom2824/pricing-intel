-- Les recommandations calculées avant V5 portaient la clé de profil « default » ; elles ont été calculées avec
-- le profil de référence (index 98 % de la médiane). On les rattache à sa clé pour que la vue de synthèse ne
-- voie qu'un profil de référence par produit. L'historique est conservé, pas supprimé.
update recommendation set profile_key = 'index-98' where profile_key = 'default';
