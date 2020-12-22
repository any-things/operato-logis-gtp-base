select
	:domainId as domain_id,
	COALESCE(job_instance_id, ind_cd) as id,
	:batchId as batch_id,
	:stageCd as stage_cd,
	:jobType as job_type,
	:gwPath as gw_path,
	ind_cd,
	status
from
	work_cells
where
	domain_id = :domainId
	and batch_id = :batchId
	and cell_cd in (
		select
			cell_cd
		from
			cells
		where
			domain_id = :domainId 
			and equip_cd = :equipCd
			and ind_cd in (
				select
					ind_cd
				from
					indicators
				where
					domain_id = :domainId
					and gw_cd = :gwCd
			)
	)
	and status in (:indStatuses)