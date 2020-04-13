select
	'Rack' as equip_type,
    rack_cd as equip_cd, 
    rack_nm as equip_nm, 
    sku_cd
    #if($qtyFilter)
    , sum(pick_qty - nvl(picked_qty, 0)) pcs_qty
    #else
    , sum(pick_qty) pcs_qty
    #end
from
    job_instances
where
    domain_id = :domainId
    and batch_id in (select id from job_batches where domain_id = :domainId and job_type = :jobType #if($batchGroupId) and batch_group_id = :batchGroupId #end #if($batchStatus) and status = :batchStatus #end)
    #if($comCd)
    and com_cd = :comCd
    #end
    and sku_cd = :skuCd
group by
    rack_cd, rack_nm, sku_cd
order by
	rack_cd
 	#if($rackAsc) 
    asc
 	#else 
    desc
 	#end