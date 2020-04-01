select 
	total_cells - running_cells as empty_cells 
from (
	select 
  		(select count(*) from cells where domain_id = :domainId and active_flag = :activeFlag and equip_type = :equipType and equip_cd = :equipCd) as total_cells,
  		(select count(*) from work_cells where domain_id = :domainId and batch_id = :batchId and status not in ('ENDING', 'ENDED')) as running_cells
	from
  		dual
 )