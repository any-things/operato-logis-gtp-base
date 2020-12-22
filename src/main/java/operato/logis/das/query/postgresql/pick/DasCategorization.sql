select
	equip_type,
	equip_cd,
	equip_nm,
	sku_cd,
	sum(order_qty) as order_qty,
	sum(picked_qty) as picked_qty
from
	orders
where
	domain_id = :domainId
	and com_cd = :comCd
	and sku_cd = :skuCd
	and batch_id in (:batchIdList)
group by
	equip_type, equip_cd, equip_nm, sku_cd
order by
	equip_cd
	#if($rackAsc)
	asc
	#else
	desc
	#end