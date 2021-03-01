select
	:domainId as domain_id,
	max(id),
	batch_id,
	ind_cd,
	'END' as status,
	:stageCd as stage_cd,
	:jobType as job_type,
	:gwPath as gw_path
from
	job_instances
where
	domain_id = :domainId
	and batch_id = :batchId
	and status = 'B'
	and sub_equip_cd in (
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
group by
	batch_id, ind_cd