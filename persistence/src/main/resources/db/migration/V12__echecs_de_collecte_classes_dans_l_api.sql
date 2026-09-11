-- L'API publique exposait les motifs d'échec bruts : messages d'exception, URL complètes, noms de classes Java.
-- Un message d'exception n'est pas un contrat. La vue classe désormais chaque échec dans un code court et stable,
-- que le portfolio traduit ; le message brut reste en table pour le diagnostic (diagnose-db.yml, logs du batch).
-- Codes : http-<statut>, timeout, unreachable, no-price, invalid-offer, no-source, storage, internal, other.
create or replace view api.collection_failures as
select f.listing_code, l.source_code, lcp.product_id, f.occurred_at,
       case
           when f.reason ~ '^HTTP [0-9]{3} '                       then 'http-' || substring(f.reason from '^HTTP ([0-9]{3}) ')
           when f.reason ~* '^Fetch failed' and f.reason ~* 'time' then 'timeout'
           when f.reason ~* '^Fetch failed'                        then 'unreachable'
           when f.reason ~* '^No extractor'                        then 'no-price'
           when f.reason ~* '^Extracted offer is invalid'          then 'invalid-offer'
           when f.reason ~* '^No site definition|^No source supports' then 'no-source'
           when f.source_id = 'sink'                               then 'storage'
           when f.reason ~* '^Unexpected error'                    then 'internal'
           else 'other'
       end as reason,
       f.retryable
from collection_failure f
left join listing l on l.id = f.listing_id
left join listing_current_product lcp on lcp.listing_id = l.id;
