| query | scale | mode | execution_time_ms | planning_time_ms | total_cost | plan_rows | actual_rows | top_node | indexes_used |
|---|---|---|---|---|---|---|---|---|---|
| candidate_search | 1000 | indexed | 52.187 | 35.737 | 3903.18 | 1 | 81 | Nested Loop | excl_worker_overlap,idx_worker_availability_worker_id,locations_pkey |
| candidate_search | 1000 | noindex | 52.05 | 31.772 | 3903.19 | 1 | 81 | Nested Loop | excl_worker_overlap,locations_pkey,uq_worker_availability_slot |
| candidate_search | 5000 | indexed | 68.052 | 40.225 | 19428.5 | 1 | 399 | Nested Loop | excl_worker_overlap,idx_worker_availability_worker_id,locations_pkey |
| candidate_search | 5000 | noindex | 62.821 | 26.854 | 19428.5 | 1 | 399 | Nested Loop | excl_worker_overlap,locations_pkey,uq_worker_availability_slot |
| candidate_search | 10000 | indexed | 85.669 | 36.467 | 38841.57 | 1 | 838 | Nested Loop | excl_worker_overlap,idx_worker_availability_worker_id,locations_pkey |
| candidate_search | 10000 | noindex | 83.424 | 26.06 | 38841.7 | 1 | 838 | Nested Loop | excl_worker_overlap,locations_pkey,uq_worker_availability_slot |
| candidate_search | 50000 | indexed | 227.651 | 43.992 | 86604.37 | 2 | 4252 | Gather | excl_worker_overlap,idx_worker_availability_worker_id,locations_pkey,worker_services_pkey,workers_pkey |
| candidate_search | 50000 | noindex | 228.445 | 28.821 | 86604.38 | 2 | 4252 | Gather | excl_worker_overlap,locations_pkey,uq_worker_availability_slot,worker_services_pkey,workers_pkey |
| candidate_search | 100000 | indexed | 613.613 | 48.38 | 170457.35 | 3 | 8495 | Gather | excl_worker_overlap,idx_worker_availability_worker_id,locations_pkey |
| candidate_search | 100000 | noindex | 610.656 | 27.513 | 170457.36 | 3 | 8495 | Gather | excl_worker_overlap,locations_pkey,uq_worker_availability_slot |
| nearby_workers | 1000 | indexed | 45.477 | 19.79 | 2346.28 | 1 | 57 | Sort | idx_locations_geom,idx_workers_location_id |
| nearby_workers | 1000 | noindex | 49.706 | 14.559 | 12394.98 | 1 | 57 | Sort | locations_pkey |
| nearby_workers | 5000 | indexed | 58.239 | 22.262 | 12942.85 | 1 | 318 | Sort | idx_locations_geom,idx_workers_location_id |
| nearby_workers | 5000 | noindex | 73.668 | 14.482 | 61858.6 | 1 | 318 | Sort | locations_pkey |
| nearby_workers | 10000 | indexed | 58.346 | 19.624 | 25755.38 | 1 | 646 | Sort | idx_locations_geom,idx_workers_location_id |
| nearby_workers | 10000 | noindex | 108.441 | 17.093 | 123695.49 | 1 | 646 | Sort | locations_pkey |
| nearby_workers | 50000 | indexed | 218.429 | 31.441 | 78182.06 | 3 | 3291 | Gather Merge | idx_locations_geom,idx_workers_location_id |
| nearby_workers | 50000 | noindex | 373.334 | 14.295 | 619272.39 | 5 | 3291 | Sort | locations_pkey |
| nearby_workers | 100000 | indexed | 193.624 | 19.664 | 112684.42 | 8 | 6720 | Gather Merge | idx_locations_geom,idx_workers_location_id |
| nearby_workers | 100000 | noindex | 522.853 | 13.633 | 737541.77 | 6 | 6720 | Gather Merge | locations_pkey |
